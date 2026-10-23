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
 * Kapsam dışı uç noktaların ve 5xx yanıtların saklanmadığını doğrular.
 */
@SpringBootTest(properties = "ohvps.idempotency.paths=/test/kapsamli/**")
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
    void sunucuHatasiSaklanmazAyniIstekYenidenIslenir() throws Exception {
        mockMvc.perform(post("/test/kapsamli/ilki-hatali").header("x-request-id", "r-5xx")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/test/kapsamli/ilki-hatali").header("x-request-id", "r-5xx")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));
    }

    @Test
    void istemciHatasiSaklanirVeAynenDoner() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/test/kapsamli/gecersiz").header("x-request-id", "r-4xx")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(content().string("1"));
        }
    }

    @Test
    void kapsamDisiUcNoktaIdempotencyUygulanmadanIslenir() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/test/kapsam-disi").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }

        assertThat(endpoints.kapsamDisiCagri.get()).isEqualTo(2);
    }

    @RestController
    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpoints {

        private final AtomicInteger ilkiHataliCagri = new AtomicInteger();
        private final AtomicInteger gecersizCagri = new AtomicInteger();
        private final AtomicInteger kapsamDisiCagri = new AtomicInteger();

        void reset() {
            ilkiHataliCagri.set(0);
            gecersizCagri.set(0);
            kapsamDisiCagri.set(0);
        }

        @PostMapping("/test/kapsamli/ilki-hatali")
        ResponseEntity<String> ilkiHatali() {
            int cagri = ilkiHataliCagri.incrementAndGet();
            HttpStatus status = cagri == 1 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
            return ResponseEntity.status(status).body(String.valueOf(cagri));
        }

        @PostMapping("/test/kapsamli/gecersiz")
        ResponseEntity<String> gecersiz() {
            return ResponseEntity.unprocessableContent().body(String.valueOf(gecersizCagri.incrementAndGet()));
        }

        @PostMapping("/test/kapsam-disi")
        ResponseEntity<String> kapsamDisi() {
            return ResponseEntity.ok(String.valueOf(kapsamDisiCagri.incrementAndGet()));
        }
    }
}
