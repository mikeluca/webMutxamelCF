package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorTemporada;

public interface JugadorTemporadaDao {

	JugadorTemporada obtener(Long jugadorId, Long temporadaId);

	/** Filas de la temporada con los datos del jugador y de su equipo actual, para la revisión. */
	List<JugadorTemporada> listarPorTemporada(Long temporadaId);

	/** Historial del jugador: una fila por temporada, la más reciente primero. */
	List<JugadorTemporada> listarPorJugador(Long jugadorId);

	/** Crea la fila o, si ya existe (JUGADOR_ID + TEMPORADA_ID), la actualiza. */
	void guardar(JugadorTemporada fila);

	/** Borra el borrador entero de la temporada. */
	int eliminarPorTemporada(Long temporadaId);

	/** Borra las filas de la temporada salvo las PRUEBA (no salen de la plantilla actual: no se pueden regenerar). */
	int eliminarBorradorSinPrueba(Long temporadaId);

	/** Si el jugador tiene alguna fila con ESTADO = 'PRUEBA' (una prueba pendiente de pasar a plantilla). */
	boolean esDePrueba(Long jugadorId);

	/** Jugadores de la plantilla actual (sin baja) que no tienen fila en esa temporada. */
	List<Jugador> jugadoresActivosSinFila(Long temporadaId);

	/** Cuántas filas (jugadores) tiene la temporada. */
	int contarPorTemporada(Long temporadaId);

	int contarPendientes(Long temporadaId);

	boolean existenParaEquipo(Long equipoId);
}
