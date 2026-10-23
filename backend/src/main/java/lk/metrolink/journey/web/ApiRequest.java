package lk.metrolink.journey.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** One HTTP request as the routes see it: its path parameters and its JSON body, parsed on demand. */
public final class ApiRequest {
    private final String method;
    private final String path;
    private final Map<String, String> pathParams;
    private final byte[] rawBody;

    ApiRequest(String method, String path, Map<String, String> pathParams, byte[] rawBody) {
        this.method = method;
        this.path = path;
        this.pathParams = pathParams;
        this.rawBody = rawBody;
    }

    ApiRequest withPathParams(Map<String, String> params) {
        return new ApiRequest(method, path, params, rawBody);
    }

    public String method() { return method; }
    public String path() { return path; }

    /** The parsed JSON body, or null when there is none. Throws BadJsonException when it is not JSON. */
    public JsonNode body() {
        if (rawBody.length == 0) {
            return null;
        }
        try {
            return Json.MAPPER.readTree(rawBody);
        } catch (JsonProcessingException e) {
            throw new BadJsonException();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A path parameter that must be a positive whole number (400 otherwise). */
    public long id(String name) {
        try {
            long value = Long.parseLong(pathParams.get(name).trim());
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the validation error below
        }
        throw new ValidationException(name, "Invalid input: expected a positive whole number");
    }
}
