package tr.ohvps.idempotency.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Counts how many times the business logic of each operation type actually ran.
 * Replayed responses do not increment the counter, which is what proves idempotency.
 */
@Component
public class OperationCounter {

    private final Map<OperationType, AtomicInteger> counters = new ConcurrentHashMap<>();

    public int increment(OperationType type) {
        return counters.computeIfAbsent(type, t -> new AtomicInteger()).incrementAndGet();
    }

    public int get(OperationType type) {
        AtomicInteger counter = counters.get(type);
        return counter == null ? 0 : counter.get();
    }

    public void reset() {
        counters.clear();
    }
}
