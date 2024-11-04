package com.mikedev.mutxamelcf.model;

public enum EnumEquipos {
	ESCUELITA("Escuelita", "A"), PREBENJAMIN("Prebenjamín", "B"), BENJAMIN("Benjamín", "C"), ALEVIN("Alevín", "D"),
	INFANTIL("Infantil", "E"), CADETE("Cadete", "F"), JUVENIL("Juvenil", "G"), SENIOR("Senior", "H");

	private final String categoria;
	private final String orden;

	// Constructor
	EnumEquipos(String categoria, String orden) {
		this.categoria = categoria;
		this.orden = orden;
	}

	// Getters
	public String getCategoria() {
		return categoria;
	}

	public String getOrden() {
		return orden;
	}

	// Método para obtener Enum por categoría
	public static EnumEquipos fromCategoria(String categoria) {
		for (EnumEquipos equipo : EnumEquipos.values()) {
			if (equipo.getCategoria().equalsIgnoreCase(categoria)) {
				return equipo;
			}
		}
		throw new IllegalArgumentException("Categoría no válida: " + categoria);
	}

	public static String getOrdenByCategoria(String categoria) {
		for (EnumEquipos equipo : EnumEquipos.values()) {
			if (equipo.getCategoria().equalsIgnoreCase(categoria)) {
				return equipo.getOrden();
			}
		}
		throw new IllegalArgumentException("Categoría no válida: " + categoria);
	}
}
