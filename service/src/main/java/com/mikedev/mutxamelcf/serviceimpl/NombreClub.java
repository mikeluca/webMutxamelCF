package com.mikedev.mutxamelcf.serviceimpl;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nombres de clubes: la forma normalizada con la que se reconoce el mismo club en clasificaciones y partidos, y el
 * nombre "bonito" (el del Excel de la FFCV sin la letra del equipo) con el que se crea el club.
 */
public final class NombreClub {

    /** Nombres normalizados de nuestro club tal como aparecen en los datos ("Mutxamel C.F.", "C.F. Mutxamel"). */
    static final List<String> NOMBRES_PROPIOS = List.of("MUTXAMEL CF", "CF MUTXAMEL");

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    // Puntos y comillas (de todo tipo) desaparecen; guiones y paréntesis separan palabras.
    private static final Pattern A_ELIMINAR = Pattern.compile("[.·'\"`´‘’“”«»]");
    private static final Pattern A_ESPACIO = Pattern.compile("[-‐‑‒–—―()\\[\\]]");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    // Sufijo de equipo al final del nombre tal como viene: " A", " 'A'", " \"B\"", " (C)", " - D".
    private static final Pattern SUFIJO_VISIBLE = Pattern
            .compile("\\s+[-‐‑‒–—―]?\\s*[(\\[]?['\"`´‘’“”«»]*[A-Za-z]['\"`´‘’“”«»]*[)\\]]?\\s*$");

    private NombreClub() {
    }

    /**
     * Mayúsculas, sin acentos (Ñ y Ç como N y C), sin puntos, comillas, guiones ni paréntesis, espacios colapsados y
     * sin el sufijo de equipo (una letra suelta, entre comillas o no).
     * "C.D. Alicante 'A'" → "CD ALICANTE"; "Atlético Mutxamel" → "ATLETICO MUTXAMEL". Nunca devuelve null.
     */
    public static String normalizar(String nombre) {

        if (nombre == null) {
            return "";
        }

        String t = Normalizer.normalize(nombre, Normalizer.Form.NFD);
        t = MARCAS.matcher(t).replaceAll("");
        t = t.toUpperCase(Locale.ROOT);
        t = A_ELIMINAR.matcher(t).replaceAll("");
        t = A_ESPACIO.matcher(t).replaceAll(" ");
        t = ESPACIOS.matcher(t).replaceAll(" ").trim();

        int ultimo = t.lastIndexOf(' ');

        // Una letra suelta al final es la letra del equipo (siempre que quede algo del nombre).
        if (ultimo > 0 && t.length() - ultimo - 1 == 1 && Character.isLetter(t.charAt(t.length() - 1))) {
            t = t.substring(0, ultimo).trim();
        }

        return t;
    }

    /**
     * El nombre del club en el formato del Excel (mayúsculas, acentos y puntos como vienen) sin la letra del equipo:
     * "C.D. Alicante 'A'" → "C.D. Alicante". Nunca devuelve null.
     */
    public static String nombreVisible(String nombre) {

        if (nombre == null) {
            return "";
        }

        String t = ESPACIOS.matcher(nombre.trim()).replaceAll(" ");
        java.util.regex.Matcher m = SUFIJO_VISIBLE.matcher(t);

        if (m.find() && m.start() > 0) {
            t = t.substring(0, m.start()).trim();
        }

        return t;
    }

    /** true si el nombre es de nuestro club o de un equipo nuestro ("Mutxamel CF", "C.F. Mutxamel B", ...). */
    public static boolean esPropio(String nombre) {

        String n = normalizar(nombre);

        for (String propio : NOMBRES_PROPIOS) {
            if (n.equals(propio) || n.startsWith(propio + " ")) {
                return true;
            }
        }

        return false;
    }
}
