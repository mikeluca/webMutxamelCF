package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Jugador;

public interface JugadorDao {
	
	boolean guardarJugador(Jugador jugador);

	Jugador obtenerPorId(Long id);

	List<Jugador> obtenerTodosPorCategoria(String categoria);
	
	List<Jugador> obtenerTodosPorEquipo(String equipo);

	List<Jugador> obtenerTodos();

	void eliminar(Long id);
	
}
