package tr.ohvps.idempotency.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChecksumGeneratorTest {

    private static final byte[] BODY = "{\"tutar\":\"100.00\"}".getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest
    @EnumSource(ChecksumAlgorithm.class)
    void ayniIstekNumarasiVeGovdeAyniChecksumuUretir(ChecksumAlgorithm algorithm) {
        ChecksumGenerator generator = new ChecksumGenerator(algorithm);

        assertThat(generator.generate("req-1", BODY)).isEqualTo(generator.generate("req-1", BODY.clone()));
    }

    @ParameterizedTest
    @EnumSource(ChecksumAlgorithm.class)
    void farkliIstekNumarasiFarkliChecksumUretir(ChecksumAlgorithm algorithm) {
        ChecksumGenerator generator = new ChecksumGenerator(algorithm);

        assertThat(generator.generate("req-1", BODY)).isNotEqualTo(generator.generate("req-2", BODY));
    }

    @ParameterizedTest
    @EnumSource(ChecksumAlgorithm.class)
    void farkliGovdeFarkliChecksumUretir(ChecksumAlgorithm algorithm) {
        ChecksumGenerator generator = new ChecksumGenerator(algorithm);
        byte[] otherBody = "{\"tutar\":\"200.00\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(generator.generate("req-1", BODY)).isNotEqualTo(generator.generate("req-1", otherBody));
    }

    @Test
    void checksumIstekNumarasiBoruVeGovdeBirlesimindenHesaplanir() {
        ChecksumGenerator generator = new ChecksumGenerator(ChecksumAlgorithm.MD5);
        byte[] joined = "req-1|{\"tutar\":\"100.00\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(generator.generate("req-1", BODY)).isEqualTo(ChecksumAlgorithm.MD5.checksum(joined));
    }
}
