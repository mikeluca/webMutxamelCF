package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Clasificacion;

public interface ClasificacionDao {

    /**
     * Tabla guardada del equipo en la temporada activa, ordenada por posición (los empates por nombre). Mientras la
     * activa no tenga ningún partido y no tenga tabla, la de la temporada anterior.
     */
    List<Clasificacion> obtenerPorEquipo(Long equipoId);

    /** Tabla guardada del equipo en una temporada concreta (el historial), ordenada por posición. */
    List<Clasificacion> obtenerPorEquipoYTemporada(Long equipoId, Long temporadaId);

    /** Todas las tablas guardadas de una temporada, por equipo y posición. */
    List<Clasificacion> listarPorTemporada(Long temporadaId);

    /** Borra la tabla guardada del equipo y devuelve las filas borradas. */
    int eliminarPorEquipo(Long equipoId);

    /** Inserta todas las filas de golpe (el ID lo genera la base de datos). */
    void insertarTodas(List<Clasificacion> filas);

}
