package com.mikedev.mutxamelcf.model;

import java.util.List;

/** Un equipo de la temporada activa en el reparto, con los jugadores que tiene asignados. */
public record EquipoRepartoDTO(
		Long equipoId,
		String nombre,
		String grupo,
		String modalidad,
		boolean activo,
		List<JugadorRepartoDTO> jugadores) {
}
