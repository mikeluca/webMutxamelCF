package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.FamiliarJugador;

public interface FamiliarJugadorDao {

    boolean guardarFamiliarJugador(FamiliarJugador familiarJugador);

    FamiliarJugador obtenerPorId(Long id);

    List<FamiliarJugador> obtenerTodos();

    List<FamiliarJugador> obtenerFamiliaresDeJugador(Long jugadorId);

    List<FamiliarJugador> obtenerJugadoresDeFamiliar(Long familiarId);

    FamiliarJugador obtenerPrincipalDeJugador(Long jugadorId);

    boolean existeRelacion(Long familiarId, Long jugadorId);

    boolean tieneJugadores(Long familiarId);

    void eliminar(Long id);
}