package tr.ohvps.idempotency.demo;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * İdempotency altyapısını göstermek için örnek ÖHVPS uç noktaları.
 * Gerçek iş mantığı yerine her çağrıda yeni bir numara üretir ve sayacı artırır;
 * kayıttan dönen yanıtta numara ve sayaç değişmez.
 */
@RestController
@RequestMapping("/ohvps")
public class OhvpsDemoController {

    private final IslemSayaci islemSayaci;
    private final Clock clock;

    public OhvpsDemoController(IslemSayaci islemSayaci, Clock clock) {
        this.islemSayaci = islemSayaci;
        this.clock = clock;
    }

    @PostMapping("/obh/s1.1/odeme-emri-rizasi")
    public ResponseEntity<Map<String, Object>> odemeEmriRizasi(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> istek) {
        return islemYap(IslemTuru.ODEME_EMRI_RIZASI, requestId, istek);
    }

    @PostMapping("/gkd/s1.1/erisim-belirteci")
    public ResponseEntity<Map<String, Object>> erisimBelirteci(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> istek) {
        return islemYap(IslemTuru.ERISIM_BELIRTECI, requestId, istek);
    }

    @PostMapping("/obh/s1.1/odeme-emri")
    public ResponseEntity<Map<String, Object>> odemeEmri(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> istek) {
        return islemYap(IslemTuru.ODEME_EMRI, requestId, istek);
    }

    @PostMapping("/hbh/s1.1/hesap-bilgisi-rizasi")
    public ResponseEntity<Map<String, Object>> hesapBilgisiRizasi(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> istek) {
        return islemYap(IslemTuru.HESAP_BILGISI_RIZASI, requestId, istek);
    }

    @PostMapping("/obh/s1.1/ileri-tarihli-odeme-emri-rizasi")
    public ResponseEntity<Map<String, Object>> ileriTarihliOdemeEmriRizasi(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> istek) {
        return islemYap(IslemTuru.ILERI_TARIHLI_ODEME_EMRI_RIZASI, requestId, istek);
    }

    @PostMapping("/obh/s1.1/ileri-tarihli-odeme-emri")
    public ResponseEntity<Map<String, Object>> ileriTarihliOdemeEmri(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> istek) {
        return islemYap(IslemTuru.ILERI_TARIHLI_ODEME_EMRI, requestId, istek);
    }

    @PostMapping("/obh/s1.1/duzenli-odeme-emri-rizasi")
    public ResponseEntity<Map<String, Object>> duzenliOdemeEmriRizasi(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> istek) {
        return islemYap(IslemTuru.DUZENLI_ODEME_EMRI_RIZASI, requestId, istek);
    }

    @PostMapping("/obh/s1.1/duzenli-odeme-emri")
    public ResponseEntity<Map<String, Object>> duzenliOdemeEmri(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> istek) {
        return islemYap(IslemTuru.DUZENLI_ODEME_EMRI, requestId, istek);
    }

    private ResponseEntity<Map<String, Object>> islemYap(IslemTuru tur, String requestId, Map<String, Object> istek) {
        Map<String, Object> yanit = new LinkedHashMap<>();
        yanit.put("islemNo", UUID.randomUUID().toString());
        yanit.put("islemTuru", tur.name());
        yanit.put("islemSirasi", islemSayaci.artir(tur));
        yanit.put("olusturmaZamani", Instant.now(clock).toString());
        yanit.put("istek", istek);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("x-request-id", requestId)
                .body(yanit);
    }
}
