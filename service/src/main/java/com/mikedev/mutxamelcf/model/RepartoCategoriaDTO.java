package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * El reparto de una categoría formativa. A un lado, {@code porAsignar}: los jugadores sin equipo definitivo (los del
 * equipo de pretemporada y los de prueba). Al otro, {@code equipos}: los equipos definitivos de la categoría.
 *
 * @param pretemporada el equipo de pretemporada de la categoría (sin jugadores; están en {@code porAsignar}), o nulo
 */
public record RepartoCategoriaDTO(
		String categoria,
		EquipoRepartoDTO pretemporada,
		List<JugadorRepartoDTO> porAsignar,
		List<EquipoRepartoDTO> equipos) {
}
