package com.mikedev.mutxamelcf.model;

/**
 * Relación nueva elegida en la vista previa para una fila SIN_EQUIPO:
 * competición (+ letra) del CSV -> equipo del club.
 */
public record AsignacionEquipoFfcv(String competicion, String letra, Long equipoId) {
}
