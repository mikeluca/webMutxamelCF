package com.mikedev.mutxamelcf.model;

/**
 * Un hijo de una solicitud de familia (tabla SOLICITUDES_FAMILIA_JUGADOR): lo que escribió la familia y, al aprobar, el
 * jugador real elegido por quien revisa. Nunca lleva fecha de nacimiento ni otros datos del menor.
 */
public record SolicitudFamiliaJugador(
        Long id,
        Long solicitudId,
        String nombre,
        String apellidos,
        String equipoTexto,
        Long jugadorId,
        String estado) {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADO = "APROBADO";
    public static final String RECHAZADO = "RECHAZADO";
}
