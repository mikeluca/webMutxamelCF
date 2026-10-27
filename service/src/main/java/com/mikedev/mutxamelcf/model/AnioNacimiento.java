package com.mikedev.mutxamelcf.model;

/**
 * Validación del año de nacimiento de un jugador: 4 dígitos entre {@link #ANIO_MINIMO} y (año actual − 3). El rango
 * llega hasta 1940 porque también hay jugadores adultos (Filial, Primer Equipo, Veteranos), donde el campo es opcional.
 */
public final class AnioNacimiento {

	public static final int ANIO_MINIMO = 1940;
	public static final int EDAD_MINIMA = 3;

	private AnioNacimiento() {
	}

	/** Mensaje de error para el panel, o {@code null} si el año es válido (el campo es opcional). */
	public static String mensajeError(Integer anio, int anioActual) {
		if (anio == null) {
			return null;
		}

		int minimo = ANIO_MINIMO;
		int maximo = anioActual - EDAD_MINIMA;

		if (anio < 1000 || anio > 9999 || anio < minimo || anio > maximo) {
			return "El año de nacimiento debe tener 4 dígitos y estar entre " + minimo + " y " + maximo + ".";
		}

		return null;
	}
}
