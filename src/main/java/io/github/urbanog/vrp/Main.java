package io.github.urbanog.vrp;

import io.github.urbanog.vrp.api.HttpServerAdapter;
import io.github.urbanog.vrp.api.RouteApi;
import io.github.urbanog.vrp.io.InstanceCatalog;
import io.github.urbanog.vrp.jobs.InMemoryJobRepository;
import io.github.urbanog.vrp.jobs.JobService;
import io.github.urbanog.vrp.solver.CvrpSolver;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;

/**
 * Wires the application together (manual dependency injection) and starts the HTTP server.
 *
 * <p>Configuration via environment: {@code PORT} (default 8080), {@code BIND} (default
 * 127.0.0.1), {@code MAX_CONCURRENT_JOBS} (default: available processors).
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(env("PORT", "8080"));
        String bind = env("BIND", "127.0.0.1");
        int maxJobs = Integer.parseInt(env("MAX_CONCURRENT_JOBS", String.valueOf(Runtime.getRuntime().availableProcessors())));

        var catalog = InstanceCatalog.loadBundled();
        var solver = new CvrpSolver();
        var jobs = new JobService(new InMemoryJobRepository(), solver, Clock.systemUTC(), maxJobs);
        var api = new RouteApi(catalog, solver, jobs);
        var server = new HttpServerAdapter(api, new InetSocketAddress(bind, port));

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            jobs.close();
            stopped.countDown();
        }));
        server.start();
        System.out.printf("vrp-route-service listening on http://%s:%d (max %d concurrent jobs)%n", bind, server.port(), maxJobs);
        stopped.await();
    }

    private static String env(String key, String dflt) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? dflt : v;
    }
}
