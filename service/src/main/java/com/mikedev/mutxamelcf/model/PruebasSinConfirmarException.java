package com.mikedev.mutxamelcf.model;

import java.util.List;

/** Se intenta asignar jugadores de prueba sin haber confirmado que pasan a CONFIRMADO. No se ha asignado nada. */
public class PruebasSinConfirmarException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient List<String> pruebas;

	public PruebasSinConfirmarException(List<String> pruebas) {
		super("Hay jugadores de prueba en la lista y pasarían a CONFIRMADO: " + String.join(", ", pruebas)
				+ ". Confirma la asignación para continuar.");
		this.pruebas = pruebas;
	}

	public List<String> getPruebas() {
		return pruebas;
	}
}
