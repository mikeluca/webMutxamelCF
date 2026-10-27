package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.ResumenAplicacionDTO;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO;
import com.mikedev.mutxamelcf.model.Temporada;

/**
 * Asistente "Nueva temporada". Las filas de JUGADOR_TEMPORADA de una temporada que todavía no está activa son el
 * borrador: se preparan durante días y nada cambia en JUGADORES hasta {@link #aplicar}.
 */
public interface NuevaTemporadaService {

	/** Temporadas que pueden ser destino: no activas y que empiezan después de la activa. */
	List<Temporada> temporadasDestino();

	Temporada temporadaActiva();

	/**
	 * Crea una fila en el destino para cada jugador activo que todavía no tenga una; no toca las que ya existen.
	 *
	 * @return cuántas filas se han creado
	 * @throws IllegalArgumentException si la temporada no es un destino válido
	 */
	int generarPropuesta(Long destinoId);

	/** Borra el borrador entero y lo vuelve a generar. */
	int regenerar(Long destinoId);

	RevisionTemporadaDTO revision(Long destinoId);

	/**
	 * Guarda una fila (equipo, dorsal, baja) y el año de nacimiento del jugador, y la marca como revisada.
	 *
	 * @throws IllegalArgumentException si los datos no son válidos
	 */
	void guardarFila(Long destinoId, Long jugadorId, Long equipoId, Integer dorsal, boolean baja,
			Integer anioNacimiento);

	/** Añade al borrador a un jugador creado después de generar la propuesta, con las reglas de la propuesta. */
	void anadirSinPropuesta(Long destinoId, Long jugadorId);

	ResumenAplicacionDTO resumenAplicacion(Long destinoId);

	/**
	 * En una sola transacción: copia equipo, dorsal y categoría a JUGADORES, da de baja a las bajas, activa el destino
	 * y desactiva las demás. No envía notificaciones.
	 *
	 * @param confirmacion el nombre de la temporada, escrito por el administrador
	 * @throws IllegalStateException    si hay jugadores sin fila o filas pendientes
	 * @throws IllegalArgumentException si la confirmación no coincide o el destino no es válido
	 */
	void aplicar(Long destinoId, String confirmacion);
}
