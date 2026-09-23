package com.mikedev.mutxamelcf.mvc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    @Test
    void claveNormalizaIpNulaEIdentificador() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        assertThat(limiter.clave(null, " Ana@Example.com ")).isEqualTo("desconocida|ana@example.com");
        assertThat(limiter.clave("127.0.0.1", null)).isEqualTo("127.0.0.1|");
    }

    @Test
    void noEstaBloqueadoParaUnaClaveSinIntentos() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        assertThat(limiter.estaBloqueado("clave-nueva")).isFalse();
    }

    @Test
    void noSeBloqueaConMenosDeCincoFallos() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String clave = "127.0.0.1|ana";

        for (int i = 0; i < 4; i++) {
            limiter.registrarFallo(clave);
        }

        assertThat(limiter.estaBloqueado(clave)).isFalse();
    }

    @Test
    void seBloqueaAlQuintoFallo() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String clave = "127.0.0.1|ana";

        for (int i = 0; i < 5; i++) {
            limiter.registrarFallo(clave);
        }

        assertThat(limiter.estaBloqueado(clave)).isTrue();
    }

    @Test
    void registrarExitoLimpiaElBloqueo() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String clave = "127.0.0.1|ana";

        for (int i = 0; i < 5; i++) {
            limiter.registrarFallo(clave);
        }
        assertThat(limiter.estaBloqueado(clave)).isTrue();

        limiter.registrarExito(clave);

        assertThat(limiter.estaBloqueado(clave)).isFalse();
    }
}
