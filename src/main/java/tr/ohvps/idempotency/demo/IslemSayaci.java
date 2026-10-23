package tr.ohvps.idempotency.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Her işlem türünün iş mantığının kaç kez gerçekten çalıştığını sayar.
 * Kayıttan dönen yanıtlar sayacı artırmaz; idempotency'nin kanıtı budur.
 */
@Component
public class IslemSayaci {

    private final Map<IslemTuru, AtomicInteger> sayaclar = new ConcurrentHashMap<>();

    public int artir(IslemTuru tur) {
        return sayaclar.computeIfAbsent(tur, t -> new AtomicInteger()).incrementAndGet();
    }

    public int deger(IslemTuru tur) {
        AtomicInteger sayac = sayaclar.get(tur);
        return sayac == null ? 0 : sayac.get();
    }

    public void sifirla() {
        sayaclar.clear();
    }
}
