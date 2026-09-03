package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.FamiliarJugadorDTO;

public interface FamiliarJugadorService {

    boolean guardarFamiliarJugador(FamiliarJugadorDTO familiarJugador);

    FamiliarJugadorDTO obtenerPorId(Long id);

    List<FamiliarJugadorDTO> obtenerTodos();

    List<FamiliarJugadorDTO> obtenerFamiliaresDeJugador(Long jugadorId);

    List<FamiliarJugadorDTO> obtenerJugadoresDeFamiliar(Long familiarId);

    void eliminar(Long id);

    FamiliarJugadorDTO obtenerPrincipalDeJugador(Long jugadorId);

    boolean existeRelacion(Long familiarId, Long jugadorId);

    boolean tieneJugadores(Long familiarId);
}