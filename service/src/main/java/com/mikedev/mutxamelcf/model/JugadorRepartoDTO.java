package com.mikedev.mutxamelcf.model;

/** Un jugador en la pantalla de reparto (su fila de la temporada activa). */
public record JugadorRepartoDTO(
		Long jugadorId,
		String nombre,
		Integer anioNacimiento,
		Integer dorsal,
		String estado,
		boolean equipoDefinitivo) {
}
