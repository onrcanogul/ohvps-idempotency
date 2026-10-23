# ÖHVPS Idempotency Altyapısı

ÖHVPS'nin HHS'lere getirdiği idempotency kuralını Spring Boot üzerinde uygulayan örnek altyapı.

## Kural

- HHS, `x-request-id|request.body` birleşiminden bir checksum üretir (CRC32, MD5 vb.).
- Bu checksum ile YÖS'e döndüğü yanıtı **5 dakika** saklar.
- Aynı istek numarası ve aynı gövdeyle gelen çağrıya, yeniden işlem yapmadan kayıtlı yanıtı döner.
- 5 dakikadan sonra aynı istek yeni bir istek sayılır.
- Kural şu POST'lar için zorunludur: ödeme emri rızası, erişim belirteci, ödeme emri, hesap bilgisi rızası,
  ileri tarihli ödeme emri rızası ve emri, düzenli tekrarlı ödeme emri rızası ve emri.

## Akış

```
YÖS ──POST + x-request-id──▶ IdempotencyFilter
                                │
                                ├─ kapsam dışı yol / POST değil ─────────▶ controller (filtre devreye girmez)
                                ├─ x-request-id yok ─────────────────────▶ 400
                                │
                                ├─ checksum = hash(x-request-id + "|" + ham gövde)
                                │
                                ├─ store.reserve(checksum)
                                │     ├─ Acquired  ─▶ controller çalışır ─▶ yanıt saklanır (TTL 5 dk) ─▶ YÖS
                                │     └─ Existing  ─▶ kayıtlı yanıt (durum + başlıklar + gövde) ───────▶ YÖS
                                │                     · istek hâlâ işleniyorsa sonucu beklenir
                                │                     · bekleme süresi aşılırsa 409
```

## Bileşenler

| Sınıf | Görev |
|---|---|
| `checksum.ChecksumAlgorithm` | CRC32, MD5, SHA256 |
| `checksum.ChecksumGenerator` | `x-request-id|body` checksum'ı; gövde ham baytlarıyla kullanılır |
| `web.CachedBodyHttpServletRequest` | Gövdeyi bir kez okur, controller'ın yeniden okumasına izin verir |
| `store.IdempotencyStore` | `reserve` / `complete` / `release` sözleşmesi |
| `store.InMemoryIdempotencyStore` | Tek düğüm için TTL'li bellek içi depo, periyodik temizlik |
| `web.IdempotencyFilter` | Kuralı kapsamdaki POST'lara uygular |
| `web.OhvpsErrorWriter` | Filtre hatalarını ÖHVPS hata gövdesi yapısında yazar |
| `demo.OhvpsDemoController` | Sekiz zorunlu POST için örnek uç nokta |

## Tasarım kararları

- **Checksum anahtarı:** Kuraldaki gibi yalnızca `x-request-id|body`. Gövde JSON olarak yeniden
  serileştirilmez; boşluk ya da alan sırası farkı yeni istek sayılır.
- **Saklanan yanıt:** Durum kodu, başlıklar (`x-request-id`, imza başlıkları vb.) ve gövde birebir saklanır.
  Gövde değişmediği için `x-jws-signature` gibi imzalar da geçerli kalır.
- **TTL başlangıcı:** Yanıtın saklandığı an.
- **Eşzamanlı kopyalar:** Aynı istek işlenirken gelen kopya, ilk işlemin yanıtını bekler (`in-flight-wait`).
  İş mantığı yalnızca bir kez çalışır.
- **5xx yanıtlar:** Varsayılan olarak saklanmaz (`store-server-errors: false`); YÖS'ün yeniden denemesi
  işlemi tekrar çalıştırır. 2xx ve 4xx yanıtlar saklanır.
- **Kayıttan dönüş işareti:** Kayıtlı yanıta `X-Idempotency-Replayed: true` eklenir. Birebir aynı yanıt
  istenirse `replay-indicator-header` boş bırakılır.
- **Filtre sırası:** Kimlik doğrulama ve imza doğrulama filtrelerinden sonra çalışmalıdır.
- **Hata kodları:** `TR.OHVPS.Idempotency.*` kodları örnektir; HHS'nin hata kataloğuyla eşlenmelidir.

## Ayarlar

```yaml
ohvps:
  idempotency:
    enabled: true
    request-id-header: x-request-id
    algorithm: SHA256          # CRC32 | MD5 | SHA256
    ttl: 5m
    in-flight-wait: 30s
    store-server-errors: false
    replay-indicator-header: X-Idempotency-Replayed
    cleanup-interval: 60s
    paths:
      - /ohvps/obh/s1.1/odeme-emri-rizasi
      - /ohvps/gkd/s1.1/erisim-belirteci
      - /ohvps/obh/s1.1/odeme-emri
      - /ohvps/hbh/s1.1/hesap-bilgisi-rizasi
      - /ohvps/obh/s1.1/ileri-tarihli-odeme-emri-rizasi
      - /ohvps/obh/s1.1/ileri-tarihli-odeme-emri
      - /ohvps/obh/s1.1/duzenli-odeme-emri-rizasi
      - /ohvps/obh/s1.1/duzenli-odeme-emri
```

Yollar örnek olarak `s1.1` sürümüyle verilmiştir; kullanılan API sürümüne göre güncellenmelidir.

## Çalıştırma

```bash
./mvnw spring-boot:run
```

```bash
curl -i -X POST localhost:8080/ohvps/obh/s1.1/odeme-emri -H "x-request-id: abc-1" -H "Content-Type: application/json" -d '{"tutar":"100.00"}'
```

Aynı komut 5 dakika içinde tekrar çalıştırıldığında aynı `islemNo` ve `X-Idempotency-Replayed: true`
başlığı döner; `islemSirasi` artmaz. `x-request-id` ya da gövde değiştiğinde yeni işlem oluşur.

## Testler

```bash
./mvnw test
```

- `ChecksumGeneratorTest`: üç algoritma için checksum davranışı
- `InMemoryIdempotencyStoreTest`: TTL, eşzamanlı rezervasyon, bırakma ve temizlik
- `IdempotencyIntegrationTest`: sekiz uç noktanın tamamında kayıttan dönüş, farklı gövde/numara,
  4:59 ve 5:00 sınırları, eksik başlık, eşzamanlı kopyalar
- `IdempotencyScopeTest`: 5xx'in saklanmaması, 4xx'in saklanması, kapsam dışı uç noktalar

## Üretime geçerken

- **Çok düğüm:** `InMemoryIdempotencyStore` yerine paylaşımlı bir depo gerekir.
  - Redis: `SET checksum <in-progress> NX PX 300000` ile rezervasyon, tamamlanınca yanıtla güncelleme.
  - Veritabanı: `checksum` üzerinde tekil indeks, `expires_at` sütunu ve periyodik silme.
- **Gövde boyutu:** İstek ve yanıt belleğe alındığı için gövde boyutuna üst sınır konmalıdır.
- **Asenkron controller'lar** (`DeferredResult`, `Callable`) bu filtreyle desteklenmez.
