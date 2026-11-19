package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/**
 * GET /api/app/imagenes-redes/partido-primer-equipo: el próximo partido del Primer Equipo para el cartel. Si no está
 * disponible, solo vienen {@code disponible = false} y {@code motivo}; el resto es null.
 *
 * @param motivo      SIN_PARTIDO o YA_EMPEZADO (null si está disponible)
 * @param local       true si el Mutxamel juega en casa
 * @param escudoRivalUrl ruta relativa del escudo del rival, o null si no tiene
 * @param dia         yyyy-MM-dd
 * @param hora        HH:mm tal como está en el partido (puede ser null)
 * @param campo       el campo tal como sale en el cartel, en una línea ("Camp Nou Los Olmos (Mutxamel)" en casa)
 * @param competicion La Nostra Copa, Amistoso, Torneo o, en liga, la competición de la FFCV (null si no se conoce)
 * @param jornada     solo si el partido tiene jornada (importada de la FFCV)
 * @param tipo        LIGA, COPA, AMISTOSO o TORNEO
 */
public record PartidoPrimerEquipoResponse(boolean disponible, String motivo, Long partidoId, Boolean local,
        String rival, String escudoRivalUrl, LocalDate dia, String hora, String campo, String competicion,
        Integer jornada, String tipo, TextoPublicacion textoPublicacion) {

    public static final String SIN_PARTIDO = "SIN_PARTIDO";
    public static final String YA_EMPEZADO = "YA_EMPEZADO";

    /** El texto sugerido para "Copiar texto", en cada idioma. */
    public record TextoPublicacion(String es, String ca) {
    }

    public static PartidoPrimerEquipoResponse noDisponible(String motivo) {
        return new PartidoPrimerEquipoResponse(false, motivo, null, null, null, null, null, null, null, null, null,
                null, null);
    }
}
