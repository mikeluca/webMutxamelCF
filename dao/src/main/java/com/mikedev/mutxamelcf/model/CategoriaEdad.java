package com.mikedev.mutxamelcf.model;

/**
 * Rango de edad de una categoría. La edad es el año en que empieza la temporada menos el año de nacimiento; la
 * categoría coincide con EQUIPO.CATEGORIA.
 */
public class CategoriaEdad {

	private String categoria;
	private Integer edadMin;
	private Integer edadMax;
	private Integer orden;

	public CategoriaEdad() {
		super();
	}

	public CategoriaEdad(String categoria, Integer edadMin, Integer edadMax, Integer orden) {
		super();
		this.categoria = categoria;
		this.edadMin = edadMin;
		this.edadMax = edadMax;
		this.orden = orden;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public Integer getEdadMin() {
		return edadMin;
	}

	public void setEdadMin(Integer edadMin) {
		this.edadMin = edadMin;
	}

	public Integer getEdadMax() {
		return edadMax;
	}

	public void setEdadMax(Integer edadMax) {
		this.edadMax = edadMax;
	}

	public Integer getOrden() {
		return orden;
	}

	public void setOrden(Integer orden) {
		this.orden = orden;
	}

	public boolean contiene(int edad) {
		return edadMin != null && edadMax != null && edad >= edadMin && edad <= edadMax;
	}
}
