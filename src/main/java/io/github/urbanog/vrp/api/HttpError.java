package io.github.urbanog.vrp.api;

/** An error with a fixed HTTP status, rendered as an RFC 9457 problem document. */
public final class HttpError extends RuntimeException {
    private final int status;
    private final String title;

    public HttpError(int status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    public static HttpError notFound(String detail) {
        return new HttpError(404, "Not Found", detail);
    }

    public static HttpError badRequest(String detail) {
        return new HttpError(400, "Bad Request", detail);
    }
}
