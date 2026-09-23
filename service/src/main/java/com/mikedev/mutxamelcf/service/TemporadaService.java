package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.TemporadaDTO;

public interface TemporadaService {

    boolean guardarTemporada(TemporadaDTO temporada);

    TemporadaDTO obtenerPorId(Long id);

    TemporadaDTO obtenerTemporadaActiva();

    List<TemporadaDTO> obtenerTodos();

    void eliminar(Long id);
}