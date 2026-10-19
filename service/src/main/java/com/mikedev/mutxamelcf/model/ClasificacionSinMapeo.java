package com.mikedev.mutxamelcf.model;

/**
 * Fila del club (por su código de club) en un grupo que no tiene relación en
 * EQUIPOS_FFCV: probablemente un equipo nuevo al que aún no se ha asignado
 * grupo.
 */
public record ClasificacionSinMapeo(String competicion, String letra, String equipo) {
}
