package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorPublicDTO;

public interface JugadorService {

	boolean guardarJugador(JugadorDTO jugador);

	List<JugadorDTO> obtenerJugadoresPorCategoria(String categoria);

	List<JugadorDTO> obtenerJugadoresPorEquipo(String equipo);

	List<JugadorDTO> obtenerTodos();

	JugadorDTO obtenerJugadorPorId(Long id);

	void eliminarJugador(Long id);

	List<JugadorPublicDTO> obtenerJugadoresPublicosPorEquipo(String equipo);

}
