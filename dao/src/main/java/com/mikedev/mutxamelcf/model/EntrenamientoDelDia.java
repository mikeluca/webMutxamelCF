package com.mikedev.mutxamelcf.model;

/**
 * Asistencia de un jugador a un entrenamiento, con la hora de su sesión
 * ({@code null} si el entrenamiento no tiene sesión o la sesión no tiene hora).
 */
public record EntrenamientoDelDia(String hora, String estado) {
}
