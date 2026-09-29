package com.mikedev.mutxamelcf.mvc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    @Test
    void claveNormalizaContextoIpEIdentificador() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        assertThat(limiter.clave(LoginRateLimiter.CONTEXTO_WEB, null, " Ana@Example.com "))
                .isEqualTo("web|desconocida|ana@example.com");
        assertThat(limiter.clave(LoginRateLimiter.CONTEXTO_APP_LOGIN, "127.0.0.1", null))
                .isEqualTo("app-login|127.0.0.1|");
    }

    @Test
    void noEstaBloqueadoParaUnaClaveSinIntentos() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        assertThat(limiter.estaBloqueado("clave-nueva")).isFalse();
    }

    @Test
    void noSeBloqueaConMenosDeCincoFallos() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String clave = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "127.0.0.1", "ana");

        for (int i = 0; i < 4; i++) {
            limiter.registrarFallo(clave);
        }

        assertThat(limiter.estaBloqueado(clave)).isFalse();
    }

    @Test
    void seBloqueaAlQuintoFallo() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String clave = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "127.0.0.1", "ana");

        for (int i = 0; i < 5; i++) {
            limiter.registrarFallo(clave);
        }

        assertThat(limiter.estaBloqueado(clave)).isTrue();
    }

    @Test
    void registrarExitoLimpiaElBloqueo() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String clave = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "127.0.0.1", "ana");

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
            String clave = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "10.0.0." + ip, cuenta);
            for (int intento = 0; intento < 4; intento++) {
                limiter.registrarFallo(clave);
            }
        }

        String claveCuartaIp = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "10.0.0.99", cuenta);
        limiter.registrarFallo(claveCuartaIp);
        limiter.registrarFallo(claveCuartaIp);
        limiter.registrarFallo(claveCuartaIp);

        assertThat(limiter.estaBloqueado(claveCuartaIp)).isTrue();

        String claveQuintaIp = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "10.0.0.100", cuenta);
        assertThat(limiter.estaBloqueado(claveQuintaIp)).isTrue();
    }

    @Test
    void elLimiteGlobalDeCuentaNoAfectaAOtraCuentaDistinta() {
        LoginRateLimiter limiter = new LoginRateLimiter();

        for (int ip = 0; ip < 4; ip++) {
            String clave = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "10.0.0." + ip, "victima@example.com");
            for (int intento = 0; intento < 4; intento++) {
                limiter.registrarFallo(clave);
            }
        }

        String claveOtraCuenta = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "10.0.0.200", "otra@example.com");
        assertThat(limiter.estaBloqueado(claveOtraCuenta)).isFalse();
    }

    @Test
    void elLimiteGlobalDeCuentaNoMezclaWebConAppLoginNiActivacion() {
        // N-04: agotar los intentos del login web para un email no debe
        // bloquear el login de la app ni la activacion para ese mismo
        // email -- son riesgos y flujos distintos.
        LoginRateLimiter limiter = new LoginRateLimiter();
        String email = "entrenador@example.com";

        for (int ip = 0; ip < 4; ip++) {
            String claveWeb = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "10.0.0." + ip, email);
            for (int intento = 0; intento < 4; intento++) {
                limiter.registrarFallo(claveWeb);
            }
        }
        String claveWebQueDispara = limiter.clave(LoginRateLimiter.CONTEXTO_WEB, "10.0.0.99", email);
        limiter.registrarFallo(claveWebQueDispara);
        assertThat(limiter.estaBloqueado(claveWebQueDispara)).isTrue();

        String claveAppLogin = limiter.clave(LoginRateLimiter.CONTEXTO_APP_LOGIN, "10.0.0.1", email);
        String claveAppActivar = limiter.clave(LoginRateLimiter.CONTEXTO_APP_ACTIVAR, "10.0.0.1", email);

        assertThat(limiter.estaBloqueado(claveAppLogin)).isFalse();
        assertThat(limiter.estaBloqueado(claveAppActivar)).isFalse();
    }
}
