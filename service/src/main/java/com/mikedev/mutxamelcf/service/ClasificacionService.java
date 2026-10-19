package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.ClasificacionPublicDTO;

public interface ClasificacionService {

    /**
     * Última clasificación importada del equipo, ordenada por posición, o
     * {@code null} si todavía no hay datos.
     */
    ClasificacionPublicDTO obtenerPublicaPorEquipo(Long equipoId);

}
