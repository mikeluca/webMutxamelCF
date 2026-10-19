package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;
import java.util.List;

public record ClasificacionPublicDTO(
        String competicion,
        Integer jornada,
        LocalDateTime fechaImportacion,
        List<ClasificacionFilaPublicDTO> clasificacion) {
}
