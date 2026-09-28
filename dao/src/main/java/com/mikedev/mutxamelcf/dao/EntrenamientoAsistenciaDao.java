package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.EntrenamientoAsistencia;

public interface EntrenamientoAsistenciaDao {

        EntrenamientoAsistencia guardar(
                        EntrenamientoAsistencia asistencia);

        List<EntrenamientoAsistencia> obtenerPorEntrenamiento(
                        Long entrenamientoId);

        EntrenamientoAsistencia obtenerPorEntrenamientoYJugador(
                        Long entrenamientoId,
                        Long jugadorId);

        void eliminarPorEntrenamiento(
                        Long entrenamientoId);

        boolean existePorJugador(
                        Long jugadorId);

        /**
         * Cambia el estado de la fila de asistencia ya existente de un
         * jugador concreto dentro de un entrenamiento (a diferencia del
         * reemplazo completo de {@link #eliminarPorEntrenamiento}, aquí se
         * actualiza una única fila). No hace nada si esa fila no existe.
         */
        void actualizarEstado(
                        Long entrenamientoId,
                        Long jugadorId,
                        String estado);
}