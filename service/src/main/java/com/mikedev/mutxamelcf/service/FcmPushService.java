package com.mikedev.mutxamelcf.service;

import java.util.Map;

public interface FcmPushService {

    void enviarNotificacion(
            String tokenFcm,
            String titulo,
            String mensaje);

    void enviarNotificacionAUsuario(
            Long usuarioId,
            String tipo,
            String titulo,
            String mensaje,
            Long referenciaId);

    /**
     * Igual que {@link #enviarNotificacionAUsuario(Long, String, String,
     * String, Long)}, añadiendo pares clave-valor extra al payload de
     * datos del push (por ejemplo, el id del autor de un mensaje
     * privado, para poder abrir el chat directamente al pulsar la
     * notificación).
     */
    void enviarNotificacionAUsuario(
            Long usuarioId,
            String tipo,
            String titulo,
            String mensaje,
            Long referenciaId,
            Map<String, String> datosExtra);

    /**
     * Publica una notificación en un topic de FCM (p.ej. "noticias",
     * "resultados"), para quien se haya suscrito a él desde la app sin
     * necesidad de tener cuenta. La suscripción/desuscripción al topic
     * la gestiona la propia app directamente contra Firebase; aquí
     * solo publicamos.
     */
    void enviarATopic(
            String topic,
            String titulo,
            String mensaje,
            Map<String, String> datosExtra);

    /**
     * Igual que {@link #enviarATopic(String, String, String, Map)} sin
     * datos extra en el payload.
     */
    void enviarATopic(
            String topic,
            String titulo,
            String mensaje);
}