package com.mikedev.mutxamelcf.dao;

import java.util.List;
import java.util.Map;

import com.mikedev.mutxamelcf.model.Equipo;

public interface EquipoDao {

	boolean guardar(Equipo equipo);

	List<String> obtenerCategorias();

	List<Equipo> obtenerTodos();

	List<Equipo> obtenerTodosPorCategoria(String categoria);

	List<Equipo> obtenerTodosPorDeporte(String deporte);

	Equipo obtenerEquipoPorId(Long id);

	Equipo obtenerEquipoPorNombre(String nombre);

	void eliminarEquipo(Long id);

	Map<String, List<Equipo>> obtenerEquiposAgrupadosPorCategoria(String deporte);

}
