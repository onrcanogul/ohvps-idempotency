package tr.ohvps.idempotency.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import tr.ohvps.idempotency.checksum.ChecksumAlgorithm;

/**
 * ÖHVPS idempotency ayarları.
 *
 * @param enabled                altyapıyı tamamen açar/kapatır
 * @param requestIdHeader        istek numarasının geldiği başlık
 * @param algorithm              checksum algoritması
 * @param ttl                    yanıtın saklanma süresi; ÖHVPS kuralı 5 dakikadır
 * @param inFlightWait           aynı istek işlenirken gelen kopyanın bekleyeceği en uzun süre
 * @param storeServerErrors      5xx yanıtların da saklanıp saklanmayacağı
 * @param replayIndicatorHeader  kayıttan dönen yanıta eklenen başlık; boşsa eklenmez
 * @param paths                  kuralın uygulandığı POST uç noktaları (Spring path pattern)
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
