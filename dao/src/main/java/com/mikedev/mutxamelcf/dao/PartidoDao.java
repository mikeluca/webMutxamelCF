package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Partido;

public interface PartidoDao {

    Partido crear(Partido partido);

    void actualizar(Partido partido);

    Partido obtenerPorId(Long id);

    List<Partido> obtenerPorEquipo(Long equipoId);

    List<Partido> obtenerUltimosPorEquipo(Long equipoId, int limite);

    Partido obtenerMasRelevantePorEquipoNombre(String equipoNombre, String categoria);

    Partido obtenerMasRelevantePorEquipo(Long equipoId);

    void eliminar(Long id);

}
