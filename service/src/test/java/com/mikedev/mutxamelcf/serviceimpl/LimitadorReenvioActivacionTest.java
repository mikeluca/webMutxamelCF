package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LimitadorReenvioActivacionTest {

    private static final Instant INICIO = Instant.parse("2026-10-21T10:00:00Z");

    @Test
    void permiteElPrimerReenvioYBloqueaDiezMinutos() {
        MutableClock reloj = new MutableClock(INICIO);
        LimitadorReenvioActivacion limitador = new LimitadorReenvioActivacion(reloj);

        assertThat(limitador.esperaRestante(7)).isEqualTo(Duration.ZERO);

        limitador.registrar(7);

        assertThat(limitador.esperaRestante(7)).isEqualTo(Duration.ofMinutes(10));

        reloj.avanzar(Duration.ofMinutes(4));
        assertThat(limitador.esperaRestante(7)).isEqualTo(Duration.ofMinutes(6));

        reloj.avanzar(Duration.ofMinutes(6));
        assertThat(limitador.esperaRestante(7)).isEqualTo(Duration.ZERO);
    }

    @Test
    void cadaUsuarioTieneSuPropioLimite() {
        LimitadorReenvioActivacion limitador = new LimitadorReenvioActivacion(new MutableClock(INICIO));

        limitador.registrar(7);

        assertThat(limitador.esperaRestante(8)).isEqualTo(Duration.ZERO);
    }

    private static final class MutableClock extends Clock {

        private Instant ahora;

        MutableClock(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
