package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.EstadoAppResponse;

/** Estado de la app para ADMIN_APP (SecurityException al resto). */
public interface EstadoAppService {

    String FILTRO_SIN_DISPOSITIVO = "SIN_DISPOSITIVO";
    String FILTRO_SIN_NOTIFICACIONES = "SIN_NOTIFICACIONES";
    String FILTRO_VERSION_ANTIGUA = "VERSION_ANTIGUA";
    String FILTRO_INACTIVOS = "INACTIVOS";

    /** Máximo de cuentas que devuelve una lista. */
    int MAXIMO_USUARIOS = 200;

    /** Días sin entrar a partir de los cuales una cuenta cuenta como inactiva. */
    int DIAS_INACTIVIDAD = 30;

    EstadoAppResponse obtenerEstado(int solicitanteId);

    /** Las cuentas de uno de los filtros (IllegalArgumentException si el filtro no existe). */
    List<EstadoAppResponse.UsuarioEstado> listarUsuarios(int solicitanteId, String filtro);
}
