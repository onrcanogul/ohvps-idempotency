package tr.ohvps.idempotency.checksum;

import java.nio.charset.StandardCharsets;

/**
 * Generates a checksum from {@code x-request-id|request.body}.
 * The body is used as the raw bytes sent by the YÖS; JSON is not re-serialized.
 */
public class ChecksumGenerator {

    private static final byte[] SEPARATOR = "|".getBytes(StandardCharsets.UTF_8);

    private final ChecksumAlgorithm algorithm;

    public ChecksumGenerator(ChecksumAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public String generate(String requestId, byte[] body) {
        return algorithm.checksum(requestId.getBytes(StandardCharsets.UTF_8), SEPARATOR, body);
    }
}
