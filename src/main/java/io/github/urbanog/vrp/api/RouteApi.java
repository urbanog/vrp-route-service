package io.github.urbanog.vrp.api;

import io.github.urbanog.vrp.api.Router.Request;
import io.github.urbanog.vrp.api.Router.Response;
import io.github.urbanog.vrp.io.InstanceCatalog;
import io.github.urbanog.vrp.io.InstanceGenerator;
import io.github.urbanog.vrp.jobs.Job;
import io.github.urbanog.vrp.jobs.JobService;
import io.github.urbanog.vrp.json.Json;
import io.github.urbanog.vrp.json.JsonObjectBuilder;
import io.github.urbanog.vrp.json.JsonParseException;
import io.github.urbanog.vrp.json.JsonValue;
import io.github.urbanog.vrp.solver.CvrpSolver;
import io.github.urbanog.vrp.solver.SolutionChecker;
import java.util.Locale;
import java.util.UUID;

/**
 * The REST resources. Handlers are plain functions from {@link Request} to {@link Response}, so
 * they are unit-tested without opening a socket; {@link HttpServerAdapter} only does transport.
 *
 * <pre>
 * GET  /api/health                     liveness + job counters
 * GET  /api/instances                  bundled benchmark instances (summaries)
 * GET  /api/instances/{name}           one bundled instance in full
 * POST /api/instances/generate         seeded random instance
 * POST /api/solve                      synchronous solve
 * POST /api/jobs                       asynchronous solve, 202 + Location
 * GET  /api/jobs                       recent jobs
 * GET  /api/jobs/{id}                  job status and, when done, the result
 * </pre>
 */
public final class RouteApi {
    private final InstanceCatalog catalog;
    private final ApiCodec codec;
    private final CvrpSolver solver;
    private final JobService jobs;
    private final Router router = new Router();

    public RouteApi(InstanceCatalog catalog, CvrpSolver solver, JobService jobs) {
        this.catalog = catalog;
        this.codec = new ApiCodec(catalog);
        this.solver = solver;
        this.jobs = jobs;
        router.add("GET", "/api/health", this::health)
                .add("GET", "/api/instances", this::listInstances)
                .add("GET", "/api/instances/{name}", this::getInstance)
                .add("POST", "/api/instances/generate", this::generate)
                .add("POST", "/api/solve", this::solve)
                .add("POST", "/api/jobs", this::submitJob)
                .add("GET", "/api/jobs", this::listJobs)
                .add("GET", "/api/jobs/{id}", this::getJob);
    }

    public Router router() {
        return router;
    }

    /** Dispatches a request and maps every failure to a problem document; never throws. */
    public Response dispatch(String method, String path, java.util.Map<String, String> query, String body) {
        try {
            return switch (router.match(method, path)) {
                case Router.Found f -> f.handler().handle(new Request(method, path, f.params(), query, body));
                case Router.MethodNotAllowed m -> problem(405, "Method Not Allowed",
                        "allowed: " + String.join(", ", m.allowed()), null)
                        .withHeader("Allow", String.join(", ", m.allowed()));
                case Router.NotFound n -> problem(404, "Not Found", "no route for " + path, null);
            };
        } catch (JsonParseException e) {
            return problem(400, "Malformed JSON", e.getMessage(), null);
        } catch (ValidationException e) {
            return problem(422, "Validation Failed", e.getMessage(), e);
        } catch (HttpError e) {
            return problem(e.status(), e.title(), e.getMessage(), null);
        } catch (Exception e) {
            return problem(500, "Internal Server Error", e.getClass().getSimpleName(), null);
        }
    }

    // ------------------------------------------------------------------ handlers

    private Response health(Request r) {
        var repo = jobs.repository();
        return ok(JsonObjectBuilder.object()
                .put("status", "UP")
                .put("instances", catalog.all().size())
                .put("jobs", JsonObjectBuilder.object()
                        .put("queued", repo.countByStatus(Job.Status.QUEUED))
                        .put("running", repo.countByStatus(Job.Status.RUNNING))
                        .put("succeeded", repo.countByStatus(Job.Status.SUCCEEDED))
                        .put("failed", repo.countByStatus(Job.Status.FAILED)))
                .build());
    }

    private Response listInstances(Request r) {
        return ok(JsonObjectBuilder.object()
                .putArray("instances", catalog.all(), ApiCodec::writeInstanceSummary)
                .build());
    }

    private Response getInstance(Request r) {
        String name = r.pathParams().get("name");
        var inst = catalog.get(name).orElseThrow(() -> HttpError.notFound("unknown instance '" + name + "'"));
        return ok(ApiCodec.writeInstance(inst));
    }

    private Response generate(Request r) {
        JsonValue body = r.body() == null || r.body().isBlank() ? new JsonValue.JsonObject(java.util.Map.of()) : Json.parse(r.body());
        if (!(body instanceof JsonValue.JsonObject o)) throw new ValidationException("$", "must be a JSON object");
        int n = intField(o, "customers", 30, 1, 500);
        int routeSize = intField(o, "routeSize", 8, 1, 500);
        int seed = intField(o, "seed", 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        String layoutStr = o.get("layout").map(v -> v instanceof JsonValue.JsonString s ? s.value() : "?").orElse("uniform");
        InstanceGenerator.Layout layout;
        try {
            layout = InstanceGenerator.Layout.valueOf(layoutStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("layout", "must be 'uniform' or 'clustered'");
        }
        String name = "gen-" + layoutStr + "-n" + n + "-s" + seed;
        return ok(ApiCodec.writeInstance(InstanceGenerator.generate(name, n, routeSize, layout, seed)));
    }

    private Response solve(Request r) {
        var req = codec.readSolveRequest(Json.parse(requireBody(r)));
        var result = solver.solve(req.instance(), req.options());
        var problems = SolutionChecker.check(req.instance(), result.solution());
        if (!problems.isEmpty()) throw new IllegalStateException("infeasible solution: " + problems);
        return ok(ApiCodec.writeResult(req.instance(), result));
    }

    private Response submitJob(Request r) {
        var req = codec.readSolveRequest(Json.parse(requireBody(r)));
        Job job = jobs.submit(req.instance(), req.options());
        return Response.json(202, Json.write(ApiCodec.writeJob(job, false)))
                .withHeader("Location", "/api/jobs/" + job.id());
    }

    private Response listJobs(Request r) {
        int limit = 20;
        String l = r.query().get("limit");
        if (l != null) {
            try {
                limit = Integer.parseInt(l);
            } catch (NumberFormatException e) {
                throw HttpError.badRequest("limit must be an integer");
            }
            if (limit < 1 || limit > 100) throw HttpError.badRequest("limit must be between 1 and 100");
        }
        return ok(JsonObjectBuilder.object()
                .putArray("jobs", jobs.repository().findRecent(limit), j -> ApiCodec.writeJob(j, false))
                .build());
    }

    private Response getJob(Request r) {
        UUID id;
        try {
            id = UUID.fromString(r.pathParams().get("id"));
        } catch (IllegalArgumentException e) {
            throw HttpError.badRequest("job id must be a UUID");
        }
        Job job = jobs.repository().findById(id).orElseThrow(() -> HttpError.notFound("no job " + id));
        return ok(ApiCodec.writeJob(job, true));
    }

    // ------------------------------------------------------------------ helpers

    private static String requireBody(Request r) {
        if (r.body() == null || r.body().isBlank()) throw HttpError.badRequest("request body is required");
        return r.body();
    }

    private static int intField(JsonValue.JsonObject o, String key, int dflt, int min, int max) {
        var v = o.get(key);
        if (v.isEmpty()) return dflt;
        if (!(v.get() instanceof JsonValue.JsonNumber n) || !n.isIntegral() || n.value() < min || n.value() > max) {
            throw new ValidationException(key, "must be an integer in [" + min + ", " + max + "]");
        }
        return (int) n.value();
    }

    private static Response ok(JsonValue v) {
        return Response.json(200, Json.write(v));
    }

    static Response problem(int status, String title, String detail, ValidationException ve) {
        var b = JsonObjectBuilder.object()
                .put("type", "about:blank")
                .put("title", title)
                .put("status", status)
                .put("detail", detail);
        if (ve != null) {
            b.putArray("errors", ve.errors(), e -> JsonObjectBuilder.object()
                    .put("field", e.field())
                    .put("message", e.message())
                    .build());
        }
        return new Response(status, "application/problem+json", Json.write(b.build()));
    }
}
