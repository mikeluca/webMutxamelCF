package com.mikedev.mutxamelcf.service;

import java.util.List;
import java.util.Map;

import com.mikedev.mutxamelcf.model.EquipoDTO;

public interface EquipoService {

	boolean guardar(EquipoDTO equipo);

	List<String> obtenerCategorias();

	/** Los equipos activos en la temporada activa (selectores del panel y de la app; incluye pretemporada). */
	List<EquipoDTO> obtenerTodos();

	/** Todos los equipos, juegue o no esta temporada, con su estado (panel de Equipos). */
	List<EquipoDTO> obtenerTodosConEstadoTemporada();

	/** Los equipos activos de TIPO NORMAL (web pública: los de pretemporada no salen). */
	List<EquipoDTO> obtenerPublicos();

	/** Los equipos activos de la categoría. */
	List<EquipoDTO> obtenerTodosPorCategoria(String categoria);

	EquipoDTO obtenerEquipoPorId(Long id);

	void eliminarEquipo(Long id);

	Map<String, List<EquipoDTO>> obtenerEquiposAgrupadosPorCategoria(String deporte);

}
