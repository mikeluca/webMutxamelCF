package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;
import java.util.List;

/** Una competición de la clasificación pública de un equipo (un equipo puede tener varias: la Liga y la Copa). */
public record ClasificacionCompeticionDTO(
        String competicion,
        Integer jornada,
        LocalDateTime fechaImportacion,
        List<ClasificacionFilaPublicDTO> clasificacion) {
}
