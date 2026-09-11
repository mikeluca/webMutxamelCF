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

    int contarNoLeidas(Long usuarioId);

    int contarComunicacionesNoLeidas(Long usuarioId);

}