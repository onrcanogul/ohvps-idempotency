package tr.ohvps.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import tr.ohvps.idempotency.demo.OperationCounter;
import tr.ohvps.idempotency.demo.OperationType;
import tr.ohvps.idempotency.support.MutableClock;
import tr.ohvps.idempotency.support.TestClockConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class IdempotencyIntegrationTest {

    private static final String PAYMENT_ORDER = "/ohvps/obh/s1.1/odeme-emri";
    private static final String BODY = "{\"amount\":\"100.00\",\"creditor\":\"TR330006100519786457841326\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperationCounter operationCounter;

    @Autowired
    private MutableClock clock;

    private String requestId;

    @BeforeEach
    void setUp() {
        operationCounter.reset();
        requestId = UUID.randomUUID().toString();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "PAYMENT_ORDER_CONSENT, /ohvps/obh/s1.1/odeme-emri-rizasi",
            "ACCESS_TOKEN, /ohvps/gkd/s1.1/erisim-belirteci",
            "PAYMENT_ORDER, /ohvps/obh/s1.1/odeme-emri",
            "ACCOUNT_INFORMATION_CONSENT, /ohvps/hbh/s1.1/hesap-bilgisi-rizasi",
            "FUTURE_DATED_PAYMENT_ORDER_CONSENT, /ohvps/obh/s1.1/ileri-tarihli-odeme-emri-rizasi",
            "FUTURE_DATED_PAYMENT_ORDER, /ohvps/obh/s1.1/ileri-tarihli-odeme-emri",
            "RECURRING_PAYMENT_ORDER_CONSENT, /ohvps/obh/s1.1/duzenli-odeme-emri-rizasi",
            "RECURRING_PAYMENT_ORDER, /ohvps/obh/s1.1/duzenli-odeme-emri"
    })
    void everyCoveredPostReplaysStoredResponseWithoutReprocessing(OperationType type, String path) throws Exception {
        MvcResult first = send(path, requestId, BODY);
        MvcResult repeated = send(path, requestId, BODY);

        assertThat(repeated.getResponse().getStatus()).isEqualTo(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(repeated.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(repeated.getResponse().getHeader("x-request-id")).isEqualTo(requestId);
        assertThat(first.getResponse().getHeader("X-Idempotency-Replayed")).isNull();
        assertThat(repeated.getResponse().getHeader("X-Idempotency-Replayed")).isEqualTo("true");
        assertThat(operationCounter.get(type)).isEqualTo(1);
    }

    @Test
    void sameRequestIdWithDifferentBodyIsNewRequest() throws Exception {
        String first = operationId(send(PAYMENT_ORDER, requestId, BODY));
        String second = operationId(send(PAYMENT_ORDER, requestId, "{\"amount\":\"200.00\"}"));

        assertThat(second).isNotEqualTo(first);
        assertThat(operationCounter.get(OperationType.PAYMENT_ORDER)).isEqualTo(2);
    }

    @Test
    void differentRequestIdWithSameBodyIsNewRequest() throws Exception {
        String first = operationId(send(PAYMENT_ORDER, requestId, BODY));
        String second = operationId(send(PAYMENT_ORDER, UUID.randomUUID().toString(), BODY));

        assertThat(second).isNotEqualTo(first);
        assertThat(operationCounter.get(OperationType.PAYMENT_ORDER)).isEqualTo(2);
    }

    @Test
    void sameRequestWithinFiveMinutesGetsStoredResponse() throws Exception {
        String first = operationId(send(PAYMENT_ORDER, requestId, BODY));

        clock.advance(Duration.ofMinutes(5).minusSeconds(1));

        assertThat(operationId(send(PAYMENT_ORDER, requestId, BODY))).isEqualTo(first);
        assertThat(operationCounter.get(OperationType.PAYMENT_ORDER)).isEqualTo(1);
    }

    @Test
    void sameRequestAfterFiveMinutesIsNewRequest() throws Exception {
        String first = operationId(send(PAYMENT_ORDER, requestId, BODY));

        clock.advance(Duration.ofMinutes(5));

        MvcResult fresh = send(PAYMENT_ORDER, requestId, BODY);
        assertThat(operationId(fresh)).isNotEqualTo(first);
        assertThat(fresh.getResponse().getHeader("X-Idempotency-Replayed")).isNull();
        assertThat(operationCounter.get(OperationType.PAYMENT_ORDER)).isEqualTo(2);
    }

    @Test
    void requestWithoutRequestIdIsRejectedAndNotProcessed() throws Exception {
        mockMvc.perform(post(PAYMENT_ORDER).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.errorCode").value("TR.OHVPS.Idempotency.MissingRequestId"));

        assertThat(operationCounter.get(OperationType.PAYMENT_ORDER)).isZero();
    }

    @Test
    void concurrentDuplicatesRunBusinessLogicOnce() throws Exception {
        int duplicates = 16;
        ExecutorService executor = Executors.newFixedThreadPool(duplicates);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (int i = 0; i < duplicates; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return send(PAYMENT_ORDER, requestId, BODY);
                }));
            }
            start.countDown();

            Set<String> operationIds = new HashSet<>();
            for (Future<MvcResult> result : results) {
                operationIds.add(operationId(result.get()));
            }

            assertThat(operationIds).hasSize(1);
            assertThat(operationCounter.get(OperationType.PAYMENT_ORDER)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void storedResponseIsReplayedWithItsHeaders() throws Exception {
        send(PAYMENT_ORDER, requestId, BODY);

        mockMvc.perform(post(PAYMENT_ORDER)
                        .header("x-request-id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header().string("x-request-id", requestId));
    }

    private MvcResult send(String path, String id, String body) throws Exception {
        return mockMvc.perform(post(path)
                        .header("x-request-id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private static String operationId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.operationId");
    }
}
