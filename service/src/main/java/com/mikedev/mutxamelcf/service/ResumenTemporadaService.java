package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse;

public interface ResumenTemporadaService {

    /**
     * Resumen de la temporada activa del jugador. Lo puede ver quien tenga
     * vínculo con el jugador (él mismo o un familiar) o gestione su equipo.
     *
     * @throws SecurityException si no es ninguna de las dos cosas
     */
    ResumenTemporadaResponse obtenerResumen(Long usuarioAppId, Long jugadorId);
}
