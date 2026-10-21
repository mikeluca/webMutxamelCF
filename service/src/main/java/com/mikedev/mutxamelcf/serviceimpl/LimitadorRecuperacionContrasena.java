package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Límites de /api/app/auth/recuperar: una petición por email cada 2 minutos y
 * diez por IP cada hora. Cuentan todas las peticiones, exista o no la cuenta. Vive
 * en memoria (se pierde al reiniciar y no se comparte entre instancias).
 */
@Component
public class LimitadorRecuperacionContrasena {

    static final Duration INTERVALO_EMAIL = Duration.ofMinutes(2);

    static final Duration VENTANA_IP = Duration.ofHours(1);

    static final int MAX_POR_IP = 10;

    // Por encima de este tamaño se purgan las entradas ya vencidas.
    private static final int UMBRAL_PURGA = 5_000;

    private final Clock clock;
    private final Map<String, Instant> ultimaPorEmail = new HashMap<>();
    private final Map<String, Deque<Instant>> porIp = new HashMap<>();

    public LimitadorRecuperacionContrasena(Clock clock) {
        this.clock = clock;
    }

    /**
     * Reserva una petición. Devuelve false (sin consumir nada) si el email o la IP
     * han superado su límite.
     */
    public synchronized boolean permitir(String email, String ip) {

        Instant ahora = clock.instant();

        purgar(ahora);

        String claveEmail = email == null ? "" : email.trim().toLowerCase();
        String claveIp = ip == null ? "" : ip;

        Instant ultima = ultimaPorEmail.get(claveEmail);

        if (ultima != null && Duration.between(ultima, ahora).compareTo(INTERVALO_EMAIL) < 0) {
            return false;
        }

        Deque<Instant> peticiones = porIp.computeIfAbsent(claveIp, k -> new ArrayDeque<>());

        while (!peticiones.isEmpty() && Duration.between(peticiones.peekFirst(), ahora).compareTo(VENTANA_IP) >= 0) {
            peticiones.removeFirst();
        }

        if (peticiones.size() >= MAX_POR_IP) {
            return false;
        }

        peticiones.addLast(ahora);
        ultimaPorEmail.put(claveEmail, ahora);

        return true;
    }

    private void purgar(Instant ahora) {

        if (ultimaPorEmail.size() + porIp.size() < UMBRAL_PURGA) {
            return;
        }

        ultimaPorEmail.values().removeIf(t -> Duration.between(t, ahora).compareTo(INTERVALO_EMAIL) >= 0);
        porIp.values().removeIf(d -> d.isEmpty() || Duration.between(d.peekLast(), ahora).compareTo(VENTANA_IP) >= 0);
    }
}
