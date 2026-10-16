package io.github.urbanog.vrp.api;

import io.github.urbanog.vrp.api.ValidationException.FieldError;
import io.github.urbanog.vrp.io.InstanceCatalog;
import io.github.urbanog.vrp.io.TsplibReader;
import io.github.urbanog.vrp.io.VrpFormatException;
import io.github.urbanog.vrp.jobs.Job;
import io.github.urbanog.vrp.json.JsonObjectBuilder;
import io.github.urbanog.vrp.json.JsonValue;
import io.github.urbanog.vrp.json.JsonValue.JsonArray;
import io.github.urbanog.vrp.json.JsonValue.JsonNumber;
import io.github.urbanog.vrp.json.JsonValue.JsonObject;
import io.github.urbanog.vrp.json.JsonValue.JsonString;
import io.github.urbanog.vrp.model.Customer;
import io.github.urbanog.vrp.model.DistanceType;
import io.github.urbanog.vrp.model.Instance;
import io.github.urbanog.vrp.model.Point;
import io.github.urbanog.vrp.model.Solution;
import io.github.urbanog.vrp.solver.Algorithm;
import io.github.urbanog.vrp.solver.SolveOptions;
import io.github.urbanog.vrp.solver.SolveResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Translation between JSON documents and domain objects, with field-level validation.
 *
 * <p>All problems found in a request are collected and reported together (HTTP 422), rather than
 * failing on the first one, so a client can fix a form in one round trip. Unknown fields are
 * rejected, which catches typos such as {@code "capactiy"}.
 */
public final class ApiCodec {
    private final InstanceCatalog catalog;

    public ApiCodec(InstanceCatalog catalog) {
        this.catalog = catalog;
    }

    /** A parsed solve request. */
    public record SolveRequest(Instance instance, SolveOptions options) {}

    // ================================================================== requests

    public SolveRequest readSolveRequest(JsonValue body) {
        List<FieldError> errs = new ArrayList<>();
        if (!(body instanceof JsonObject root)) {
            throw new ValidationException("$", "request body must be a JSON object");
        }
        checkKnown(root, "", Set.of("instance", "instanceName", "tsplib", "options"), errs);
        int sources = (root.has("instance") ? 1 : 0) + (root.has("instanceName") ? 1 : 0) + (root.has("tsplib") ? 1 : 0);
        Instance instance = null;
        if (sources != 1) {
            errs.add(new FieldError("$", "exactly one of 'instance', 'instanceName' or 'tsplib' is required"));
        } else if (root.has("instance")) {
            instance = readInstance(root.members().get("instance"), "instance", errs);
        } else if (root.has("instanceName")) {
            String name = string(root, "instanceName", "", errs);
            if (name != null) {
                instance = catalog.get(name).orElse(null);
                if (instance == null) errs.add(new FieldError("instanceName", "unknown instance '" + name + "'"));
            }
        } else {
            String text = string(root, "tsplib", "", errs);
            if (text != null) {
                try {
                    instance = TsplibReader.parse(text);
                } catch (VrpFormatException | IllegalArgumentException e) {
                    errs.add(new FieldError("tsplib", e.getMessage()));
                }
            }
        }
        if (instance != null) {
            for (Instance.Violation v : instance.validate()) {
                String prefix = root.has("instance") ? "instance." : (root.has("tsplib") ? "tsplib:" : "instanceName:");
                errs.add(new FieldError(prefix + v.field(), v.message()));
            }
        }
        SolveOptions options = readOptions(root.members().get("options"), errs);
        if (options != null && instance != null && options.algorithm() == Algorithm.EXACT
                && instance.size() > io.github.urbanog.vrp.solver.ExactSolver.MAX_CUSTOMERS) {
            errs.add(new FieldError("options.algorithm", "'exact' supports at most "
                    + io.github.urbanog.vrp.solver.ExactSolver.MAX_CUSTOMERS + " customers, instance has " + instance.size()));
        }
        if (!errs.isEmpty()) throw new ValidationException(errs);
        return new SolveRequest(instance, options);
    }

    Instance readInstance(JsonValue v, String path, List<FieldError> errs) {
        if (!(v instanceof JsonObject o)) {
            errs.add(new FieldError(path, "must be an object"));
            return null;
        }
        checkKnown(o, path + ".", Set.of("name", "capacity", "distance", "depot", "customers"), errs);
        String name = o.has("name") ? string(o, "name", path + ".", errs) : "custom";
        Integer capacity = integer(o, "capacity", path + ".", true, errs);
        DistanceType dt = DistanceType.EUCLIDEAN;
        if (o.has("distance")) {
            String d = string(o, "distance", path + ".", errs);
            if ("euclidean".equals(d)) {
                dt = DistanceType.EUCLIDEAN;
            } else if ("euclidean-rounded".equals(d)) {
                dt = DistanceType.EUCLIDEAN_ROUNDED;
            } else if (d != null) {
                errs.add(new FieldError(path + ".distance", "must be 'euclidean' or 'euclidean-rounded'"));
            }
        }
        Point depot = point(o.members().get("depot"), path + ".depot", errs);
        List<Customer> customers = new ArrayList<>();
        JsonValue cv = o.members().get("customers");
        if (!(cv instanceof JsonArray arr)) {
            errs.add(new FieldError(path + ".customers", cv == null ? "is required" : "must be an array"));
        } else if (arr.size() > Instance.MAX_CUSTOMERS) {
            errs.add(new FieldError(path + ".customers", "at most " + Instance.MAX_CUSTOMERS + " customers are supported"));
        } else {
            for (int i = 0; i < arr.size(); i++) {
                String cp = path + ".customers[" + i + "]";
                if (!(arr.get(i) instanceof JsonObject c)) {
                    errs.add(new FieldError(cp, "must be an object"));
                    continue;
                }
                checkKnown(c, cp + ".", Set.of("id", "x", "y", "demand"), errs);
                Integer id = c.has("id") ? integer(c, "id", cp + ".", true, errs) : Integer.valueOf(i + 1);
                Double x = number(c, "x", cp + ".", errs);
                Double y = number(c, "y", cp + ".", errs);
                Integer q = integer(c, "demand", cp + ".", true, errs);
                if (id != null && x != null && y != null && q != null) {
                    customers.add(new Customer(id, new Point(x, y), q));
                }
            }
        }
        if (name == null || capacity == null || depot == null || !errs.isEmpty()) return null;
        return new Instance(name, capacity, dt, depot, customers);
    }

    SolveOptions readOptions(JsonValue v, List<FieldError> errs) {
        SolveOptions opt = SolveOptions.defaults();
        if (v == null) return opt;
        if (!(v instanceof JsonObject o)) {
            errs.add(new FieldError("options", "must be an object"));
            return null;
        }
        checkKnown(o, "options.", Set.of("algorithm", "timeLimitMs", "maxIterations", "seed"), errs);
        if (o.has("algorithm")) {
            String a = string(o, "algorithm", "options.", errs);
            if (a != null) {
                var alg = Algorithm.fromWireName(a);
                if (alg.isEmpty()) {
                    errs.add(new FieldError("options.algorithm", "must be one of "
                            + Arrays.stream(Algorithm.values()).map(Algorithm::wireName).toList()));
                } else {
                    opt = opt.withAlgorithm(alg.get());
                }
            }
        }
        if (o.has("timeLimitMs")) {
            Integer t = integer(o, "timeLimitMs", "options.", true, errs);
            if (t != null) {
                if (t < 1 || t > SolveOptions.MAX_TIME_LIMIT_MILLIS) {
                    errs.add(new FieldError("options.timeLimitMs", "must be between 1 and " + SolveOptions.MAX_TIME_LIMIT_MILLIS));
                } else {
                    opt = opt.withTimeLimit(t);
                }
            }
        }
        if (o.has("maxIterations")) {
            Integer it = integer(o, "maxIterations", "options.", true, errs);
            if (it != null) opt = opt.withMaxIterations(it);
        }
        if (o.has("seed")) {
            Integer s = integer(o, "seed", "options.", false, errs);
            if (s != null) opt = opt.withSeed(s);
        }
        return opt;
    }

    // ================================================================== responses

    public static JsonObject writeInstance(Instance inst) {
        return JsonObjectBuilder.object()
                .put("name", inst.name())
                .put("capacity", inst.capacity())
                .put("distance", inst.distanceType() == DistanceType.EUCLIDEAN ? "euclidean" : "euclidean-rounded")
                .put("depot", JsonObjectBuilder.object().put("x", inst.depot().x()).put("y", inst.depot().y()))
                .putArray("customers", inst.customers(), c -> JsonObjectBuilder.object()
                        .put("id", c.id())
                        .put("x", c.location().x())
                        .put("y", c.location().y())
                        .put("demand", c.demand())
                        .build())
                .build();
    }

    public static JsonObject writeInstanceSummary(Instance inst) {
        return JsonObjectBuilder.object()
                .put("name", inst.name())
                .put("customers", inst.size())
                .put("capacity", inst.capacity())
                .put("totalDemand", inst.totalDemand())
                .put("minVehicles", inst.minVehicles())
                .build();
    }

    public static JsonObject writeResult(Instance inst, SolveResult r) {
        Solution s = r.solution();
        return JsonObjectBuilder.object()
                .put("instance", inst.name())
                .put("algorithm", r.algorithm().wireName())
                .put("cost", round(s.cost()))
                .put("vehicles", s.routes().size())
                .put("lowerBound", round(r.lowerBound()))
                .put("gapToLowerBound", round(r.gapToLowerBound()))
                .put("initialCost", round(r.initialCost()))
                .put("optimal", r.optimal())
                .put("runtimeMs", r.runtimeMillis())
                .put("iterations", r.iterations())
                .putArray("routes", s.routes(), route -> JsonObjectBuilder.object()
                        .put("customers", JsonObjectBuilder.intArray(route.customerIds()))
                        .put("load", route.load())
                        .put("cost", round(route.cost()))
                        .build())
                .build();
    }

    public static JsonObject writeJob(Job job, boolean includeResult) {
        var b = JsonObjectBuilder.object()
                .put("id", job.id().toString())
                .put("status", job.status().name())
                .put("instance", job.instanceName())
                .put("algorithm", job.algorithm().wireName())
                .put("createdAt", job.createdAt().toString())
                .put("startedAt", job.startedAt() == null ? null : job.startedAt().toString())
                .put("finishedAt", job.finishedAt() == null ? null : job.finishedAt().toString());
        if (job.cost() != null) b.put("cost", round(job.cost()));
        if (job.error() != null) b.put("error", job.error());
        if (includeResult && job.resultJson() != null) b.put("result", job.resultJson());
        return b.build();
    }

    /** Six significant decimals are plenty for display and keep payloads readable. */
    static double round(double d) {
        return Math.round(d * 1e6) / 1e6;
    }

    // ================================================================== field helpers

    private static void checkKnown(JsonObject o, String prefix, Set<String> allowed, List<FieldError> errs) {
        for (String k : o.members().keySet()) {
            if (!allowed.contains(k)) errs.add(new FieldError(prefix + k, "unknown field"));
        }
    }

    private static String string(JsonObject o, String key, String prefix, List<FieldError> errs) {
        JsonValue v = o.members().get(key);
        if (v instanceof JsonString s) return s.value();
        errs.add(new FieldError(prefix + key, v == null ? "is required" : "must be a string, got " + v.typeName()));
        return null;
    }

    private static Double number(JsonObject o, String key, String prefix, List<FieldError> errs) {
        JsonValue v = o.members().get(key);
        if (v instanceof JsonNumber n) return n.value();
        errs.add(new FieldError(prefix + key, v == null ? "is required" : "must be a number, got " + v.typeName()));
        return null;
    }

    private static Integer integer(JsonObject o, String key, String prefix, boolean nonNegative, List<FieldError> errs) {
        JsonValue v = o.members().get(key);
        if (!(v instanceof JsonNumber n)) {
            errs.add(new FieldError(prefix + key, v == null ? "is required" : "must be an integer, got " + v.typeName()));
            return null;
        }
        if (!n.isIntegral() || n.value() > Integer.MAX_VALUE || n.value() < Integer.MIN_VALUE) {
            errs.add(new FieldError(prefix + key, "must be a 32-bit integer"));
            return null;
        }
        if (nonNegative && n.value() < 0) {
            errs.add(new FieldError(prefix + key, "must be non-negative"));
            return null;
        }
        return (int) n.value();
    }

    private static Point point(JsonValue v, String path, List<FieldError> errs) {
        if (!(v instanceof JsonObject o)) {
            errs.add(new FieldError(path, v == null ? "is required" : "must be an object {x, y}"));
            return null;
        }
        checkKnown(o, path + ".", Set.of("x", "y"), errs);
        Double x = number(o, "x", path + ".", errs);
        Double y = number(o, "y", path + ".", errs);
        return x == null || y == null ? null : new Point(x, y);
    }
}
