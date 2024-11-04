package com.mikedev.mutxamelcf.service;

import java.util.List;
import java.util.Map;

import com.mikedev.mutxamelcf.model.EquipoDTO;

public interface EquipoService {

	boolean guardar(EquipoDTO equipo);

	List<String> obtenerCategorias();

	List<EquipoDTO> obtenerTodos();

	List<EquipoDTO> obtenerTodosPorCategoria(String categoria);

	EquipoDTO obtenerEquipoPorId(Long id);

	void eliminarEquipo(Long id);

	Map<String, List<EquipoDTO>> obtenerEquiposAgrupadosPorCategoria(String deporte);

}
