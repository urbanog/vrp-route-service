package io.github.urbanog.vrp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.urbanog.vrp.io.InstanceCatalog;
import io.github.urbanog.vrp.jobs.InMemoryJobRepository;
import io.github.urbanog.vrp.jobs.JobService;
import io.github.urbanog.vrp.solver.CvrpSolver;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end tests over a real socket on an ephemeral port. */
class HttpServerAdapterTest {
    private JobService jobs;
    private HttpServerAdapter server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void start() throws Exception {
        jobs = new JobService(new InMemoryJobRepository(), new CvrpSolver(), Clock.systemUTC(), 2);
        var api = new RouteApi(InstanceCatalog.loadBundled(), new CvrpSolver(), jobs);
        server = new HttpServerAdapter(api, new InetSocketAddress("127.0.0.1", 0));
        server.start();
        base = "http://127.0.0.1:" + server.port();
        client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    }

    @AfterEach
    void stop() {
        server.close();
        jobs.close();
    }

    private HttpResponse<String> post(String path, String contentType, String body) throws Exception {
        var req = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesJsonWithCors() throws Exception {
        var r = client.send(HttpRequest.newBuilder(URI.create(base + "/api/health")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertEquals("*", r.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    void rejectsNonJsonContentType() throws Exception {
        var r = post("/api/solve", "text/plain", "{}");
        assertEquals(415, r.statusCode());
    }

    @Test
    void rejectsOversizedBodies() throws Exception {
        var r = post("/api/solve", "application/json", " ".repeat(HttpServerAdapter.MAX_BODY_BYTES + 10));
        assertEquals(413, r.statusCode());
    }

    @Test
    void handlesConcurrentSolves() throws Exception {
        List<CompletableFuture<HttpResponse<String>>> fs = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            var req = HttpRequest.newBuilder(URI.create(base + "/api/solve"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"instanceName\":\"C-n50-k5\",\"options\":{\"algorithm\":\"local-search\",\"seed\":" + i + "}}"))
                    .build();
            fs.add(client.sendAsync(req, HttpResponse.BodyHandlers.ofString()));
        }
        for (var f : fs) assertEquals(200, f.get().statusCode());
    }

    @Test
    void asyncJobReturnsLocationHeader() throws Exception {
        var r = post("/api/jobs", "application/json", RouteApiTest.TINY);
        assertEquals(202, r.statusCode());
        assertTrue(r.headers().firstValue("Location").orElse("").startsWith("/api/jobs/"));
    }
}
