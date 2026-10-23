package tr.ohvps.idempotency.store;

import java.util.concurrent.CompletableFuture;

/**
 * Bir checksum için {@link IdempotencyStore#reserve(String)} sonucu.
 */
public sealed interface Reservation {

    /** Checksum için geçerli kayıt yok; isteği bu çağrı işleyecek. */
    record Acquired() implements Reservation {
    }

    /**
     * Checksum için kayıt var. Yanıt hazırsa future tamamlanmıştır; aynı istek hâlâ
     * işleniyorsa işlem bitince tamamlanır. İşlem yanıt saklanmadan biterse
     * {@link ReservationReleasedException} ile tamamlanır.
     */
    record Existing(CompletableFuture<StoredResponse> response) implements Reservation {
    }
}
