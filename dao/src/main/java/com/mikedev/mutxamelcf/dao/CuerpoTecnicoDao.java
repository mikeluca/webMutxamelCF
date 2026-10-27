package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoEquipo;

/**
 * Fichas de cuerpo técnico. Una ficha puede llevar varios equipos (tabla CUERPO_TECNICO_EQUIPO) y el puesto mostrado
 * en un equipo es COALESCE(CTE.PUESTO, CT.PUESTO). Las columnas CUERPO_TECNICO.EQUIPO, CATEGORIA y DEPORTE son solo una
 * copia informativa: ninguna consulta depende de ellas.
 */
public interface CuerpoTecnicoDao {

	/** Inserta o actualiza los datos de la ficha (no sus equipos); al insertar rellena el id generado. */
	boolean guardar(CuerpoTecnico cuerpoTecnico);

	/** La ficha con sus equipos, o {@code null}. */
	CuerpoTecnico obtenerPorId(Long id);

	/**
	 * Una entrada por cada (ficha, equipo) de la categoría (EQUIPO.CATEGORIA), con el equipo y el puesto de ese
	 * equipo.
	 */
	List<CuerpoTecnico> obtenerTodosPorCategoria(String categoria);

	/** Una entrada por técnico del equipo con ese nombre (EQUIPO.NOMBRE), con el puesto en ese equipo. */
	List<CuerpoTecnico> obtenerTodosPorEquipo(String nombreEquipo);

	/** Una entrada por técnico del equipo, con el puesto en ese equipo. */
	List<CuerpoTecnico> obtenerTodosPorEquipoId(Long equipoId);

	/** Todas las fichas, cada una con sus equipos. */
	List<CuerpoTecnico> obtenerTodos();

	/** Borra la ficha y sus filas de CUERPO_TECNICO_EQUIPO. */
	void eliminar(Long id);

	List<CuerpoTecnicoEquipo> obtenerEquipos(Long cuerpoTecnicoId);

	/**
	 * Deja los equipos de la ficha exactamente como se indica: añade los nuevos, cambia el puesto de los que siguen y
	 * quita los que sobran. Un equipo repetido en la lista cuenta una vez.
	 */
	void reemplazarEquipos(Long cuerpoTecnicoId, List<CuerpoTecnicoEquipo> equipos);

	/** ¿Alguna ficha lleva ese equipo? */
	boolean existenParaEquipo(Long equipoId);

	/** Copia informativa de la ficha (el primer equipo por nombre, o nulos si no tiene). */
	void actualizarCopiaInformativa(Long cuerpoTecnicoId, String equipo, String categoria, String deporte);

	/**
	 * Pasa los vínculos de cuentas de la app de una ficha a otra sin repetir (si la cuenta ya está vinculada a la de
	 * destino se descarta el de origen).
	 *
	 * @return cuántos vínculos se han movido
	 */
	int moverVinculosApp(Long desdeId, Long haciaId);

	/** Cuántas cuentas de la app tiene vinculadas la ficha. */
	int contarVinculosApp(Long cuerpoTecnicoId);
}
