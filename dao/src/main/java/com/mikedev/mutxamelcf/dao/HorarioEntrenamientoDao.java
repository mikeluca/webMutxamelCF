package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.HorarioEntrenamiento;

public interface HorarioEntrenamientoDao {

    HorarioEntrenamiento crear(HorarioEntrenamiento horario);

    void actualizar(HorarioEntrenamiento horario);

    HorarioEntrenamiento obtenerPorId(Long id);

    List<HorarioEntrenamiento> obtenerActivosPorEquipo(Long equipoId);

    /**
     * Todos los horarios activos de todos los equipos, usado por el job
     * mensual que mantiene la ventana de generación de sesiones.
     */
    List<HorarioEntrenamiento> obtenerTodosActivos();

}
