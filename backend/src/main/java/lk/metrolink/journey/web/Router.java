package lk.metrolink.journey.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The smallest router that does the job: a method plus a path template such as {@code /journeys/{id}/settle}. */
final class Router {

    @FunctionalInterface
    interface Handler {
        ApiResponse handle(ApiRequest request) throws Exception;
    }

    private static final class Entry {
        private final String method;
        private final String[] segments;
        private final Handler handler;

        private Entry(String method, String[] segments, Handler handler) {
            this.method = method;
            this.segments = segments;
            this.handler = handler;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    void add(String method, String template, Handler handler) {
        entries.add(new Entry(method, split(template), handler));
    }

    /** Runs the handler that matches, or returns empty when nothing does. */
    Optional<ApiResponse> dispatch(ApiRequest request) throws Exception {
        String[] actual = split(request.path());
        for (Entry entry : entries) {
            if (!entry.method.equals(request.method())) {
                continue;
            }
            Map<String, String> params = match(entry.segments, actual);
            if (params != null) {
                return Optional.of(entry.handler.handle(request.withPathParams(params)));
            }
        }
        return Optional.empty();
    }

    private static Map<String, String> match(String[] template, String[] actual) {
        if (template.length != actual.length) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < template.length; i++) {
            String part = template[i];
            if (part.startsWith("{") && part.endsWith("}")) {
                params.put(part.substring(1, part.length() - 1), actual[i]);
            } else if (!part.equals(actual[i])) {
                return null;
            }
        }
        return params;
    }

    private static String[] split(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/", -1);
    }
}
