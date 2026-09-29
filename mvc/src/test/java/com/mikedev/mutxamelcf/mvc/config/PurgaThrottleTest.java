package com.mikedev.mutxamelcf.mvc.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurgaThrottleTest {

    @Test
    void permitePurgarLaPrimeraVez() {
        PurgaThrottle throttle = new PurgaThrottle(Duration.ofMinutes(1));

        assertThat(throttle.tocaPurgar()).isTrue();
    }

    @Test
    void noPermitePurgarOtraVezDentroDelIntervalo() {
        // N-08: sin este freno, un ataque con miles de identificadores
        // distintos hace que cada peticion recorra el mapa entero.
        PurgaThrottle throttle = new PurgaThrottle(Duration.ofMinutes(1));

        assertThat(throttle.tocaPurgar()).isTrue();
        assertThat(throttle.tocaPurgar()).isFalse();
        assertThat(throttle.tocaPurgar()).isFalse();
    }

    @Test
    void permitePurgarDeNuevoPasadoElIntervalo() {
        PurgaThrottle throttle = new PurgaThrottle(Duration.ofMillis(1));

        assertThat(throttle.tocaPurgar()).isTrue();

        await(5);

        assertThat(throttle.tocaPurgar()).isTrue();
    }

    private static void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
