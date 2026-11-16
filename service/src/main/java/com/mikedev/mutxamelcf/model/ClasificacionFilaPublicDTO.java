package com.mikedev.mutxamelcf.model;

/**
 * Una fila de la tabla pública.
 *
 * @param escudoUrl ruta relativa a la URL base del escudo del club ({@code /api/public/escudos-rivales/{id}?v=N}); null si
 *                  no tiene y siempre en la fila nuestra y en las de nuestro club (la app pone nuestro escudo con
*                  {@code esNuestro})
 * @param esPropio true si la fila es de nuestro club: la marcada {@code esNuestro}, cualquiera con nuestro código de
 *                club de la FFCV (los filiales) o cuyo nombre es el de nuestro club ("Mutxamel C.F. B"). La app pone
 *                entonces nuestro escudo; su {@code escudoUrl} sigue siendo null
 */
public record ClasificacionFilaPublicDTO(
        int posicion,
        String equipo,
        boolean esNuestro,
        int puntos,
        int pj,
        int pg,
        int pe,
        int pp,
        int gf,
        int gc,
        String escudoUrl,
        boolean esPropio) {
}
