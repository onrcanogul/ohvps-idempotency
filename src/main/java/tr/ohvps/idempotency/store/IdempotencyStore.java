package tr.ohvps.idempotency.store;

/**
 * Checksum anahtarıyla yanıtları TTL süresince saklayan depo.
 * <p>
 * Uygulama tek düğümde çalışıyorsa {@link InMemoryIdempotencyStore} yeterlidir.
 * Birden fazla düğümde aynı sözleşme Redis ({@code SET NX PX}) ya da veritabanında
 * checksum üzerinde tekil indeksle uygulanmalıdır.
 */
public interface IdempotencyStore {

    /**
     * Checksum için işlem hakkı almaya çalışır. Süresi dolmamış bir kayıt varsa onu döner.
     */
    Reservation reserve(String key);

    /**
     * İşlenen isteğin yanıtını saklar ve bekleyen kopyaları bu yanıtla uyandırır.
     * Saklama süresi bu andan itibaren başlar.
     */
    void complete(String key, StoredResponse response);

    /**
     * Yanıt saklanmayacaksa rezervasyonu kaldırır; bekleyen kopyalar isteği yeniden işler.
     */
    void release(String key);
}
