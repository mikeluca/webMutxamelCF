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
}