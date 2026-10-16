package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/**
 * Un partido del Mutxamel tal como viene en el CSV de la FFCV. El árbitro
 * y el resultado del CSV se descartan a propósito y no figuran aquí.
 *
 * @param competicion competición ya normalizada (FfcvCsvParser.normalizarCompeticion)
 * @param letra       letra del Mutxamel en el CSV ("A", "B"...), o null si no lleva
 * @param local       true si el Mutxamel juega como local
 * @param rival       el otro lado, tal cual viene en el CSV
 * @param campo       nombre del campo (no la dirección)
 * @param hora        HH:mm, o null si el CSV no la trae
 * @param jornada     número de jornada, o null
 */
public record FfcvFilaCsv(
        LocalDate fecha,
        String competicion,
        String letra,
        boolean local,
        String rival,
        String campo,
        String hora,
        Integer jornada) {
}
