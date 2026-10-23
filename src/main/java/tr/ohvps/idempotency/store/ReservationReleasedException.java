package tr.ohvps.idempotency.store;

/**
 * Delivered to waiting duplicates when the request in progress ends without a storable
 * response (exception, 5xx); the duplicates then try to process the request themselves.
 */
public class ReservationReleasedException extends RuntimeException {

    public ReservationReleasedException(String key) {
        super("Reservation released without a stored response: " + key);
    }
}
