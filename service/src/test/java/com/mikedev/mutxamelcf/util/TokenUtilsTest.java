package com.mikedev.mutxamelcf.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUtilsTest {

    @Test
    void generarTokenDevuelveValoresDistintosYNoVacios() {
        String token1 = TokenUtils.generarToken();
        String token2 = TokenUtils.generarToken();

        assertThat(token1).isNotBlank();
        assertThat(token2).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void generarCodigoActivacionTieneSeisDigitos() {
        for (int i = 0; i < 50; i++) {
            String codigo = TokenUtils.generarCodigoActivacion();
            assertThat(codigo).hasSize(6);
            assertThat(codigo).matches("\\d{6}");
        }
    }

    @Test
    void hashTokenEsDeterministaYProduceHexDe64Caracteres() {
        String hash1 = TokenUtils.hashToken("mi-token");
        String hash2 = TokenUtils.hashToken("mi-token");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
        assertThat(hash1).matches("[0-9a-f]{64}");
    }

    @Test
    void hashTokenProduceElValorSha256Esperado() {
        // Vector de prueba conocido: SHA-256("abc")
        String hash = TokenUtils.hashToken("abc");

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashTokenDiferenciaEntradasDistintas() {
        assertThat(TokenUtils.hashToken("uno")).isNotEqualTo(TokenUtils.hashToken("dos"));
    }
}
