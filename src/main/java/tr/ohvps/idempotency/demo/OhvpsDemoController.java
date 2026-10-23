package tr.ohvps.idempotency.demo;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample ÖHVPS endpoints demonstrating the idempotency mechanism.
 * Instead of real business logic, each call generates a new id and increments a counter;
 * in a replayed response the id and the counter stay the same.
 */
@RestController
@RequestMapping("/ohvps")
public class OhvpsDemoController {

    private final OperationCounter operationCounter;
    private final Clock clock;

    public OhvpsDemoController(OperationCounter operationCounter, Clock clock) {
        this.operationCounter = operationCounter;
        this.clock = clock;
    }

    @PostMapping("/obh/s1.1/odeme-emri-rizasi")
    public ResponseEntity<Map<String, Object>> paymentOrderConsent(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> request) {
        return process(OperationType.PAYMENT_ORDER_CONSENT, requestId, request);
    }

    @PostMapping("/gkd/s1.1/erisim-belirteci")
    public ResponseEntity<Map<String, Object>> accessToken(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> request) {
        return process(OperationType.ACCESS_TOKEN, requestId, request);
    }

    @PostMapping("/obh/s1.1/odeme-emri")
    public ResponseEntity<Map<String, Object>> paymentOrder(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> request) {
        return process(OperationType.PAYMENT_ORDER, requestId, request);
    }

    @PostMapping("/hbh/s1.1/hesap-bilgisi-rizasi")
    public ResponseEntity<Map<String, Object>> accountInformationConsent(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> request) {
        return process(OperationType.ACCOUNT_INFORMATION_CONSENT, requestId, request);
    }

    @PostMapping("/obh/s1.1/ileri-tarihli-odeme-emri-rizasi")
    public ResponseEntity<Map<String, Object>> futureDatedPaymentOrderConsent(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> request) {
        return process(OperationType.FUTURE_DATED_PAYMENT_ORDER_CONSENT, requestId, request);
    }

    @PostMapping("/obh/s1.1/ileri-tarihli-odeme-emri")
    public ResponseEntity<Map<String, Object>> futureDatedPaymentOrder(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> request) {
        return process(OperationType.FUTURE_DATED_PAYMENT_ORDER, requestId, request);
    }

    @PostMapping("/obh/s1.1/duzenli-odeme-emri-rizasi")
    public ResponseEntity<Map<String, Object>> recurringPaymentOrderConsent(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> request) {
        return process(OperationType.RECURRING_PAYMENT_ORDER_CONSENT, requestId, request);
    }

    @PostMapping("/obh/s1.1/duzenli-odeme-emri")
    public ResponseEntity<Map<String, Object>> recurringPaymentOrder(
            @RequestHeader("x-request-id") String requestId, @RequestBody Map<String, Object> request) {
        return process(OperationType.RECURRING_PAYMENT_ORDER, requestId, request);
    }

    private ResponseEntity<Map<String, Object>> process(OperationType type, String requestId,
                                                        Map<String, Object> request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operationId", UUID.randomUUID().toString());
        response.put("operationType", type.name());
        response.put("sequence", operationCounter.increment(type));
        response.put("createdAt", Instant.now(clock).toString());
        response.put("request", request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("x-request-id", requestId)
                .body(response);
    }
}
