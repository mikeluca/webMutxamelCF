package com.mikedev.mutxamelcf.model;

/**
 * Una fila de la clasificación de la Liga Veteranos Ciudad de Alicante (LVCA) tal como la da la web.
 *
 * @param equipo    el nombre del equipo tal cual sale en la web (span.scoretable-teamname)
 * @param esNuestro true en la fila del Mutxamel (su nombre normalizado empieza por MUTXAMEL)
 */
public record FilaClasificacionVeteranos(
        int posicion,
        String equipo,
        int pj,
        int pg,
        int pe,
        int pp,
        int gf,
        int gc,
        int dif,
        int puntos,
        boolean esNuestro) {
}
