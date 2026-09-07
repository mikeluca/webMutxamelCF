package com.mikedev.mutxamelcf.dao;

import com.mikedev.mutxamelcf.model.PreferenciasNotificacion;

public interface PreferenciasNotificacionDao {

    PreferenciasNotificacion obtenerPorUsuario(Long usuarioId);

    void guardar(PreferenciasNotificacion preferencias);

    void actualizar(PreferenciasNotificacion preferencias);
}