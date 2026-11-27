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

	/**
	 * Cambia de equipo a un jugador activo con lo mismo que el guardado de su ficha (categoría, deporte, historial de la
	 * temporada activa, regla del dorsal); no toca ningún otro dato. Si su dorsal choca con el de un jugador activo del
	 * equipo de destino, se mueve sin dorsal. Una transacción por llamada.
	 *
	 * @throws IllegalArgumentException si el jugador o el equipo no existen
	 * @throws IllegalStateException    si el jugador está de baja
	 */
	com.mikedev.mutxamelcf.model.CambioEquipoJugador cambiarEquipo(Long jugadorId, Long equipoId);

	/** Temporada, equipo, categoría, dorsal y baja de cada temporada del jugador, la más reciente primero. */
	List<HistorialJugadorDTO> obtenerHistorial(Long jugadorId);

	List<JugadorPublicDTO> obtenerJugadoresPublicosPorEquipo(String equipo);

	/**
	 * Si el equipo (por nombre) todavía no tiene ningún jugador con EQUIPO_DEFINITIVO = 1 en la temporada activa: la web
	 * pública muestra entonces "Plantillas en preparación" en vez de su plantilla. Sin temporada activa, o si la
	 * temporada activa no tiene todavía ninguna ficha de jugador (los datos no se han migrado), es falso.
	 */
	boolean plantillaEnPreparacion(String equipoNombre);

}
