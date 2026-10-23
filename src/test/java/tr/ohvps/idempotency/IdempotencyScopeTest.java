package tr.ohvps.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import tr.ohvps.idempotency.support.TestClockConfig;

/**
 * Verifies that out-of-scope endpoints and 5xx responses are not stored.
 */
@SpringBootTest(properties = "ohvps.idempotency.paths=/test/covered/**")
@AutoConfigureMockMvc
@Import({TestClockConfig.class, IdempotencyScopeTest.TestEndpoints.class})
class IdempotencyScopeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestEndpoints endpoints;

    @BeforeEach
    void setUp() {
        endpoints.reset();
    }

    @Test
    void serverErrorIsNotStoredAndSameRequestIsProcessedAgain() throws Exception {
        mockMvc.perform(post("/test/covered/fails-first").header("x-request-id", "r-5xx")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/test/covered/fails-first").header("x-request-id", "r-5xx")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));
    }

    @Test
    void clientErrorIsStoredAndReplayed() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/test/covered/invalid").header("x-request-id", "r-4xx")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(content().string("1"));
        }
    }

    @Test
    void outOfScopeEndpointIsProcessedWithoutIdempotency() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/test/not-covered").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }

        assertThat(endpoints.notCoveredCalls.get()).isEqualTo(2);
    }

    @RestController
    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpoints {

        private final AtomicInteger failsFirstCalls = new AtomicInteger();
        private final AtomicInteger invalidCalls = new AtomicInteger();
        private final AtomicInteger notCoveredCalls = new AtomicInteger();

        void reset() {
            failsFirstCalls.set(0);
            invalidCalls.set(0);
            notCoveredCalls.set(0);
        }

        @PostMapping("/test/covered/fails-first")
        ResponseEntity<String> failsFirst() {
            int call = failsFirstCalls.incrementAndGet();
            HttpStatus status = call == 1 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
            return ResponseEntity.status(status).body(String.valueOf(call));
        }

        @PostMapping("/test/covered/invalid")
        ResponseEntity<String> invalid() {
            return ResponseEntity.unprocessableContent().body(String.valueOf(invalidCalls.incrementAndGet()));
        }

        @PostMapping("/test/not-covered")
        ResponseEntity<String> notCovered() {
            return ResponseEntity.ok(String.valueOf(notCoveredCalls.incrementAndGet()));
        }
    }
}
