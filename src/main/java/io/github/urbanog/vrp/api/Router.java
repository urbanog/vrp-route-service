package io.github.urbanog.vrp.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Minimal method + path-template router. Templates use {@code {name}} segments, e.g. {@code
 * /api/jobs/{id}}. Matching is exact per segment, so {@code /api/jobs} and {@code
 * /api/jobs/{id}} never shadow each other.
 */
public final class Router {
    /** Handler for a matched request. */
    @FunctionalInterface
    public interface Handler {
        Response handle(Request request) throws Exception;
    }

    /** A matched request: method, path parameters, query string and raw body. */
    public record Request(String method, String path, Map<String, String> pathParams, Map<String, String> query, String body) {}

    /** A response: status, content type, body text and extra headers. */
    public record Response(int status, String contentType, String body, Map<String, String> headers) {
        public Response {
            headers = Map.copyOf(headers);
        }

        public Response(int status, String contentType, String body) {
            this(status, contentType, body, Map.of());
        }

        public static Response json(int status, String body) {
            return new Response(status, "application/json; charset=utf-8", body);
        }

        public Response withHeader(String name, String value) {
            var h = new java.util.LinkedHashMap<>(headers);
            h.put(name, value);
            return new Response(status, contentType, body, h);
        }
    }

    private record Route(String method, String[] segments, Handler handler) {}

    /** Result of a lookup: a handler with its bound parameters, or the allowed methods for the path. */
    public sealed interface Match permits Found, MethodNotAllowed, NotFound {}

    public record Found(Handler handler, Map<String, String> params) implements Match {}

    public record MethodNotAllowed(Set<String> allowed) implements Match {}

    public record NotFound() implements Match {}

    private final List<Route> routes = new ArrayList<>();

    public Router add(String method, String template, Handler handler) {
        routes.add(new Route(method, split(template), handler));
        return this;
    }

    public Match match(String method, String path) {
        String[] segs = split(path);
        Set<String> allowed = new LinkedHashSet<>();
        for (Route r : routes) {
            Optional<Map<String, String>> params = bind(r.segments, segs);
            if (params.isEmpty()) continue;
            if (r.method.equals(method)) return new Found(r.handler, params.get());
            allowed.add(r.method);
        }
        return allowed.isEmpty() ? new NotFound() : new MethodNotAllowed(allowed);
    }

    private static Optional<Map<String, String>> bind(String[] template, String[] path) {
        if (template.length != path.length) return Optional.empty();
        Map<String, String> params = new TreeMap<>();
        for (int i = 0; i < template.length; i++) {
            String t = template[i];
            if (t.startsWith("{") && t.endsWith("}")) {
                if (path[i].isEmpty()) return Optional.empty();
                params.put(t.substring(1, t.length() - 1), path[i]);
            } else if (!t.equals(path[i])) {
                return Optional.empty();
            }
        }
        return Optional.of(params);
    }

    private static String[] split(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p.isEmpty() ? new String[0] : p.split("/", -1);
    }
}
