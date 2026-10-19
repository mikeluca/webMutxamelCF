package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Clasificacion;

public interface ClasificacionDao {

    /** Tabla guardada del equipo, ordenada por posición (los empates por nombre). */
    List<Clasificacion> obtenerPorEquipo(Long equipoId);

    /** Borra la tabla guardada del equipo y devuelve las filas borradas. */
    int eliminarPorEquipo(Long equipoId);

    /** Inserta todas las filas de golpe (el ID lo genera la base de datos). */
    void insertarTodas(List<Clasificacion> filas);

}
