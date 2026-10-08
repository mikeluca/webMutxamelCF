package com.mikedev.mutxamelcf.mvc.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Bloqueo temporal de intentos de login, por contexto + IP + identificador
 * de usuario, y de forma global por contexto + cuenta (sin IP).
 *
 * SEC-06: con solo la clave IP+cuenta, un atacante que rota de IP nunca
 * agota el límite de 5 intentos para la misma cuenta (cada IP nueva
 * empieza de cero). Se añade una segunda clave, solo por cuenta, que sí
 * acumula fallos entre IPs distintas.
 *
 * N-04 (2.ª auditoría): la primera versión de esta clase bloqueaba la
 * cuenta global hasta 2 horas, duplicando la duración en cada bloqueo
 * consecutivo. Como esa clave no depende de la IP ni de conocer la
 * contraseña, cualquiera que supiera el usuario web SUPER o el email de
 * un entrenador podía dejarlo bloqueado horas enviando contraseñas falsas
 * desde cualquier sitio: un bloqueo duro por cuenta es en sí mismo un
 * vector de denegación de servicio. Se sustituye por un retardo
 * creciente pero corto (segundos, no hasta 2h) -- sigue frenando la
 * fuerza bruta sin poder usarse para bloquear indefinidamente a un
 * usuario legítimo. El contexto (web/app-login/app-activar) también
 * separa los contadores: agotar los intentos del login web no debe
 * bloquear la activación de la app para el mismo email.
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

    public static final String CONTEXTO_WEB = "web";
    public static final String CONTEXTO_APP_LOGIN = "app-login";
    public static final String CONTEXTO_APP_ACTIVAR = "app-activar";
    public static final String CONTEXTO_APP_BORRAR_CUENTA = "app-borrar-cuenta";

    private static final int MAX_INTENTOS = 5;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private static final int MAX_INTENTOS_GLOBAL_CUENTA = 15;
    private static final Duration DURACION_BLOQUEO_GLOBAL_INICIAL = Duration.ofSeconds(5);
    private static final Duration DURACION_BLOQUEO_GLOBAL_MAXIMA = Duration.ofSeconds(60);

    /** Entradas sin actividad más antigua que esto se consideran basura. */
    private static final Duration ANTIGUEDAD_MAXIMA_ENTRADA = Duration.ofHours(6);

    private final ConcurrentMap<String, Intentos> intentosPorClave = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Intentos> intentosPorCuenta = new ConcurrentHashMap<>();
    private final PurgaThrottle purgaThrottle = new PurgaThrottle(Duration.ofSeconds(30));

    /**
     * @param contexto uno de {@link #CONTEXTO_WEB}, {@link #CONTEXTO_APP_LOGIN}
     *                 o {@link #CONTEXTO_APP_ACTIVAR}: separa los contadores
     *                 entre el login web, el login de la app y la
     *                 activación, para que agotar los intentos de uno no
     *                 bloquee los demás para el mismo identificador.
     */
    public String clave(String contexto, String ip, String identificador) {
        String contextoNormalizado = contexto == null ? "desconocido" : contexto;
        String ipNormalizada = ip == null ? "desconocida" : ip;
        String idNormalizado = normalizarIdentificador(identificador);
        return contextoNormalizado + "|" + ipNormalizada + "|" + idNormalizado;
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

    /**
     * La clave tiene forma "contexto|ip|identificador"; la cuenta global
     * es "contexto|identificador" (se quita solo el tramo de la IP), para
     * que el límite global tampoco mezcle web/app-login/app-activar entre
     * sí.
     */
    private static String cuentaDeClave(String clave) {
        if (clave == null) {
            return "";
        }
        String[] partes = clave.split("\\|", 3);
        if (partes.length < 3) {
            // Formato inesperado (p.ej. clave construida a mano en tests):
            // se usa tal cual, sin agrupar por cuenta.
            return clave;
        }
        return partes[0] + "|" + partes[2];
    }

    private static String normalizarIdentificador(String identificador) {
        return identificador == null ? "" : identificador.trim().toLowerCase();
    }

    private void purgarObsoletos() {
        // N-08: recorrer los dos mapas enteros en cada petición es O(n);
        // basta con hacerlo periódicamente.
        if (!purgaThrottle.tocaPurgar()) {
            return;
        }
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
