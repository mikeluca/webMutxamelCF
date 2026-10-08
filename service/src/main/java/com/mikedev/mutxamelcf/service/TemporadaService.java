package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.TemporadaDTO;

public interface TemporadaService {

    boolean guardarTemporada(TemporadaDTO temporada);

    TemporadaDTO obtenerPorId(Long id);

    TemporadaDTO obtenerTemporadaActiva();

    /**
     * Último día de la temporada activa, o {@code null} si no hay
     * ninguna temporada activa (o no tiene fecha de fin). Es el tope
     * hasta el que se pueden crear entrenamientos.
     */
    java.time.LocalDate obtenerFechaFinTemporadaActiva();

    List<TemporadaDTO> obtenerTodos();

    void eliminar(Long id);
}