package tr.ohvps.idempotency.store;

/**
 * İşlenen istek saklanacak bir yanıt üretmeden bittiğinde (hata, 5xx) bekleyen
 * kopyalara iletilir; kopyalar isteği yeniden işlemeyi dener.
 */
public class ReservationReleasedException extends RuntimeException {

    public ReservationReleasedException(String key) {
        super("Rezervasyon yanıt saklanmadan bırakıldı: " + key);
    }
}
