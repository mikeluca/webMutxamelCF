package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;

public interface ConvocatoriaJugadorDao {

    ConvocatoriaJugador guardar(
            ConvocatoriaJugador convocatoriaJugador);

    List<ConvocatoriaJugador> obtenerPorConvocatoria(
            Long convocatoriaId);

    ConvocatoriaJugador obtenerPorConvocatoriaYJugador(
            Long convocatoriaId,
            Long jugadorId);

    void eliminarPorConvocatoria(
            Long convocatoriaId);
}