package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.JustificacionFaltaEntrenamiento;

public interface JustificacionFaltaEntrenamientoDao {

    JustificacionFaltaEntrenamiento crear(JustificacionFaltaEntrenamiento justificacion);

    void actualizar(JustificacionFaltaEntrenamiento justificacion);

    JustificacionFaltaEntrenamiento obtenerPorSesionYJugador(Long sesionId, Long jugadorId);

    List<JustificacionFaltaEntrenamiento> obtenerPorSesion(Long sesionId);

}
