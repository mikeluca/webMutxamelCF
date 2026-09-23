package com.mikedev.mutxamelcf.mvc.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bloqueo temporal de intentos de login, por IP + identificador de usuario.
 *
 * En memoria y por instancia: válido para el despliegue actual
 * (un único contenedor). Si algún día se despliega en varias
 * instancias, esto habría que moverlo a un almacén compartido
 * (Redis, base de datos...).
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_INTENTOS = 5;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private final ConcurrentMap<String, Intentos> intentosPorClave = new ConcurrentHashMap<>();

    public String clave(String ip, String identificador) {
        String ipNormalizada = ip == null ? "desconocida" : ip;
        String idNormalizado = identificador == null
                ? ""
                : identificador.trim().toLowerCase();
        return ipNormalizada + "|" + idNormalizado;
    }

    public boolean estaBloqueado(String clave) {
        Intentos intentos = intentosPorClave.get(clave);
        return intentos != null
                && intentos.bloqueadoHasta != null
                && Instant.now().isBefore(intentos.bloqueadoHasta);
    }

    public void registrarFallo(String clave) {
        intentosPorClave.compute(clave, (k, actual) -> {
            Intentos intentos = actual != null ? actual : new Intentos();
            intentos.fallos++;
            if (intentos.fallos >= MAX_INTENTOS) {
                intentos.bloqueadoHasta = Instant.now().plus(DURACION_BLOQUEO);
            }
            return intentos;
        });
    }

    public void registrarExito(String clave) {
        intentosPorClave.remove(clave);
    }

    private static class Intentos {
        int fallos;
        Instant bloqueadoHasta;
    }
}
