package lk.metrolink.journey.web;

/** A status code and anything Jackson can write as JSON. */
public final class ApiResponse {
    private final int status;
    private final Object body;

    public ApiResponse(int status, Object body) {
        this.status = status;
        this.body = body;
    }

    public static ApiResponse ok(Object body) {
        return new ApiResponse(200, body);
    }

    public int status() { return status; }
    public Object body() { return body; }
}
