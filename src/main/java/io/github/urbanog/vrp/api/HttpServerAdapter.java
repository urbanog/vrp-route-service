package io.github.urbanog.vrp.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Transport layer on top of the JDK's built-in {@link HttpServer}: one virtual thread per
 * request, a request-size limit, a JSON content-type check, permissive CORS for local development,
 * and one access-log line per request. All application logic lives in {@link RouteApi}.
 */
public final class HttpServerAdapter implements AutoCloseable {
    /** Maximum accepted request body (2 MiB; a 2000-customer instance is about 120 KiB). */
    public static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private static final Logger LOG = Logger.getLogger(HttpServerAdapter.class.getName());
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final RouteApi api;

    public HttpServerAdapter(RouteApi api, InetSocketAddress address) throws IOException {
        this.api = api;
        this.server = HttpServer.create(address, 0);
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange ex) throws IOException {
        long t0 = System.nanoTime();
        int status = 500;
        try (ex) {
            var h = ex.getResponseHeaders();
            h.set("Access-Control-Allow-Origin", "*");
            h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            h.set("Access-Control-Allow-Headers", "Content-Type");
            h.set("Access-Control-Expose-Headers", "Location");
            String method = ex.getRequestMethod();
            if (method.equals("OPTIONS")) { // CORS pre-flight
                status = 204;
                ex.sendResponseHeaders(204, -1);
                return;
            }
            Router.Response resp;
            String body = readBody(ex.getRequestBody());
            if (body == null) {
                resp = RouteApi.problem(413, "Payload Too Large", "request body exceeds " + MAX_BODY_BYTES + " bytes", null);
            } else if (method.equals("POST") && !body.isEmpty() && !isJson(ex.getRequestHeaders().getFirst("Content-Type"))) {
                resp = RouteApi.problem(415, "Unsupported Media Type", "Content-Type must be application/json", null);
            } else {
                resp = api.dispatch(method, ex.getRequestURI().getPath(), parseQuery(ex.getRequestURI().getRawQuery()), body);
            }
            status = resp.status();
            byte[] bytes = resp.body().getBytes(StandardCharsets.UTF_8);
            h.set("Content-Type", resp.contentType());
            resp.headers().forEach(h::set);
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } finally {
            final int s = status;
            LOG.info(() -> String.format(Locale.ROOT, "%s %s -> %d (%.1f ms)",
                    ex.getRequestMethod(), ex.getRequestURI(), s, (System.nanoTime() - t0) / 1e6));
        }
    }

    private static boolean isJson(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
    }

    /** Reads the body as UTF-8, or returns {@code null} if it exceeds {@link #MAX_BODY_BYTES}. */
    private static String readBody(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_BODY_BYTES + 1);
        if (data.length > MAX_BODY_BYTES) return null;
        return new String(data, StandardCharsets.UTF_8);
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> q = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return q;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            q.putIfAbsent(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return q;
    }
}
