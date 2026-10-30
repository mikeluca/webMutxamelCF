package com.mikedev.mutxamelcf.model;

/** El resultado de aprobar, rechazar o liberar una invitación. */
public record InvitacionResponse(
        Long id,
        Long convocatoriaId,
        Long partidoId,
        Long jugadorId,
        String jugador,
        Long equipoOrigenId,
        String equipoOrigen,
        String estado,
        String motivoRechazo) {
}
