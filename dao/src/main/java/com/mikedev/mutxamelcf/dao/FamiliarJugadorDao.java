package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.FamiliarJugador;

public interface FamiliarJugadorDao {

    boolean guardarFamiliarJugador(FamiliarJugador familiarJugador);

    FamiliarJugador obtenerPorId(Long id);

    List<FamiliarJugador> obtenerTodos();

    List<FamiliarJugador> obtenerFamiliaresDeJugador(Long jugadorId);

    List<FamiliarJugador> obtenerJugadoresDeFamiliar(Long familiarId);

    /**
     * Los jugadores vinculados de TODOS los familiares, con su equipo y categoría actuales, en una sola consulta (el
     * listado del panel no hace una por familiar). Por familiar y, dentro, por apellidos y nombre del jugador.
     */
    List<com.mikedev.mutxamelcf.model.JugadorDeFamiliar> obtenerJugadoresDeTodosLosFamiliares();

    FamiliarJugador obtenerPrincipalDeJugador(Long jugadorId);

    boolean existeRelacion(Long familiarId, Long jugadorId);

    boolean tieneJugadores(Long familiarId);

    void eliminar(Long id);
}