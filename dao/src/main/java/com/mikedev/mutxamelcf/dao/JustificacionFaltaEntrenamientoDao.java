package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.JustificacionFaltaEntrenamiento;

public interface JustificacionFaltaEntrenamientoDao {

    JustificacionFaltaEntrenamiento crear(JustificacionFaltaEntrenamiento justificacion);

    void actualizar(JustificacionFaltaEntrenamiento justificacion);

    JustificacionFaltaEntrenamiento obtenerPorSesionYJugador(Long sesionId, Long jugadorId);

    List<JustificacionFaltaEntrenamiento> obtenerPorSesion(Long sesionId);

    /**
     * Elimina todas las justificaciones de las sesiones indicadas. Debe
     * llamarse ANTES de eliminar esas sesiones: FK_JUSTIF_FALTA_SESION
     * no tiene ON DELETE CASCADE, así que Oracle rechaza (ORA-02292) el
     * DELETE de una sesión con alguna justificación todavía asociada.
     */
    void eliminarPorSesionIds(List<Long> sesionIds);

}
