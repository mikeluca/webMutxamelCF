package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.CategoriaEdad;

public interface CategoriaEdadDao {

	/** Ordenadas por ORDEN (de menor a mayor edad). */
	List<CategoriaEdad> obtenerTodas();

	/** Crea la categoría o, si ya existe (CATEGORIA es la clave), actualiza su rango y su orden. */
	void guardar(CategoriaEdad categoriaEdad);

	void eliminar(String categoria);
}
