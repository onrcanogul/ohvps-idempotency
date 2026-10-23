package tr.ohvps.idempotency.checksum;

import java.nio.charset.StandardCharsets;

/**
 * {@code x-request-id|request.body} birleşiminden checksum üretir.
 * Gövde, YÖS'ün gönderdiği ham baytlarla kullanılır; JSON yeniden serileştirilmez.
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
