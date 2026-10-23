package tr.ohvps.idempotency.demo;

/**
 * POST operations for which the ÖHVPS idempotency rule is mandatory.
 */
public enum OperationType {
    PAYMENT_ORDER_CONSENT,
    ACCESS_TOKEN,
    PAYMENT_ORDER,
    ACCOUNT_INFORMATION_CONSENT,
    FUTURE_DATED_PAYMENT_ORDER_CONSENT,
    FUTURE_DATED_PAYMENT_ORDER,
    RECURRING_PAYMENT_ORDER_CONSENT,
    RECURRING_PAYMENT_ORDER
}
