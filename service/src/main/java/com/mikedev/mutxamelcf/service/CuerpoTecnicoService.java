package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoPublicDTO;
import com.mikedev.mutxamelcf.model.FusionFichasDTO;

public interface CuerpoTecnicoService {

	/**
	 * Guarda la ficha y sus equipos ({@link CuerpoTecnicoDTO#getEquipos()}: equipo y puesto en ese equipo) en UNA
	 * transacción: alta, baja y cambio de puesto de las filas de CUERPO_TECNICO_EQUIPO. Un puesto de equipo vacío o
	 * igual al general se guarda como NULL. Sin equipos también es válido.
	 *
	 * @throws IllegalArgumentException si algún equipo no existe
	 */
	boolean guardarCuerpoTecnico(CuerpoTecnicoDTO cuerpoTecnico);

	/** Una entrada por (técnico, equipo) de la categoría, con el puesto en ese equipo. */
	List<CuerpoTecnicoDTO> obtenerCuerpoTecnicoPorCategoria(String categoria);

	/** Una entrada por técnico del equipo con ese nombre, con el puesto en ese equipo. */
	List<CuerpoTecnicoDTO> obtenerCuerpoTecnicoPorEquipo(String equipo);

	/** Todas las fichas, cada una con sus equipos. */
	List<CuerpoTecnicoDTO> obtenerTodos();

	CuerpoTecnicoDTO obtenerCuerpoTecnicoPorId(Long id);

	void eliminarCuerpoTecnico(Long id);

	/** El cuerpo técnico del equipo (por ID, a través de CUERPO_TECNICO_EQUIPO) con el puesto en ese equipo. */
	List<CuerpoTecnicoPublicDTO> obtenerCuerpoTecnicoPublicoPorEquipoId(Long equipoId);

	/** ¿Alguna ficha lleva este equipo? */
	boolean equipoTieneCuerpoTecnico(Long equipoId);

	/** Resumen de lo que haría {@link #fusionarFichas}; no cambia nada. */
	FusionFichasDTO resumenFusion(Long conservarId, List<Long> duplicadasIds);

	/**
	 * Fusiona fichas duplicadas en una sola, en UNA transacción: los equipos de las duplicadas pasan a la conservada
	 * (sin repetir; si el equipo ya está se mantiene el de la conservada; si el puesto de la duplicada es distinto del
	 * general de la conservada se guarda como puesto de ese equipo), los vínculos con cuentas de la app pasan a la
	 * conservada y las duplicadas se borran.
	 *
	 * @throws IllegalArgumentException si no hay duplicadas, alguna ficha no existe o se intenta fusionar una ficha
	 *                                  consigo misma
	 */
	void fusionarFichas(Long conservarId, List<Long> duplicadasIds);

}
