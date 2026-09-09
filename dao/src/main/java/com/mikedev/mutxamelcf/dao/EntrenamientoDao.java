package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.Entrenamiento;

public interface EntrenamientoDao {

    Entrenamiento guardar(Entrenamiento entrenamiento);

    void actualizar(Entrenamiento entrenamiento);

    Entrenamiento obtenerPorId(Long id);

    List<Entrenamiento> obtenerPorEquipo(Long equipoId);

    boolean existe(Long id);

    void eliminar(Long id);
}