package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Como máximo un reenvío de activación por usuario cada 10 minutos, tanto desde la
 * app como desde el panel web. La marca se pone ANTES de enviar (de forma atómica,
 * para que dos peticiones a la vez no pasen las dos) y se quita si el envío falla.
 * Vive en memoria (no hay columna donde guardarlo): se pierde al reiniciar y no se
 * comparte entre instancias, lo que basta para frenar pulsaciones repetidas.
 */
@Component
public class LimitadorReenvioActivacion {

    static final Duration INTERVALO = Duration.ofMinutes(10);

    private final Clock clock;
    private final ConcurrentMap<Integer, Instant> ultimoReenvio = new ConcurrentHashMap<>();

    public LimitadorReenvioActivacion(Clock clock) {
        this.clock = clock;
    }

    /**
     * Intenta reservar el reenvío a ese usuario. Devuelve {@link Duration#ZERO} si
     * se ha reservado (hay que enviar y, si falla, llamar a {@link #liberar(int)});
     * si no, cuánto falta para poder volver a reenviar.
     */
    public Duration reservar(int usuarioAppId) {

        AtomicReference<Duration> espera = new AtomicReference<>(Duration.ZERO);
        Instant ahora = clock.instant();

        ultimoReenvio.compute(usuarioAppId, (id, ultimo) -> {

            if (ultimo != null) {

                Duration restante = INTERVALO.minus(Duration.between(ultimo, ahora));

                if (!restante.isNegative() && !restante.isZero()) {
                    espera.set(restante);
                    return ultimo;
                }
            }

            return ahora;
        });

        return espera.get();
    }

    /** Quita la marca (el envío falló: se puede reintentar al momento). */
    public void liberar(int usuarioAppId) {
        ultimoReenvio.remove(usuarioAppId);
    }
}
