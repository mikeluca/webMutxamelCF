package com.mikedev.mutxamelcf.model;

/**
 * Lo que ha pasado al cambiar de equipo a un jugador. {@code detalle} es el texto para el resumen (vacío si se ha movido
 * sin más).
 */
public record CambioEquipoJugador(Resultado resultado, String detalle) {

    public enum Resultado {
        /** Movido con su dorsal (o sin dorsal, si no tenía). */
        MOVIDO,
        /** Movido, pero el dorsal que tenía ya lo lleva otro jugador activo del equipo de destino. */
        MOVIDO_SIN_DORSAL,
        /** Ya estaba en ese equipo: no se toca. */
        SALTADO
    }
}
