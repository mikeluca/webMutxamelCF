package com.mikedev.mutxamelcf.dao;

import java.time.LocalDate;
import java.util.List;

import com.mikedev.mutxamelcf.model.Jugador;

public interface JugadorDao {

	/** Inserta o actualiza; al insertar rellena el id generado en el jugador. */
	boolean guardarJugador(Jugador jugador);

	Jugador obtenerPorId(Long id);

	/** Jugadores activos de los equipos de esa categoría (EQUIPO.CATEGORIA). */
	List<Jugador> obtenerTodosPorCategoria(String categoria);

	/** Jugadores activos del equipo, por EQUIPO_ID. */
	List<Jugador> obtenerTodosPorEquipoId(Long equipoId);

	/** Jugadores activos del equipo cuyo EQUIPO.NOMBRE es ese (la web pública enlaza por nombre). */
	List<Jugador> obtenerTodosPorNombreEquipo(String nombreEquipo);

	/** Todos, activos y bajas. */
	List<Jugador> obtenerTodos();

	List<Jugador> obtenerActivos();

	List<Jugador> obtenerBajas();

	void eliminar(Long id);

	/** FECHA_BAJA = fecha y EQUIPO_ID = NULL. */
	boolean darDeBaja(Long id, LocalDate fecha);

	/** Quita la baja y lo asigna al equipo (copiando nombre, categoría y deporte). */
	boolean reactivar(Long id, Long equipoId, String equipoNombre, String categoria, String deporte);

	/** Quita la baja (FECHA_BAJA = NULL) sin equipo y con la categoría dada: una prueba que pasa a plantilla. */
	boolean activarSinEquipo(Long id, String categoria);

	/** Asignación de plantilla al aplicar una temporada: equipo (con las copias) y dorsal. */
	boolean asignarPlantilla(Long id, Long equipoId, String equipoNombre, String categoria, String deporte,
			Integer dorsal);

	boolean actualizarAnioNacimiento(Long id, Integer anioNacimiento);

}
