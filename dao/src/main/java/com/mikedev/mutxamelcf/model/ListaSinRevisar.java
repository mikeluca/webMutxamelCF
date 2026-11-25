package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/** Un entrenamiento con sesión cuya lista de asistencia nadie ha revisado (pantalla "Pendientes"). */
public record ListaSinRevisar(
        Long entrenamientoId,
        Long equipoId,
        Long sesionId,
        LocalDate fecha,
        String hora,
        String estadoSesion) {
}
