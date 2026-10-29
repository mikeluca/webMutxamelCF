package com.mikedev.mutxamelcf.dao;

import java.util.List;
import java.util.Map;

import com.mikedev.mutxamelcf.model.Equipo;

/**
 * Qué equipos juegan cada temporada lo dice EQUIPO_TEMPORADA. "Activos" son los de ACTIVO = 1 en la temporada activa
 * (los de pretemporada incluidos); "públicos", los activos de TIPO = 'NORMAL'. Sin temporada activa no se filtra nada.
 * La modalidad que devuelven todas las lecturas es la de EQUIPO_TEMPORADA.MODALIDAD de la temporada activa, con
 * EQUIPO.MODALIDAD como respaldo si no hay fila.
 */
public interface EquipoDao {

	/**
	 * Crea o actualiza el equipo y, si hay temporada activa, su fila de EQUIPO_TEMPORADA: {@code activoTemporada} nulo
	 * conserva el estado actual (un equipo nuevo entra activo) y la modalidad se guarda también en EQUIPO.MODALIDAD.
	 */
	boolean guardar(Equipo equipo);

	List<String> obtenerCategorias();

	/** Todos los equipos, activos o no (panel de Equipos), con su estado en la temporada activa. */
	List<Equipo> obtenerTodos();

	/** Los equipos activos en la temporada activa (selectores del panel y de la app). */
	List<Equipo> obtenerActivos();

	/** Los activos de TIPO = 'NORMAL' (web pública). */
	List<Equipo> obtenerActivosPublicos();

	List<Equipo> obtenerActivosPorCategoria(String categoria);

	/** Los activos de TIPO = 'NORMAL' de un deporte (web pública). */
	List<Equipo> obtenerActivosPublicosPorDeporte(String deporte);

	Equipo obtenerEquipoPorId(Long id);

	Equipo obtenerEquipoPorNombre(String nombre);

	void eliminarEquipo(Long id);

	/** Los activos de TIPO = 'NORMAL' de un deporte, agrupados por categoría (web pública). */
	Map<String, List<Equipo>> obtenerEquiposAgrupadosPorCategoria(String deporte);

}
