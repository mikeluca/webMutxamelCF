package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Lo que ve el administrador al revisar el borrador de una temporada: las filas por equipo destino y las listas
 * de trabajo (pendientes, sin equipo, bajas propuestas, sin propuesta).
 */
public record RevisionTemporadaDTO(
		Temporada destino,
		List<GrupoEquipo> equipos,
		List<Fila> pendientes,
		List<Fila> sinEquipo,
		List<Fila> bajas,
		List<SinPropuesta> sinPropuesta,
		List<String> avisosDorsal,
		int totalFilas) {

	/** Una fila del borrador. {@code dorsalRepetido}: otro jugador del mismo equipo destino tiene ese dorsal. */
	public record Fila(
			Long jugadorId,
			String nombre,
			Integer anioNacimiento,
			Long equipoActualId,
			String equipoActual,
			Long equipoId,
			String equipoNombre,
			String categoria,
			Integer dorsal,
			boolean baja,
			boolean pendiente,
			String nota,
			boolean dorsalRepetido,
			boolean jugadorDeBaja) {
	}

	public record GrupoEquipo(Long equipoId, String equipoNombre, int jugadores, List<Fila> filas) {
	}

	/** Jugador activo creado después de generar la propuesta: aún no tiene fila en el destino. */
	public record SinPropuesta(Long jugadorId, String nombre, Integer anioNacimiento, String equipoActual) {
	}
}
