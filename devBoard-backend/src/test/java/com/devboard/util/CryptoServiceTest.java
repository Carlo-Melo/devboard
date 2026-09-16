package com.devboard.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        cryptoService = new CryptoService(Base64.getEncoder().encodeToString(key));
    }

    @Test
    void encryptDecrypt_deveRetornarValorOriginal_emRoundTrip() {
        String original = "github-access-token-example";

        String encrypted = cryptoService.encrypt(original);
        String decrypted = cryptoService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encrypt_deveGerarCiphertextsDiferentes_paraOMesmoTexto() {
        String original = "mesmo-valor";

        String first = cryptoService.encrypt(original);
        String second = cryptoService.encrypt(original);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void encrypt_nuncaDeveExporOTextoOriginalEmClaro() {
        String original = "token-nao-pode-vazar";

        String encrypted = cryptoService.encrypt(original);

        assertThat(encrypted).doesNotContain(original);
    }

    @Test
    void decrypt_deveLancarExcecao_quandoValorCorrompido() {
        assertThatThrownBy(() -> cryptoService.decrypt("valor-invalido-nao-base64-ou-cifrado"))
                .isInstanceOf(IllegalStateException.class);
    }
}
