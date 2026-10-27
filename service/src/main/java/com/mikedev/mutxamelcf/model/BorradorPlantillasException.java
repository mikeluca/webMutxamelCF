package com.mikedev.mutxamelcf.model;

/**
 * La temporada es futura y tiene un borrador de plantillas del asistente "Nueva temporada": se puede borrar, pero
 * solo si el administrador lo confirma, porque el borrador se borra con ella.
 */
public class BorradorPlantillasException extends IllegalStateException {

	private static final long serialVersionUID = 1L;

	private final int jugadores;

	public BorradorPlantillasException(int jugadores) {
		super("Se borrará también el borrador de plantillas de " + jugadores + " jugadores");
		this.jugadores = jugadores;
	}

	public int getJugadores() {
		return jugadores;
	}
}
