package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.HistorialJugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorPublicDTO;

public interface JugadorService {

	/**
	 * Crea o actualiza al jugador y, en la misma transacción, su fila de JUGADOR_TEMPORADA de la temporada activa.
	 */
	boolean guardarJugador(JugadorDTO jugador);

	/** Jugadores activos de los equipos de la categoría. */
	List<JugadorDTO> obtenerJugadoresPorCategoria(String categoria);

	/** Jugadores activos del equipo con ese nombre (EQUIPO.NOMBRE). */
	List<JugadorDTO> obtenerJugadoresPorEquipo(String equipo);

	/** Todos los jugadores, activos y de baja. */
	List<JugadorDTO> obtenerTodos();

	List<JugadorDTO> obtenerActivos();

	List<JugadorDTO> obtenerBajas();

	JugadorDTO obtenerJugadorPorId(Long id);

	void eliminarJugador(Long id);

	/** FECHA_BAJA = hoy, EQUIPO_ID = NULL y BAJA = 1 en la fila de la temporada activa. */
	void darDeBaja(Long id);

	/** Quita la baja y lo asigna al equipo elegido. */
	void reactivar(Long id, Long equipoId);

	/** Temporada, equipo, categoría, dorsal y baja de cada temporada del jugador, la más reciente primero. */
	List<HistorialJugadorDTO> obtenerHistorial(Long jugadorId);

	List<JugadorPublicDTO> obtenerJugadoresPublicosPorEquipo(String equipo);

}
