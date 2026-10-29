package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Asignar jugadores a un equipo definitivo. {@code confirmarPruebas} confirma que los jugadores de prueba de la
 * lista pasan a CONFIRMADO (si no se confirma y hay alguno, no se asigna nada).
 */
public record AsignacionRepartoRequest(
		Long equipoId,
		List<Jugador> jugadores,
		boolean confirmarPruebas) {

	/** Un jugador con el dorsal que tendrá en el equipo (nulo = sin dorsal). */
	public record Jugador(Long jugadorId, Integer dorsal) {
	}
}
