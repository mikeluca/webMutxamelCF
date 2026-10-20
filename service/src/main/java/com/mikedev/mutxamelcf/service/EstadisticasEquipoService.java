package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.EstadisticasEquipoResponse;
import com.mikedev.mutxamelcf.model.TarjetasJugadorResponse;

/** Tarjetas y estadísticas de un equipo para su staff (puedeGestionarEquipo), en la temporada activa. */
public interface EstadisticasEquipoService {

    /** Por jugador, ordenado por amarillas descendente. */
    List<TarjetasJugadorResponse> obtenerTarjetas(Long usuarioAppId, Long equipoId);

    EstadisticasEquipoResponse obtenerEstadisticas(Long usuarioAppId, Long equipoId);
}
