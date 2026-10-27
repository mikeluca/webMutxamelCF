package com.mikedev.mutxamelcf.model;

/**
 * Categoría que corresponde por edad. {@code categoria} solo viene rellena con {@link Estado#CATEGORIA}; en los
 * demás casos {@code estado} explica por qué no hay ninguna.
 */
public record CategoriaPorEdadResultado(String categoria, Estado estado, Integer edad) {

	public enum Estado {
		/** Hay una categoría para esa edad. */
		CATEGORIA,
		/** No se conoce el año de nacimiento. */
		SIN_ANIO_NACIMIENTO,
		/** Supera la edad máxima de todas las categorías: termina la etapa formativa. */
		SUPERA_EDAD_MAXIMA,
		/** Es menor que la edad mínima de todas las categorías. */
		POR_DEBAJO_DEL_MINIMO,
		/** Ninguna categoría cubre esa edad (hueco entre rangos) o no hay categorías configuradas. */
		SIN_CATEGORIA
	}

	public boolean tieneCategoria() {
		return estado == Estado.CATEGORIA;
	}
}
