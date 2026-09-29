package com.mikedev.mutxamelcf.mvc.config;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SEC-08: límite de envíos por IP para los formularios públicos que
 * disparan un email desde la cuenta SMTP del club (pedido de tienda,
 * contacto), sin sesión ni CSRF de por medio. Sin este límite, un script
 * puede repetir la petición sin parar y quemar la cuota o la reputación
 * del dominio en IONOS.
 *
 * Igual que LoginRateLimiter: en memoria y por instancia, válido para el
 * despliegue actual de un único contenedor. Si en producción hay un
 * proxy inverso que no reenvía la IP real del cliente, este límite pierde
 * su utilidad práctica (todas las peticiones comparten IP) -- eso
 * depende de la configuración del proxy, no de este código.
 */
@Component
public class PublicFormRateLimiter {

    private static final int MAX_ENVIOS = 5;
    private static final Duration VENTANA = Duration.ofMinutes(10);
    private static final Duration ANTIGUEDAD_MAXIMA_ENTRADA = Duration.ofHours(2);

    private final ConcurrentMap<String, Ventana> enviosPorIp = new ConcurrentHashMap<>();

    /**
     * @return true si la petición puede continuar; false si se ha
     *         superado el límite de envíos para esa IP en la ventana
     *         actual (y no cuenta como un envío más).
     */
    public boolean permitir(String ip) {
        purgarObsoletos();
        String clave = ip == null ? "desconocida" : ip;

        Ventana ventana = enviosPorIp.compute(clave, (k, actual) -> {
            Instant ahora = Instant.now();
            if (actual == null || actual.inicio.plus(VENTANA).isBefore(ahora)) {
                return new Ventana(ahora);
            }
            actual.envios++;
            return actual;
        });

        return ventana.envios <= MAX_ENVIOS;
    }

    private void purgarObsoletos() {
        Instant limite = Instant.now().minus(ANTIGUEDAD_MAXIMA_ENTRADA);
        enviosPorIp.values().removeIf(ventana -> ventana.inicio.isBefore(limite));
    }

    private static class Ventana {
        final Instant inicio;
        int envios = 1;

        Ventana(Instant inicio) {
            this.inicio = inicio;
        }
    }
}
