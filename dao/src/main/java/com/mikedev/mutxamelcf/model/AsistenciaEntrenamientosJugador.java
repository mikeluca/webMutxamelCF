package com.mikedev.mutxamelcf.model;

/** Filas de asistencia de un jugador en un rango, contadas por estado. */
public record AsistenciaEntrenamientosJugador(
        int total, int presentes, int retrasos, int malComportamiento, int faltas, int justificadas) {

    /** Asistió: PRESENTE + RETRASO + MAL_COMPORTAMIENTO. */
    public int asistidos() {
        return presentes + retrasos + malComportamiento;
    }
}
