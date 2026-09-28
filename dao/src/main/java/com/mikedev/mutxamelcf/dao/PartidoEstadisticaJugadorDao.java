package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugador;

public interface PartidoEstadisticaJugadorDao {

    PartidoEstadisticaJugador guardar(
            PartidoEstadisticaJugador estadistica);

    List<PartidoEstadisticaJugador> obtenerPorPartido(
            Long partidoId);

    void eliminarPorPartido(
            Long partidoId);
}
