package com.mikedev.mutxamelcf.model;

/** Una fila de la foto del cuerpo técnico de una temporada (CUERPO_TECNICO_TEMPORADA) con los nombres ya resueltos. */
public record CuerpoTecnicoTemporadaFila(
		Long equipoId,
		String equipoNombre,
		Long cuerpoTecnicoId,
		String nombre,
		String apellidos,
		String puesto) {
}
