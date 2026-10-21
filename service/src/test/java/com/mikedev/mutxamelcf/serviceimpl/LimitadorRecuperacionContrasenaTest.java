package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LimitadorRecuperacionContrasenaTest {

    private static class RelojMovil extends Clock {

        private Instant ahora = Instant.parse("2026-10-25T10:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojMovil reloj = new RelojMovil();
    private final LimitadorRecuperacionContrasena limitador = new LimitadorRecuperacionContrasena(reloj);

    @Test
    void unaPeticionPorEmailCadaDosMinutos() {
        assertThat(limitador.permitir("ana@example.com", "1.1.1.1")).isTrue();
        assertThat(limitador.permitir("ANA@example.com", "1.1.1.1")).isFalse();

        reloj.avanzar(Duration.ofSeconds(119));
        assertThat(limitador.permitir("ana@example.com", "1.1.1.1")).isFalse();

        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(limitador.permitir("ana@example.com", "1.1.1.1")).isTrue();
    }

    @Test
    void otroEmailDesdeLaMismaIpNoQuedaBloqueadoPorElPrimero() {
        assertThat(limitador.permitir("ana@example.com", "1.1.1.1")).isTrue();
        assertThat(limitador.permitir("luis@example.com", "1.1.1.1")).isTrue();
    }

    @Test
    void lasPeticionesPorIpEstanLimitadasCadaHora() {
        for (int i = 0; i < LimitadorRecuperacionContrasena.MAX_POR_IP; i++) {
            assertThat(limitador.permitir("u" + i + "@example.com", "2.2.2.2")).isTrue();
        }

        assertThat(limitador.permitir("extra@example.com", "2.2.2.2")).isFalse();
        // Otra IP no se ve afectada.
        assertThat(limitador.permitir("extra@example.com", "3.3.3.3")).isTrue();

        reloj.avanzar(Duration.ofHours(1));
        assertThat(limitador.permitir("u11@example.com", "2.2.2.2")).isTrue();
    }

    @Test
    void comoMaximoCincoCodigosPorEmailCada24Horas() {
        for (int i = 0; i < 5; i++) {
            assertThat(limitador.permitir("ana@example.com", "1.1.1." + i)).isTrue();
            reloj.avanzar(Duration.ofMinutes(3));
        }

        // Pasados los 2 minutos de cada una, la sexta ya choca con el tope diario.
        assertThat(limitador.permitir("ana@example.com", "1.1.1.9")).isFalse();
        // Otro email no se ve afectado.
        assertThat(limitador.permitir("luis@example.com", "1.1.1.9")).isTrue();

        // A las 24 h de la primera se libera un hueco.
        reloj.avanzar(Duration.ofHours(24));
        assertThat(limitador.permitir("ana@example.com", "1.1.1.9")).isTrue();
    }

    @Test
    void unaPeticionRechazadaPorIpNoConsumeElTurnoDelEmail() {
        for (int i = 0; i < LimitadorRecuperacionContrasena.MAX_POR_IP; i++) {
            limitador.permitir("u" + i + "@example.com", "2.2.2.2");
        }

        assertThat(limitador.permitir("ana@example.com", "2.2.2.2")).isFalse();
        assertThat(limitador.permitir("ana@example.com", "4.4.4.4")).isTrue();
    }
}
