package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class LimitadorReenvioActivacionTest {

    private static final Instant INICIO = Instant.parse("2026-10-21T10:00:00Z");

    @Test
    void permiteElPrimerReenvioYBloqueaDiezMinutos() {
        MutableClock reloj = new MutableClock(INICIO);
        LimitadorReenvioActivacion limitador = new LimitadorReenvioActivacion(reloj);

        assertThat(limitador.reservar(7)).isEqualTo(Duration.ZERO);

        // Reservado: el siguiente intento espera los 10 minutos completos.
        assertThat(limitador.reservar(7)).isEqualTo(Duration.ofMinutes(10));

        reloj.avanzar(Duration.ofMinutes(4));
        assertThat(limitador.reservar(7)).isEqualTo(Duration.ofMinutes(6));

        reloj.avanzar(Duration.ofMinutes(6));
        assertThat(limitador.reservar(7)).isEqualTo(Duration.ZERO);
    }

    @Test
    void siElEnvioFallaSeLiberaLaMarcaYSePuedeReintentarAlMomento() {
        LimitadorReenvioActivacion limitador = new LimitadorReenvioActivacion(new MutableClock(INICIO));

        assertThat(limitador.reservar(7)).isEqualTo(Duration.ZERO);

        limitador.liberar(7);

        assertThat(limitador.reservar(7)).isEqualTo(Duration.ZERO);
    }

    @Test
    void cadaUsuarioTieneSuPropioLimite() {
        LimitadorReenvioActivacion limitador = new LimitadorReenvioActivacion(new MutableClock(INICIO));

        limitador.reservar(7);

        assertThat(limitador.reservar(8)).isEqualTo(Duration.ZERO);
    }

    @Test
    void conPeticionesSimultaneasSoloUnaPasa() throws Exception {
        LimitadorReenvioActivacion limitador = new LimitadorReenvioActivacion(new MutableClock(INICIO));

        int hilos = 16;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return limitador.reservar(7).isZero();
            }));
        }

        salida.countDown();

        int pasan = 0;

        for (Future<Boolean> resultado : resultados) {
            if (resultado.get()) {
                pasan++;
            }
        }

        pool.shutdown();

        assertThat(pasan).isEqualTo(1);
    }

    private static final class MutableClock extends Clock {

        private volatile Instant ahora;

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
