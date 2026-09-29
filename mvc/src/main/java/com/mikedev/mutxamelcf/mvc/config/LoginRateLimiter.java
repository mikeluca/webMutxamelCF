package com.mikedev.mutxamelcf.mvc.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bloqueo temporal de intentos de login, por IP + identificador de usuario,
 * y de forma global por cuenta (sin IP).
 *
 * SEC-06: con solo la clave IP+cuenta, un atacante que rota de IP nunca
 * agota el límite de 5 intentos para la misma cuenta (cada IP nueva
 * empieza de cero). Se añade una segunda clave, solo por cuenta, con un
 * umbral más alto y bloqueo progresivo (cada bloqueo consecutivo dobla la
 * duración del anterior, hasta un máximo), que sí acumula fallos entre
 * IPs distintas.
 *
 * Nota: si en producción hay un proxy inverso delante y todas las
 * peticiones comparten IP (server.forward-headers-strategy sin configurar
 * o proxy que no reenvía la IP real), la clave IP+cuenta pierde su
 * utilidad práctica -- eso depende de la configuración del proxy y no se
 * puede cerrar solo desde el código.
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

    private static final int MAX_INTENTOS_GLOBAL_CUENTA = 15;
    private static final Duration DURACION_BLOQUEO_GLOBAL_INICIAL = Duration.ofMinutes(15);
    private static final Duration DURACION_BLOQUEO_GLOBAL_MAXIMA = Duration.ofHours(2);

    /** Entradas sin actividad más antigua que esto se consideran basura. */
    private static final Duration ANTIGUEDAD_MAXIMA_ENTRADA = Duration.ofHours(6);

    private final ConcurrentMap<String, Intentos> intentosPorClave = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Intentos> intentosPorCuenta = new ConcurrentHashMap<>();

    public String clave(String ip, String identificador) {
        String ipNormalizada = ip == null ? "desconocida" : ip;
        String idNormalizado = normalizarIdentificador(identificador);
        return ipNormalizada + "|" + idNormalizado;
    }

    public boolean estaBloqueado(String clave) {
        purgarObsoletos();
        return estaBloqueada(intentosPorClave.get(clave))
                || estaBloqueada(intentosPorCuenta.get(cuentaDeClave(clave)));
    }

    public void registrarFallo(String clave) {
        intentosPorClave.compute(clave, (k, actual) -> {
            Intentos intentos = actual != null ? actual : new Intentos();
            intentos.marcarActividad();
            intentos.fallos++;
            if (intentos.fallos >= MAX_INTENTOS) {
                intentos.bloqueadoHasta = Instant.now().plus(DURACION_BLOQUEO);
            }
            return intentos;
        });

        intentosPorCuenta.compute(cuentaDeClave(clave), (k, actual) -> {
            Intentos intentos = actual != null ? actual : new Intentos();
            intentos.marcarActividad();
            intentos.fallos++;
            if (intentos.fallos >= MAX_INTENTOS_GLOBAL_CUENTA) {
                Duration duracion = intentos.bloqueosConsecutivos == 0
                        ? DURACION_BLOQUEO_GLOBAL_INICIAL
                        : DURACION_BLOQUEO_GLOBAL_INICIAL.multipliedBy(1L << Math.min(intentos.bloqueosConsecutivos, 4));
                if (duracion.compareTo(DURACION_BLOQUEO_GLOBAL_MAXIMA) > 0) {
                    duracion = DURACION_BLOQUEO_GLOBAL_MAXIMA;
                }
                intentos.bloqueadoHasta = Instant.now().plus(duracion);
                intentos.bloqueosConsecutivos++;
                intentos.fallos = 0;
            }
            return intentos;
        });
    }

    public void registrarExito(String clave) {
        intentosPorClave.remove(clave);
        intentosPorCuenta.remove(cuentaDeClave(clave));
    }

    private static boolean estaBloqueada(Intentos intentos) {
        return intentos != null
                && intentos.bloqueadoHasta != null
                && Instant.now().isBefore(intentos.bloqueadoHasta);
    }

    /** La clave tiene forma "ip|identificador"; la cuenta es la parte tras el separador. */
    private static String cuentaDeClave(String clave) {
        if (clave == null) {
            return "";
        }
        int separador = clave.indexOf('|');
        return separador >= 0 ? clave.substring(separador + 1) : clave;
    }

    private static String normalizarIdentificador(String identificador) {
        return identificador == null ? "" : identificador.trim().toLowerCase();
    }

    private void purgarObsoletos() {
        Instant limite = Instant.now().minus(ANTIGUEDAD_MAXIMA_ENTRADA);
        intentosPorClave.values().removeIf(intentos -> intentos.ultimaActividad.isBefore(limite));
        intentosPorCuenta.values().removeIf(intentos -> intentos.ultimaActividad.isBefore(limite));
    }

    private static class Intentos {
        int fallos;
        int bloqueosConsecutivos;
        Instant bloqueadoHasta;
        Instant ultimaActividad = Instant.now();

        void marcarActividad() {
            ultimaActividad = Instant.now();
        }
    }
}
