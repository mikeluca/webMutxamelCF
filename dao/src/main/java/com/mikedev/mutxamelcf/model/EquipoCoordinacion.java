package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/**
 * Equipo para el panel de coordinación. {@code jornada} y {@code fecha} solo se
 * rellenan en los descansos.
 */
public record EquipoCoordinacion(Long equipoId, String equipo, Integer jornada, LocalDate fecha) {
}
