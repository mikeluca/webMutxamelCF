package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.AsignacionRepartoRequest;
import com.mikedev.mutxamelcf.model.EquipoRepartoDTO;
import com.mikedev.mutxamelcf.model.RepartoCategoriaDTO;
import com.mikedev.mutxamelcf.model.ResultadoRepartoDTO;

/**
 * El reparto de septiembre: el coordinador pasa a los jugadores del equipo de pretemporada de cada categoría a un
 * equipo definitivo. Lo usan el panel (SUPER y ADMIN) y la app (COORDINADOR).
 */
public interface RepartoService {

	/** @throws SecurityException si el usuario de la app no es COORDINADOR ni ADMIN_APP */
	void comprobarCoordinador(Long usuarioAppId);

	/** Las categorías formativas que tienen equipos en la temporada activa. */
	List<String> categorias();

	/**
	 * Los jugadores sin equipo definitivo de la categoría y sus equipos definitivos.
	 *
	 * @throws IllegalArgumentException si la categoría no es una categoría formativa
	 */
	RepartoCategoriaDTO obtenerCategoria(String categoria);

	/**
	 * Asigna jugadores a un equipo definitivo: JUGADORES.EQUIPO_ID, JUGADOR_TEMPORADA (equipo, dorsal y
	 * EQUIPO_DEFINITIVO = 1; una PRUEBA pasa a CONFIRMADO). El equipo se activa en la temporada y, si el equipo de
	 * pretemporada se queda vacío, se desactiva. Todo en una transacción.
	 *
	 * @throws PruebasSinConfirmarException si hay pruebas y no se ha confirmado (no se asigna nada)
	 * @throws IllegalArgumentException     si el equipo o algún jugador no es válido
	 */
	ResultadoRepartoDTO asignar(AsignacionRepartoRequest request);

	/**
	 * Crea un equipo nuevo de la categoría: la siguiente letra, con la misma modalidad, inactivo hasta que se le
	 * asigne el primer jugador.
	 */
	EquipoRepartoDTO crearEquipo(String categoria);
}
