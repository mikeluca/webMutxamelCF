package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoTemporadaFila;

/** Lectura de la foto del cuerpo técnico que se toma al cerrar cada temporada (la escribe el cierre). */
public interface CuerpoTecnicoTemporadaDao {

	/** El cuerpo técnico de una temporada, por equipo (EQUIPO.ORDEN y nombre) y apellidos. */
	List<CuerpoTecnicoTemporadaFila> listarPorTemporada(Long temporadaId);
}
