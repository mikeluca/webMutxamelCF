package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una convocatoria de un jugador vinculado al usuario (él mismo o un hijo),
 * con su respuesta y el plazo para darla.
 */
public record MiConvocatoriaResponse(
        Long convocatoriaId,
        Long jugadorId,
        String jugador,
        Long equipoId,
        String equipo,
        String rival,
        LocalDate fechaPartido,
        String horaPartido,
        String campo,
        boolean local,
        boolean cancelado,
        String horaConvocatoria,
        String lugarConvocatoria,
        String respuesta,
        String motivo,
        LocalDateTime fechaRespuesta,
        boolean puedeResponder,
        LocalDateTime limiteRespuesta) {
}
