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

import tr.ohvps.idempotency.demo.IslemSayaci;
import tr.ohvps.idempotency.demo.IslemTuru;
import tr.ohvps.idempotency.support.MutableClock;
import tr.ohvps.idempotency.support.TestClockConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class IdempotencyIntegrationTest {

    private static final String ODEME_EMRI = "/ohvps/obh/s1.1/odeme-emri";
    private static final String GOVDE = "{\"tutar\":\"100.00\",\"alici\":\"TR330006100519786457841326\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IslemSayaci islemSayaci;

    @Autowired
    private MutableClock clock;

    private String requestId;

    @BeforeEach
    void setUp() {
        islemSayaci.sifirla();
        requestId = UUID.randomUUID().toString();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "ODEME_EMRI_RIZASI, /ohvps/obh/s1.1/odeme-emri-rizasi",
            "ERISIM_BELIRTECI, /ohvps/gkd/s1.1/erisim-belirteci",
            "ODEME_EMRI, /ohvps/obh/s1.1/odeme-emri",
            "HESAP_BILGISI_RIZASI, /ohvps/hbh/s1.1/hesap-bilgisi-rizasi",
            "ILERI_TARIHLI_ODEME_EMRI_RIZASI, /ohvps/obh/s1.1/ileri-tarihli-odeme-emri-rizasi",
            "ILERI_TARIHLI_ODEME_EMRI, /ohvps/obh/s1.1/ileri-tarihli-odeme-emri",
            "DUZENLI_ODEME_EMRI_RIZASI, /ohvps/obh/s1.1/duzenli-odeme-emri-rizasi",
            "DUZENLI_ODEME_EMRI, /ohvps/obh/s1.1/duzenli-odeme-emri"
    })
    void kapsamdakiHerPostAyniIstegiYenidenIslemedenKayitliYanitiDoner(IslemTuru tur, String path) throws Exception {
        MvcResult ilk = gonder(path, requestId, GOVDE);
        MvcResult tekrar = gonder(path, requestId, GOVDE);

        assertThat(tekrar.getResponse().getStatus()).isEqualTo(ilk.getResponse().getStatus()).isEqualTo(201);
        assertThat(tekrar.getResponse().getContentAsString()).isEqualTo(ilk.getResponse().getContentAsString());
        assertThat(tekrar.getResponse().getHeader("x-request-id")).isEqualTo(requestId);
        assertThat(ilk.getResponse().getHeader("X-Idempotency-Replayed")).isNull();
        assertThat(tekrar.getResponse().getHeader("X-Idempotency-Replayed")).isEqualTo("true");
        assertThat(islemSayaci.deger(tur)).isEqualTo(1);
    }

    @Test
    void ayniIstekNumarasiFarkliGovdeYeniIstekSayilir() throws Exception {
        String ilk = islemNo(gonder(ODEME_EMRI, requestId, GOVDE));
        String ikinci = islemNo(gonder(ODEME_EMRI, requestId, "{\"tutar\":\"200.00\"}"));

        assertThat(ikinci).isNotEqualTo(ilk);
        assertThat(islemSayaci.deger(IslemTuru.ODEME_EMRI)).isEqualTo(2);
    }

    @Test
    void farkliIstekNumarasiAyniGovdeYeniIstekSayilir() throws Exception {
        String ilk = islemNo(gonder(ODEME_EMRI, requestId, GOVDE));
        String ikinci = islemNo(gonder(ODEME_EMRI, UUID.randomUUID().toString(), GOVDE));

        assertThat(ikinci).isNotEqualTo(ilk);
        assertThat(islemSayaci.deger(IslemTuru.ODEME_EMRI)).isEqualTo(2);
    }

    @Test
    void besDakikaDolmadanAyniIstekKayitliYanitiAlir() throws Exception {
        String ilk = islemNo(gonder(ODEME_EMRI, requestId, GOVDE));

        clock.advance(Duration.ofMinutes(5).minusSeconds(1));

        assertThat(islemNo(gonder(ODEME_EMRI, requestId, GOVDE))).isEqualTo(ilk);
        assertThat(islemSayaci.deger(IslemTuru.ODEME_EMRI)).isEqualTo(1);
    }

    @Test
    void besDakikaSonraAyniIstekYeniIstekSayilir() throws Exception {
        String ilk = islemNo(gonder(ODEME_EMRI, requestId, GOVDE));

        clock.advance(Duration.ofMinutes(5));

        MvcResult yeni = gonder(ODEME_EMRI, requestId, GOVDE);
        assertThat(islemNo(yeni)).isNotEqualTo(ilk);
        assertThat(yeni.getResponse().getHeader("X-Idempotency-Replayed")).isNull();
        assertThat(islemSayaci.deger(IslemTuru.ODEME_EMRI)).isEqualTo(2);
    }

    @Test
    void istekNumarasiOlmayanIstekReddedilirVeIslenmez() throws Exception {
        mockMvc.perform(post(ODEME_EMRI).contentType(MediaType.APPLICATION_JSON).content(GOVDE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpCode").value(400))
                .andExpect(jsonPath("$.errorCode").value("TR.OHVPS.Idempotency.MissingRequestId"));

        assertThat(islemSayaci.deger(IslemTuru.ODEME_EMRI)).isZero();
    }

    @Test
    void eszamanliAyniIstekleriIsMantigiYalnizcaBirKezCalisir() throws Exception {
        int kopyaSayisi = 16;
        ExecutorService executor = Executors.newFixedThreadPool(kopyaSayisi);
        CountDownLatch basla = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> sonuclar = new ArrayList<>();
            for (int i = 0; i < kopyaSayisi; i++) {
                sonuclar.add(executor.submit(() -> {
                    basla.await();
                    return gonder(ODEME_EMRI, requestId, GOVDE);
                }));
            }
            basla.countDown();

            Set<String> islemNolari = new HashSet<>();
            for (Future<MvcResult> sonuc : sonuclar) {
                islemNolari.add(islemNo(sonuc.get()));
            }

            assertThat(islemNolari).hasSize(1);
            assertThat(islemSayaci.deger(IslemTuru.ODEME_EMRI)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void kayitliYanitBasliklariylaBirlikteDoner() throws Exception {
        gonder(ODEME_EMRI, requestId, GOVDE);

        mockMvc.perform(post(ODEME_EMRI)
                        .header("x-request-id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GOVDE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header().string("x-request-id", requestId));
    }

    private MvcResult gonder(String path, String id, String govde) throws Exception {
        return mockMvc.perform(post(path)
                        .header("x-request-id", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(govde))
                .andReturn();
    }

    private static String islemNo(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.islemNo");
    }
}
