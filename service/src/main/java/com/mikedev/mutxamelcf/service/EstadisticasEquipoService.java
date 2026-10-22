package com.mikedev.mutxamelcf.service;

import com.mikedev.mutxamelcf.model.EstadisticasEquipoResponse;
import com.mikedev.mutxamelcf.model.TarjetasEquipoResponse;

/** Tarjetas y estadísticas de un equipo para su staff (puedeGestionarEquipo), en la temporada activa. */
public interface EstadisticasEquipoService {

    /** Por jugador, ordenado por amarillas descendente; vacío si el equipo no admite estadísticas. */
    TarjetasEquipoResponse obtenerTarjetas(Long usuarioAppId, Long equipoId);

    EstadisticasEquipoResponse obtenerEstadisticas(Long usuarioAppId, Long equipoId);
}
