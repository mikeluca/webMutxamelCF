package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * La clasificación pública de un equipo (GET /api/public/equipos/{id}/clasificacion).
 *
 * <p>
 * Los cuatro primeros campos son los de la competición principal, como siempre (así las versiones antiguas de la app
 * siguen funcionando). {@code competiciones} trae todas las del equipo: los equipos de la FFCV tienen una sola; el equipo
 * Veteranos, la Liga y la Copa. La principal es la de más equipos (si empatan, la primera por nombre) y va la primera.
 */
public record ClasificacionPublicDTO(
        String competicion,
        Integer jornada,
        LocalDateTime fechaImportacion,
        List<ClasificacionFilaPublicDTO> clasificacion,
        List<ClasificacionCompeticionDTO> competiciones) {

    /** Una sola competición: la lista {@code competiciones} lleva solo esa. */
    public ClasificacionPublicDTO(
            String competicion,
            Integer jornada,
            LocalDateTime fechaImportacion,
            List<ClasificacionFilaPublicDTO> clasificacion) {
        this(competicion, jornada, fechaImportacion, clasificacion,
                List.of(new ClasificacionCompeticionDTO(competicion, jornada, fechaImportacion, clasificacion)));
    }
}
