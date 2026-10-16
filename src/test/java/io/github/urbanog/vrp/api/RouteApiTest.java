package io.github.urbanog.vrp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.urbanog.vrp.api.Router.Response;
import io.github.urbanog.vrp.io.InstanceCatalog;
import io.github.urbanog.vrp.jobs.InMemoryJobRepository;
import io.github.urbanog.vrp.jobs.JobService;
import io.github.urbanog.vrp.json.Json;
import io.github.urbanog.vrp.json.JsonValue;
import io.github.urbanog.vrp.json.JsonValue.JsonArray;
import io.github.urbanog.vrp.json.JsonValue.JsonNumber;
import io.github.urbanog.vrp.json.JsonValue.JsonObject;
import io.github.urbanog.vrp.json.JsonValue.JsonString;
import io.github.urbanog.vrp.solver.CvrpSolver;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Handler-level tests: no sockets, just request in, response out. */
class RouteApiTest {
    private JobService jobs;
    private RouteApi api;

    static final String TINY = """
            {"instance": {"name": "tiny", "capacity": 10,
              "depot": {"x": 0, "y": 0},
              "customers": [{"id": 1, "x": 3, "y": 4, "demand": 4},
                            {"id": 2, "x": -3, "y": 4, "demand": 5},
                            {"id": 3, "x": 0, "y": -5, "demand": 6}]},
             "options": {"algorithm": "exact"}}
            """;

    @BeforeEach
    void setUp() {
        jobs = new JobService(new InMemoryJobRepository(), new CvrpSolver(), Clock.systemUTC(), 2);
        api = new RouteApi(InstanceCatalog.loadBundled(), new CvrpSolver(), jobs);
    }

    @AfterEach
    void tearDown() {
        jobs.close();
    }

    private Response call(String method, String path, String body) {
        return api.dispatch(method, path, Map.of(), body);
    }

    private static JsonObject obj(Response r) {
        return assertInstanceOf(JsonObject.class, Json.parse(r.body()));
    }

    private static double num(JsonObject o, String key) {
        return ((JsonNumber) o.members().get(key)).value();
    }

    @Test
    void healthIsUp() {
        Response r = call("GET", "/api/health", "");
        assertEquals(200, r.status());
        assertEquals(new JsonString("UP"), obj(r).members().get("status"));
    }

    @Test
    void listsBundledInstances() {
        Response r = call("GET", "/api/instances", "");
        assertEquals(200, r.status());
        JsonArray arr = (JsonArray) obj(r).members().get("instances");
        assertEquals(InstanceCatalog.BUNDLED.size(), arr.size());
        assertEquals(200, call("GET", "/api/instances/U-n10-k3", "").status());
        assertEquals(404, call("GET", "/api/instances/nope", "").status());
    }

    @Test
    void solvesInlineInstanceExactly() {
        Response r = call("POST", "/api/solve", TINY);
        assertEquals(200, r.status(), r.body());
        JsonObject o = obj(r);
        // Optimal: {1,2} share a route (5 + 6 + 5 = 16), {3} alone (10), total 26.
        assertEquals(26.0, num(o, "cost"), 1e-9);
        assertEquals(new JsonValue.JsonBool(true), o.members().get("optimal"));
        assertEquals(2.0, num(o, "vehicles"), 0.0);
    }

    @Test
    void solvesBundledInstanceByNameAndTsplibText() {
        Response byName = call("POST", "/api/solve", "{\"instanceName\": \"U-n30-k4\", \"options\": {\"algorithm\": \"local-search\"}}");
        assertEquals(200, byName.status(), byName.body());
        String text = "NAME : t\nTYPE : CVRP\nDIMENSION : 3\nEDGE_WEIGHT_TYPE : EUC_2D\nCAPACITY : 5\n"
                + "NODE_COORD_SECTION\n1 0 0\n2 3 4\n3 6 8\nDEMAND_SECTION\n1 0\n2 2\n3 3\nDEPOT_SECTION\n1\n-1\nEOF\n";
        String tsplib = Json.write(JsonValue.of(text));
        Response byText = call("POST", "/api/solve", "{\"tsplib\": " + tsplib + ", \"options\": {\"algorithm\": \"savings\"}}");
        assertEquals(200, byText.status(), byText.body());
    }

    @Test
    void malformedJsonIs400() {
        Response r = call("POST", "/api/solve", "{\"instance\": ");
        assertEquals(400, r.status());
        assertEquals("application/problem+json", r.contentType());
    }

    @Test
    void validationErrorsAre422AndListEveryField() {
        String body = """
                {"instance": {"capacity": 5, "depot": {"x": 0},
                  "customers": [{"id": 1, "x": 1, "y": 1, "demand": 9},
                                {"id": 1, "x": "a", "y": 1, "demand": 1}],
                  "capactiy": 3},
                 "options": {"algorithm": "magic", "timeLimitMs": 0}}
                """;
        Response r = call("POST", "/api/solve", body);
        assertEquals(422, r.status());
        String text = r.body();
        for (String field : new String[] {"instance.depot.y", "instance.customers[1].x", "instance.capactiy",
                "options.algorithm", "options.timeLimitMs"}) {
            assertTrue(text.contains("\"" + field + "\""), "missing error for " + field + " in " + text);
        }
    }

    @Test
    void semanticViolationsAre422() {
        String body = TINY.replace("\"demand\": 6", "\"demand\": 60");
        Response r = call("POST", "/api/solve", body);
        assertEquals(422, r.status());
        assertTrue(r.body().contains("instance.customers[2].demand"), r.body());
        Response big = call("POST", "/api/solve", "{\"instanceName\": \"U-n30-k4\", \"options\": {\"algorithm\": \"exact\"}}");
        assertEquals(422, big.status());
    }

    @Test
    void routingErrors() {
        assertEquals(404, call("GET", "/api/nothing", "").status());
        Response r = call("DELETE", "/api/solve", "");
        assertEquals(405, r.status());
        assertEquals("POST", r.headers().get("Allow"));
        assertEquals(400, call("GET", "/api/jobs/not-a-uuid", "").status());
        assertEquals(404, call("GET", "/api/jobs/00000000-0000-0000-0000-000000000000", "").status());
        assertEquals(400, call("POST", "/api/solve", "").status());
    }

    @Test
    void asyncJobLifecycle() throws Exception {
        Response r = call("POST", "/api/jobs", TINY);
        assertEquals(202, r.status(), r.body());
        String id = ((JsonString) obj(r).members().get("id")).value();
        assertEquals("/api/jobs/" + id, r.headers().get("Location"));
        JsonObject job = null;
        for (int i = 0; i < 200; i++) {
            job = obj(call("GET", "/api/jobs/" + id, ""));
            if (job.members().get("status").equals(new JsonString("SUCCEEDED"))) break;
            Thread.sleep(10);
        }
        assertEquals(new JsonString("SUCCEEDED"), job.members().get("status"));
        JsonObject result = (JsonObject) job.members().get("result");
        assertEquals(26.0, num(result, "cost"), 1e-9);
        JsonArray list = (JsonArray) obj(call("GET", "/api/jobs", "")).members().get("jobs");
        assertEquals(1, list.size());
    }

    @Test
    void generatesInstances() {
        Response r = call("POST", "/api/instances/generate", "{\"customers\": 25, \"layout\": \"clustered\", \"seed\": 3}");
        assertEquals(200, r.status(), r.body());
        assertEquals(25, ((JsonArray) obj(r).members().get("customers")).size());
        assertEquals(422, call("POST", "/api/instances/generate", "{\"customers\": 0}").status());
        assertEquals(422, call("POST", "/api/instances/generate", "{\"layout\": \"spiral\"}").status());
    }
}
