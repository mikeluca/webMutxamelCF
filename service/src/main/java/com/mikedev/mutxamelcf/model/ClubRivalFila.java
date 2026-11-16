package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Una fila de la pantalla "Escudos de rivales".
 *
 * @param escudoUrl ruta relativa del escudo ({@code /api/public/escudos-rivales/{id}?v=N}); null si no tiene
 * @param ligas competiciones de la temporada activa en las que aparece (por las clasificaciones)
 * @param equipos equipos nuestros que se han enfrentado a él o en cuya clasificación aparece
 * @param partidos nº de partidos contra este club
 * @param alias nombres normalizados con los que aparece
 */
public record ClubRivalFila(
        Long id,
        String nombre,
        Integer codigoFfcv,
        boolean tieneEscudo,
        String escudoUrl,
        List<String> ligas,
        List<String> equipos,
        int partidos,
        List<String> alias) {
}
