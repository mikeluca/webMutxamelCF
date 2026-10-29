package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.HistorialTemporadaDTO;
import com.mikedev.mutxamelcf.model.Temporada;

/** El historial de las temporadas, solo para el panel. */
public interface HistorialTemporadaService {

	/** Todas las temporadas, la más reciente primero (para el selector). */
	List<Temporada> temporadas();

	/**
	 * El historial de una temporada.
	 *
	 * @param equipoId si no es nulo, se añaden las estadísticas de ese equipo con las fechas de la temporada
	 * @throws IllegalArgumentException si la temporada no existe
	 */
	HistorialTemporadaDTO obtener(Long temporadaId, Long equipoId);
}
