package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDate;
import java.util.Locale;

/** El texto sugerido (en castellano) para acompañar las imágenes en la publicación. */
public final class TextoPublicacionRedes {

    public static final String HASHTAGS = "#somMTX #mareaAzul #MutxamelCF";

    private static final String[] MESES = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
            "septiembre", "octubre", "noviembre", "diciembre" };

    private TextoPublicacionRedes() {
    }

    public static String previa(LocalDate desde, LocalDate hasta) {

        return "⚽ ¡Fin de semana de fútbol en Mutxamel C.F.!\n\n"
                + "Estos son los partidos de nuestros equipos " + periodo(desde, hasta) + ". "
                + "¡Os esperamos en las gradas para animar a la marea azul! 💙\n\n"
                + HASHTAGS;
    }

    public static String resultados(LocalDate desde, LocalDate hasta) {

        return "📊 Así han quedado los resultados de nuestros equipos " + periodo(desde, hasta) + ".\n\n"
                + "¡Gracias a jugadores, entrenadores y familias por vuestro esfuerzo y apoyo! 💙\n\n"
                + HASHTAGS;
    }

    /** "del 14 al 16 de noviembre" o, si cruza de mes, "del 30 de octubre al 1 de noviembre". */
    static String periodo(LocalDate desde, LocalDate hasta) {

        if (desde.getMonth() == hasta.getMonth() && desde.getYear() == hasta.getYear()) {
            return "del " + desde.getDayOfMonth() + " al " + hasta.getDayOfMonth() + " de " + mes(hasta);
        }

        return "del " + desde.getDayOfMonth() + " de " + mes(desde) + " al " + hasta.getDayOfMonth() + " de "
                + mes(hasta);
    }

    private static String mes(LocalDate fecha) {
        return MESES[fecha.getMonthValue() - 1].toLowerCase(Locale.ROOT);
    }
}
