package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Entrenamiento;

public interface EntrenamientoDao {

    Entrenamiento guardar(Entrenamiento entrenamiento);

    void actualizar(Entrenamiento entrenamiento);

    Entrenamiento obtenerPorId(Long id);

    List<Entrenamiento> obtenerPorEquipo(Long equipoId);

    /**
     * Igual que {@link #obtenerPorEquipo(Long)} pero limitado a las fechas
     * entre desde y hasta (ambas incluidas); un extremo null queda abierto.
     */
    List<Entrenamiento> obtenerPorEquipoEnRango(Long equipoId, java.time.LocalDate desde, java.time.LocalDate hasta);

    /**
     * Entrenamiento creado automáticamente para esa sesión del calendario
     * (SESIONES_ENTRENAMIENTO), o {@code null} si esa sesión todavía no
     * tiene ningún ENTRENAMIENTO vinculado. La UNIQUE(SESION_ENTRENAMIENTO_ID)
     * garantiza que como mucho hay uno.
     */
    Entrenamiento obtenerPorSesionEntrenamientoId(Long sesionEntrenamientoId);

    boolean existe(Long id);

    void eliminar(Long id);
}