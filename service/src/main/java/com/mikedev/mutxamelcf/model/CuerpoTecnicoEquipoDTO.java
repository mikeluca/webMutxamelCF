package com.mikedev.mutxamelcf.model;

/**
 * Un equipo de una ficha de cuerpo técnico. {@code puesto} es el puesto en ese equipo si es distinto del general
 * (nulo en el caso habitual); {@code puestoMostrado} es el que se enseña: el de ese equipo o, si no hay, el general.
 *
 * <p>Lleva también getters de bean porque las plantillas Thymeleaf (SpEL) acceden por propiedad.
 */
public record CuerpoTecnicoEquipoDTO(Long equipoId, String equipoNombre, String categoria, String puesto,
		String puestoMostrado) {

	public Long getEquipoId() {
		return equipoId;
	}

	public String getEquipoNombre() {
		return equipoNombre;
	}

	public String getCategoria() {
		return categoria;
	}

	public String getPuesto() {
		return puesto;
	}

	public String getPuestoMostrado() {
		return puestoMostrado;
	}
}
