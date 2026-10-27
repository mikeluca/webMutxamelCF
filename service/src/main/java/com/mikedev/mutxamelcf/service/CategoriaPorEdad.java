package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.model.CategoriaPorEdadResultado;
import com.mikedev.mutxamelcf.model.Temporada;

/**
 * Categoría de un jugador según su año de nacimiento. La edad es el año de inicio de la temporada (año de
 * FECHA_INICIO) menos el año de nacimiento; los rangos están en CATEGORIAS_EDAD.
 */
public interface CategoriaPorEdad {

	CategoriaPorEdadResultado categoriaPara(Integer anioNacimiento, Temporada temporada);

	CategoriaPorEdadResultado categoriaPara(Integer anioNacimiento, int anioInicioTemporada);

	/** Año en que empieza la temporada (año de FECHA_INICIO). */
	int anioInicio(Temporada temporada);

	List<CategoriaEdad> obtenerTodas();

	/**
	 * Crea o actualiza una categoría.
	 *
	 * @throws IllegalArgumentException si los datos no son válidos
	 */
	void guardar(CategoriaEdad categoriaEdad);

	void eliminar(String categoria);
}
