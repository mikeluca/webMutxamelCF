package com.mikedev.mutxamelcf.service;

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
}