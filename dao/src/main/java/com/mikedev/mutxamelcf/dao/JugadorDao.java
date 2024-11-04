package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Jugador;

public interface JugadorDao {
	
	boolean guardarJugador(Jugador jugador);

	Jugador obtenerPorId(String dni);

	List<Jugador> obtenerTodosPorCategoria(String categoria);
	
	List<Jugador> obtenerTodosPorEquipo(String equipo);

	List<Jugador> obtenerTodos();

	void eliminar(String dni);
	
}
