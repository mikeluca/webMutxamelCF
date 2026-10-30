package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Equipacion;

public interface EquipacionDao {

	/** Todas (activas o no) por ORDEN y NOMBRE, con cuántas convocatorias usa cada una. Para el panel. */
	List<Equipacion> obtenerTodas();

	/** Solo las activas, por ORDEN y NOMBRE. Para elegir en la convocatoria. */
	List<Equipacion> obtenerActivas();

	/** null si no existe. */
	Equipacion obtenerPorId(Long id);

	/** null si no existe. Comparación exacta del nombre. */
	Equipacion obtenerPorNombre(String nombre);

	/** Inserta y devuelve el equipaje con su ID. */
	Equipacion crear(Equipacion equipacion);

	/** Actualiza todos los datos menos el ID. */
	void actualizar(Equipacion equipacion);

	/** Cuántas convocatorias lo usan, como equipaje principal o en la mochila. */
	int contarConvocatorias(Long id);

	void eliminar(Long id);
}
