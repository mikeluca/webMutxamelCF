package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.PreferenciasNotificacion;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacionRequest;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacionResponse;

public interface PreferenciasNotificacionService {

    PreferenciasNotificacionResponse obtenerPorUsuario(
            Long usuarioId);

    PreferenciasNotificacionResponse actualizar(
            Long usuarioId,
            PreferenciasNotificacionRequest request);

    boolean puedeRecibir(
            Long usuarioId,
            String tipo);
}