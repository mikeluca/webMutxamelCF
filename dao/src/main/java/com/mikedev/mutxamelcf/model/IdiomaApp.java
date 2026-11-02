package com.mikedev.mutxamelcf.model;

import java.util.List;
import java.util.Locale;

/** Idiomas en los que la app muestra los textos (y en los que se generan los avisos). */
public final class IdiomaApp {

    public static final String ES = "es";
    public static final String CA = "ca";
    public static final String EN = "en";

    /** Orden fijo: castellano, valenciano e inglés. */
    public static final List<String> TODOS = List.of(ES, CA, EN);

    private IdiomaApp() {
    }

    /** El idioma admitido ("es", "ca" o "en", sin distinguir mayúsculas); cualquier otro valor, incluido null, es "es". */
    public static String normalizar(String idioma) {

        if (idioma == null) {
            return ES;
        }

        String valor = idioma.trim().toLowerCase(Locale.ROOT);

        return CA.equals(valor) || EN.equals(valor) ? valor : ES;
    }
}
