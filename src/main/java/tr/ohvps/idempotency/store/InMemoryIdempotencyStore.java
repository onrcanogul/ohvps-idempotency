package tr.ohvps.idempotency.store;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tek düğüm için bellek içi depo. Süresi dolan kayıtlar okunurken yok sayılır
 * ve periyodik olarak temizlenir.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public InMemoryIdempotencyStore(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public Reservation reserve(String key) {
        Instant now = clock.instant();
        CompletableFuture<StoredResponse> candidate = new CompletableFuture<>();
        Entry winner = entries.compute(key, (k, current) ->
                current == null || current.isExpired(now) ? new Entry(candidate, now.plus(ttl)) : current);
        return winner.response() == candidate
                ? new Reservation.Acquired()
                : new Reservation.Existing(winner.response());
    }

    @Override
    public void complete(String key, StoredResponse response) {
        Instant now = clock.instant();
        entries.computeIfPresent(key, (k, current) -> {
            current.response().complete(response);
            return new Entry(current.response(), now.plus(ttl));
        });
    }

    @Override
    public void release(String key) {
        entries.computeIfPresent(key, (k, current) -> {
            current.response().completeExceptionally(new ReservationReleasedException(key));
            return null;
        });
    }

    @Scheduled(fixedDelayString = "${ohvps.idempotency.cleanup-interval:60s}")
    public void evictExpired() {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> entry.isExpired(now));
    }

    int size() {
        return entries.size();
    }

    private record Entry(CompletableFuture<StoredResponse> response, Instant expiresAt) {

        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }
}
