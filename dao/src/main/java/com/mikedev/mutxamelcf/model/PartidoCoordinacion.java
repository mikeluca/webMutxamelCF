package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/** Un partido con su equipo y si tiene convocatoria, para el panel de coordinación. */
public record PartidoCoordinacion(
        Long partidoId,
        Long equipoId,
        String equipo,
        String rival,
        LocalDate dia,
        String hora,
        boolean local,
        boolean cancelado,
        Integer golesFavor,
        String resultado,
        boolean conConvocatoria) {
}
