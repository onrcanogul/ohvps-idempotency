package tr.ohvps.idempotency.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import tr.ohvps.idempotency.checksum.ChecksumAlgorithm;

/**
 * ÖHVPS idempotency settings.
 *
 * @param enabled                turns the whole mechanism on/off
 * @param requestIdHeader        header carrying the request id
 * @param algorithm              checksum algorithm
 * @param ttl                    how long a response is kept; the ÖHVPS rule is 5 minutes
 * @param inFlightWait           maximum time a duplicate waits while the same request is still being processed
 * @param storeServerErrors      whether 5xx responses are stored as well
 * @param replayIndicatorHeader  header added to replayed responses; not added when blank
 * @param paths                  POST endpoints the rule applies to (Spring path patterns)
 */
@ConfigurationProperties(prefix = "ohvps.idempotency")
public record IdempotencyProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("x-request-id") String requestIdHeader,
        @DefaultValue("SHA256") ChecksumAlgorithm algorithm,
        @DefaultValue("5m") Duration ttl,
        @DefaultValue("30s") Duration inFlightWait,
        @DefaultValue("false") boolean storeServerErrors,
        @DefaultValue("X-Idempotency-Replayed") String replayIndicatorHeader,
        List<String> paths) {

    public IdempotencyProperties {
        paths = paths == null ? List.of() : List.copyOf(paths);
    }
}
