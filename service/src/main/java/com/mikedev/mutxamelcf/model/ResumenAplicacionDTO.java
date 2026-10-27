package com.mikedev.mutxamelcf.model;

import java.util.List;
import java.util.Map;

/**
 * Resumen de "Aplicar y activar temporada". Si {@code bloqueos} no está vacío no se puede aplicar.
 *
 * @param jugadoresPorEquipo   jugadores que tendrá cada equipo en la temporada nueva
 * @param cambiosDeEquipo      jugadores que cambian de equipo respecto a hoy (incluye los que hoy no tienen)
 * @param bajas                jugadores que se dan de baja
 * @param sinEquipo            jugadores activos que se quedan sin equipo
 */
public record ResumenAplicacionDTO(
		String temporada,
		Map<String, Integer> jugadoresPorEquipo,
		int cambiosDeEquipo,
		int bajas,
		int sinEquipo,
		List<String> bloqueos) {

	public boolean puedeAplicar() {
		return bloqueos.isEmpty();
	}
}
