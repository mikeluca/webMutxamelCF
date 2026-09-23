package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.NotificacionApp;

public interface NotificacionAppDao {

    Long guardar(NotificacionApp notificacion);

    NotificacionApp obtenerPorId(Long id);

    List<NotificacionApp> obtenerPorUsuario(Long usuarioId);

    List<NotificacionApp> obtenerNoLeidas(Long usuarioId);

    void marcarComoLeida(Long id, Long usuarioId);

    void marcarTodasComoLeidas(Long usuarioId);

    /**
     * Marca como leídas las notificaciones del usuario cuya
     * REFERENCIA_ID esté en la lista dada (usado para marcar de golpe
     * todos los mensajes de una conversación privada al abrirla).
     */
    void marcarLeidasPorReferencias(Long usuarioId, List<Long> referenciaIds);

    int contarNoLeidas(Long usuarioId);

    int contarComunicacionesNoLeidas(Long usuarioId);

}