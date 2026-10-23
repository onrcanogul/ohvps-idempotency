package tr.ohvps.idempotency.store.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

import tr.ohvps.idempotency.store.exception.ReservationReleasedException;
import tr.ohvps.idempotency.store.model.Reservation;
import tr.ohvps.idempotency.store.model.StoredResponse;
import tr.ohvps.idempotency.support.MutableClock;

class InMemoryIdempotencyStoreTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(clock, TTL);

    @Test
    void firstReservationIsAcquired() {
        assertThat(store.reserve("k")).isInstanceOf(Reservation.Acquired.class);
    }

    @Test
    void duplicateDuringProcessingGetsSameResponseOnCompletion() {
        store.reserve("k");
        Reservation duplicate = store.reserve("k");

        assertThat(duplicate).isInstanceOf(Reservation.Existing.class);
        Reservation.Existing existing = (Reservation.Existing) duplicate;
        assertThat(existing.response()).isNotDone();

        store.complete("k", response("first"));

        assertThat(existing.response().join().body()).isEqualTo(bytes("first"));
    }

    @Test
    void responseIsReturnedForFiveMinutes() {
        store.reserve("k");
        store.complete("k", response("first"));

        clock.advance(TTL.minusSeconds(1));

        Reservation reservation = store.reserve("k");
        assertThat(reservation).isInstanceOf(Reservation.Existing.class);
        assertThat(((Reservation.Existing) reservation).response().join().body()).isEqualTo(bytes("first"));
    }

    @Test
    void sameRequestAfterFiveMinutesIsNewRequest() {
        store.reserve("k");
        store.complete("k", response("first"));

        clock.advance(TTL);

        assertThat(store.reserve("k")).isInstanceOf(Reservation.Acquired.class);
    }

    @Test
    void ttlStartsWhenResponseIsStored() {
        store.reserve("k");
        clock.advance(Duration.ofMinutes(2));
        store.complete("k", response("first"));

        clock.advance(TTL.minusSeconds(1));

        assertThat(store.reserve("k")).isInstanceOf(Reservation.Existing.class);
    }

    @Test
    void releasedReservationWakesWaitersAndAllowsReprocessing() {
        store.reserve("k");
        Reservation.Existing waiting = (Reservation.Existing) store.reserve("k");

        store.release("k");

        assertThatThrownBy(() -> waiting.response().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ReservationReleasedException.class);
        assertThat(store.reserve("k")).isInstanceOf(Reservation.Acquired.class);
    }

    @Test
    void expiredEntriesAreEvicted() {
        store.reserve("a");
        store.complete("a", response("a"));
        clock.advance(Duration.ofMinutes(3));
        store.reserve("b");
        store.complete("b", response("b"));

        clock.advance(Duration.ofMinutes(2));
        store.evictExpired();

        assertThat(store.size()).isEqualTo(1);
    }

    private static StoredResponse response(String body) {
        return new StoredResponse(201, Map.of("Content-Type", List.of("application/json")), bytes(body));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
