package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Temporada;

public interface TemporadaDao {

    boolean guardarTemporada(Temporada temporada);

    void desactivarOtrasTemporadas(Long temporadaId);

    /** Marca la temporada como activa (no toca las demás: ver {@link #desactivarOtrasTemporadas}). */
    boolean activar(Long temporadaId);

    Temporada obtenerPorId(Long id);

    Temporada obtenerTemporadaActiva();

    List<Temporada> obtenerTodos();

    void eliminar(Long id);
}