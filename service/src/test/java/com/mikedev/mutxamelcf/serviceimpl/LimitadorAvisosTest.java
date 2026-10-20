package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class LimitadorAvisosTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final class RelojManual extends Clock {
        private Instant ahora = LocalDateTime.of(2026, 10, 21, 10, 0).atZone(MADRID).toInstant();

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return MADRID;
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

    private final RelojManual reloj = new RelojManual();
    private final LimitadorAvisos limitador = new LimitadorAvisos(reloj, Duration.ofMinutes(10));

    @Test
    void sinAvisoPrevioSiempreDejaPasar() {
        assertThat(limitador.permitir("30:100", "JUSTIFICADA")).isTrue();
    }

    @Test
    void consultarNoRegistraNada() {
        assertThat(limitador.permitir("30:100", "JUSTIFICADA")).isTrue();
        assertThat(limitador.permitir("30:100", "JUSTIFICADA")).isTrue();
        assertThat(limitador.ultimoTipo("30:100")).isNull();
    }

    @Test
    void elMismoTipoSeDescartaAntesDeLosDiezMinutos() {
        limitador.registrar("30:100", "JUSTIFICADA");

        reloj.avanzar(Duration.ofMinutes(9).plusSeconds(59));

        assertThat(limitador.permitir("30:100", "JUSTIFICADA")).isFalse();

        reloj.avanzar(Duration.ofSeconds(1));

        assertThat(limitador.permitir("30:100", "JUSTIFICADA")).isTrue();
    }

    @Test
    void otroTipoSiempreDejaPasarAunqueSeaAlInstante() {
        limitador.registrar("30:100", "JUSTIFICADA");

        assertThat(limitador.permitir("30:100", "RETIRADA")).isTrue();
        assertThat(limitador.permitir("30:100", "QUITADO")).isTrue();
        assertThat(limitador.permitir("30:100", "CONVOCADO_SUSTITUIR")).isTrue();
    }

    @Test
    void soloCuentaElUltimoAvisoRegistrado() {
        limitador.registrar("30:100", "JUSTIFICADA");
        limitador.registrar("30:100", "RETIRADA");

        // El anterior ya es RETIRADA: justificar de nuevo es un cambio de estado.
        assertThat(limitador.permitir("30:100", "JUSTIFICADA")).isTrue();
        assertThat(limitador.permitir("30:100", "RETIRADA")).isFalse();
        assertThat(limitador.ultimoTipo("30:100")).isEqualTo("RETIRADA");
    }

    @Test
    void cadaPartidoYJugadorTieneSuPropiaCuenta() {
        limitador.registrar("30:100", "JUSTIFICADA");

        assertThat(limitador.permitir("30:101", "JUSTIFICADA")).isTrue();
        assertThat(limitador.permitir("31:100", "JUSTIFICADA")).isTrue();
    }

    @Test
    void unAvisoDescartadoNoReiniciaLaCuenta() {
        limitador.registrar("30:100", "JUSTIFICADA");

        reloj.avanzar(Duration.ofMinutes(6));

        assertThat(limitador.permitir("30:100", "JUSTIFICADA")).isFalse();   // no se registra

        reloj.avanzar(Duration.ofMinutes(4));

        assertThat(limitador.permitir("30:100", "JUSTIFICADA")).isTrue();    // 10 min desde el primero
    }
}
