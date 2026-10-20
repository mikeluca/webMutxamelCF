package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/** Ausencia justificada de un jugador a un partido. */
public record JustificacionFaltaPartidoResponse(
        Long partidoId,
        Long jugadorId,
        String jugador,
        String motivo,
        LocalDateTime fechaCreacion) {
}
