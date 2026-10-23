package tr.ohvps.idempotency.store;

import tr.ohvps.idempotency.store.model.Reservation;
import tr.ohvps.idempotency.store.model.StoredResponse;

/**
 * Store that keeps responses for the TTL, keyed by checksum.
 * <p>
 * {@code InMemoryIdempotencyStore} is enough when the application runs on a single node.
 * With multiple nodes the same contract should be implemented with Redis ({@code SET NX PX})
 * or with a unique index on the checksum in a database.
 */
public interface IdempotencyStore {

    /**
     * Tries to acquire the right to process the checksum. Returns the existing entry if one
     * has not expired yet.
     */
    Reservation reserve(String key);

    /**
     * Stores the response of the processed request and wakes waiting duplicates with it.
     * The retention period starts at this moment.
     */
    void complete(String key, StoredResponse response);

    /**
     * Removes the reservation when no response will be stored; waiting duplicates process
     * the request again.
     */
    void release(String key);
}
