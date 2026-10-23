# ÖHVPS Idempotency

A sample Spring Boot implementation of the idempotency rule that ÖHVPS (the Turkish open banking API standard)
imposes on HHSs (account servicing providers, i.e. banks) for calls made by YÖSs (third-party providers).

## The rule

- The HHS generates a checksum from `x-request-id|request.body` (CRC32, MD5, etc.).
- Using that checksum, it keeps the response returned to the YÖS for **5 minutes**.
- A call with the same request id and the same body gets the stored response without being processed again.
- After 5 minutes, the same request counts as a new request.
- The rule is mandatory for these POSTs: payment order consent, access token, payment order,
  account information consent, future-dated payment order consent and order, recurring payment order
  consent and order.

## Flow

```
YÖS ──POST + x-request-id──▶ IdempotencyFilter
                                │
                                ├─ path out of scope / not a POST ───────▶ controller (filter does nothing)
                                ├─ no x-request-id ──────────────────────▶ 400
                                │
                                ├─ checksum = hash(x-request-id + "|" + raw body)
                                │
                                ├─ store.reserve(checksum)
                                │     ├─ Acquired  ─▶ controller runs ─▶ response stored (TTL 5 min) ─▶ YÖS
                                │     └─ Existing  ─▶ stored response (status + headers + body) ──────▶ YÖS
                                │                     · if the request is still in progress, wait for its result
                                │                     · 409 if the wait times out
```

## Components

| Class | Responsibility |
|---|---|
| `checksum.ChecksumAlgorithm` | CRC32, MD5, SHA256 |
| `checksum.ChecksumGenerator` | `x-request-id|body` checksum; the body is used as raw bytes |
| `web.CachedBodyHttpServletRequest` | Reads the body once and lets the controller read it again |
| `store.IdempotencyStore` | `reserve` / `complete` / `release` contract |
| `store.InMemoryIdempotencyStore` | Single-node in-memory store with TTL and periodic eviction |
| `web.IdempotencyFilter` | Applies the rule to the configured POST endpoints |
| `web.OhvpsErrorWriter` | Writes filter errors in the ÖHVPS error body format |
| `demo.OhvpsDemoController` | Sample endpoints for the eight mandatory POSTs (`OperationCounter` tracks how many times each one really ran) |

## Design decisions

- **Checksum key:** Only `x-request-id|body`, as the rule states. The body is not re-serialized as JSON;
  a difference in whitespace or field order counts as a new request.
- **Stored response:** Status code, headers (`x-request-id`, signature headers, etc.) and body are stored as is.
  Since the body does not change, signatures such as `x-jws-signature` remain valid.
- **TTL start:** The moment the response is stored.
- **Concurrent duplicates:** A duplicate arriving while the same request is in progress waits for the first
  response (`in-flight-wait`). The business logic runs only once.
- **5xx responses:** Not stored by default (`store-server-errors: false`), so a retry by the YÖS processes the
  operation again. 2xx and 4xx responses are stored.
- **Replay indicator:** `X-Idempotency-Replayed: true` is added to replayed responses. Leave
  `replay-indicator-header` blank if replays must be byte-for-byte identical.
- **Filter order:** Must run after authentication and signature verification filters.
- **Error codes:** The `TR.OHVPS.Idempotency.*` codes are examples and should be mapped to the HHS's error catalogue.

## Configuration

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

The paths use version `s1.1` as an example; update them to match the API version in use.

## Running

```bash
./mvnw spring-boot:run
```

```bash
curl -i -X POST localhost:8080/ohvps/obh/s1.1/odeme-emri -H "x-request-id: abc-1" -H "Content-Type: application/json" -d '{"amount":"100.00"}'
```

Running the same command again within 5 minutes returns the same `operationId` and the
`X-Idempotency-Replayed: true` header; `sequence` does not increase. Changing `x-request-id` or the body
creates a new operation.

## Tests

```bash
./mvnw test
```

- `ChecksumGeneratorTest`: checksum behaviour for all three algorithms
- `InMemoryIdempotencyStoreTest`: TTL, concurrent reservation, release and eviction
- `IdempotencyIntegrationTest`: replay on all eight endpoints, different body/request id,
  the 4:59 and 5:00 boundaries, missing header, concurrent duplicates
- `IdempotencyScopeTest`: 5xx not stored, 4xx stored, out-of-scope endpoints

## Going to production

- **Multiple nodes:** Replace `InMemoryIdempotencyStore` with a shared store.
  - Redis: reserve with `SET checksum <in-progress> NX PX 300000`, then update with the response on completion.
  - Database: unique index on `checksum`, an `expires_at` column and periodic deletion.
- **Body size:** Requests and responses are buffered in memory, so put an upper limit on body size.
- **Async controllers** (`DeferredResult`, `Callable`) are not supported by this filter.
