package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * Un jugador del usuario de la app con su respuesta de continuidad para la temporada siguiente.
 *
 * @param respuesta PENDIENTE (todavía no ha contestado), SIGUE, NO_SIGUE o DUDA
 */
public record ContinuidadJugadorDTO(
		Long jugadorId,
		String nombre,
		String categoria,
		Long temporadaId,
		String temporada,
		String respuesta,
		LocalDateTime fechaRespuesta) {
}
