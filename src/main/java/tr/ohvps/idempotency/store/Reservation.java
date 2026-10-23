package tr.ohvps.idempotency.store;

import java.util.concurrent.CompletableFuture;

/**
 * Result of {@link IdempotencyStore#reserve(String)} for a checksum.
 */
public sealed interface Reservation {

    /** No valid entry for the checksum; this call will process the request. */
    record Acquired() implements Reservation {
    }

    /**
     * An entry exists for the checksum. If the response is ready the future is already
     * complete; if the same request is still being processed it completes when processing
     * ends. If processing ends without storing a response it completes with
     * {@link ReservationReleasedException}.
     */
    record Existing(CompletableFuture<StoredResponse> response) implements Reservation {
    }
}
