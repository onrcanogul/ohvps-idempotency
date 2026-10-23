package tr.ohvps.idempotency.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact copy of the response returned to the YÖS: status code, headers and body.
 */
public record StoredResponse(int status, Map<String, List<String>> headers, byte[] body) {

    public StoredResponse {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        headers.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        headers = Collections.unmodifiableMap(copy);
        body = body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
