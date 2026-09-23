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
}