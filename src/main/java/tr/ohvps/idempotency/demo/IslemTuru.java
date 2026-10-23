package tr.ohvps.idempotency.demo;

/**
 * ÖHVPS idempotency kuralının zorunlu olduğu POST işlemleri.
 */
public enum IslemTuru {
    ODEME_EMRI_RIZASI,
    ERISIM_BELIRTECI,
    ODEME_EMRI,
    HESAP_BILGISI_RIZASI,
    ILERI_TARIHLI_ODEME_EMRI_RIZASI,
    ILERI_TARIHLI_ODEME_EMRI,
    DUZENLI_ODEME_EMRI_RIZASI,
    DUZENLI_ODEME_EMRI
}
