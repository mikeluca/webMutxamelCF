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

    @Test
    void unAtacanteQueRotaDeIpAcabaBloqueadoPorElLimiteGlobalDeCuenta() {
        // SEC-06: la clave IP+cuenta por si sola no detiene a un atacante
        // que cambia de IP en cada tanda de intentos; el limite global por
        // cuenta (sin IP) si acumula los fallos entre IPs distintas.
        LoginRateLimiter limiter = new LoginRateLimiter();
        String cuenta = "victima@example.com";

        for (int ip = 0; ip < 3; ip++) {
            String clave = limiter.clave("10.0.0." + ip, cuenta);
            for (int intento = 0; intento < 4; intento++) {
                limiter.registrarFallo(clave);
            }
        }

        String claveCuartaIp = limiter.clave("10.0.0.99", cuenta);
        limiter.registrarFallo(claveCuartaIp);
        limiter.registrarFallo(claveCuartaIp);
        limiter.registrarFallo(claveCuartaIp);

        assertThat(limiter.estaBloqueado(claveCuartaIp)).isTrue();

        String claveQuintaIp = limiter.clave("10.0.0.100", cuenta);
        assertThat(limiter.estaBloqueado(claveQuintaIp)).isTrue();
    }

    @Test
    void elLimiteGlobalDeCuentaNoAfectaAOtraCuentaDistinta() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int ip = 0; ip < 4; ip++) {
            String clave = limiter.clave("10.0.0." + ip, "victima@example.com");
            for (int intento = 0; intento < 4; intento++) {
                limiter.registrarFallo(clave);
            }
        }

        String claveOtraCuenta = limiter.clave("10.0.0.200", "otra@example.com");
        assertThat(limiter.estaBloqueado(claveOtraCuenta)).isFalse();
    }
}
