package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/**
 * Una fila de ListadoClasificaciones.xlsx ya limpia. {@code competicion} es la
 * clave del grupo: competición + ", " + grupo normalizados con
 * FfcvCsvParser.normalizarCompeticion (la misma forma que EQUIPOS_FFCV).
 *
 * @param letra   {@code null} cuando el Excel trae "-" (equipo sin letra)
 * @param club    código numérico de club de la FFCV; {@code null} si no es legible
 * @param jornada {@code null} si la celda está vacía
 */
public record FfcvFilaClasificacion(
        int filaExcel,
        String competicion,
        String letra,
        Integer club,
        String equipo,
        Integer jornada,
        int posicion,
        int puntos,
        int pj,
        int pg,
        int pe,
        int pp,
        int gf,
        int gc,
        LocalDate fechaUltPartido) {
}
