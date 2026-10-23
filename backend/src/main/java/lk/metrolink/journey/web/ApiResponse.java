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

    /** Something new was made, for example a registered customer. */
    public static ApiResponse created(Object body) {
        return new ApiResponse(201, body);
    }

    public int status() { return status; }
    public Object body() { return body; }
}
