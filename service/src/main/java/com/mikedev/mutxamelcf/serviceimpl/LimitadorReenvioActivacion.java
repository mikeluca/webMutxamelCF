package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/**
 * Como máximo un reenvío de activación por usuario cada 10 minutos. Vive en
 * memoria (no hay columna donde guardarlo): se pierde al reiniciar y no se
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

    /** Cuánto falta para poder reenviar a ese usuario; {@link Duration#ZERO} si ya se puede. */
    public Duration esperaRestante(int usuarioAppId) {

        Instant ultimo = ultimoReenvio.get(usuarioAppId);

        if (ultimo == null) {
            return Duration.ZERO;
        }

        Duration restante = INTERVALO.minus(Duration.between(ultimo, clock.instant()));

        return restante.isNegative() ? Duration.ZERO : restante;
    }

    /** Anota un reenvío hecho ahora. */
    public void registrar(int usuarioAppId) {
        ultimoReenvio.put(usuarioAppId, clock.instant());
    }
}
