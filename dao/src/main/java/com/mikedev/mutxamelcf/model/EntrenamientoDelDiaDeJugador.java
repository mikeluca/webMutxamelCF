package com.mikedev.mutxamelcf.model;

/** Asistencia de un jugador a un entrenamiento, con la hora de su sesión (null si no la tiene) y si su lista está revisada. */
public record EntrenamientoDelDiaDeJugador(
        Long entrenamientoId,
        Long jugadorId,
        String hora,
        String estado,
        boolean listaRevisada) {
}
