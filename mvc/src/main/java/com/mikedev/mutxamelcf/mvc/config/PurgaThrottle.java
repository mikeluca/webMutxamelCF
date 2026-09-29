package com.mikedev.mutxamelcf.mvc.config;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * N-08 (2.ª auditoría): {@code LoginRateLimiter}/{@code PublicFormRateLimiter}
 * recorrían todo su mapa (O(n)) en cada petición para purgar entradas
 * caducadas. Un ataque con miles de identificadores distintos (emails,
 * IPs) hace crecer ese mapa sin límite mientras las entradas siguen sin
 * caducar, así que cada petición posterior es más lenta que la anterior.
 * Limitando la purga a como mucho una vez cada {@code intervaloMinimo},
 * el coste de recorrer el mapa deja de pagarlo cada petición.
 */
final class PurgaThrottle {

    private final Duration intervaloMinimo;
    private final AtomicReference<Instant> ultimaPurga = new AtomicReference<>(Instant.MIN);

    PurgaThrottle(Duration intervaloMinimo) {
        this.intervaloMinimo = intervaloMinimo;
    }

    /**
     * @return true como mucho una vez cada {@code intervaloMinimo}; el
     *         llamante solo debe purgar cuando esto devuelva true.
     */
    boolean tocaPurgar() {
        Instant ahora = Instant.now();
        Instant anterior = ultimaPurga.get();
        if (ahora.isBefore(anterior.plus(intervaloMinimo))) {
            return false;
        }
        return ultimaPurga.compareAndSet(anterior, ahora);
    }
}
