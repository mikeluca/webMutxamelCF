package com.mikedev.mutxamelcf.serviceimpl;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * El campo tal como sale en el cartel "Próximo partido": en casa y jugando en Els Oms ({@link #esElsOms}), el Camp Nou
 * Los Olmos; si no (fuera, o en casa pero en otro campo, p. ej. el Hogar Provincial), el del partido recortado (sin "Campo Municipal", "Camp de Futbol"... ni identificadores de terreno como F-11, F-8 o C.A.).
 */
public final class CampoCartel {

    /** El campo de casa, en las tres líneas fijas del cartel. */
    public static final String CASA_LINEAS = "CAMP NOU\nLOS OLMOS\n(MUTXAMEL)";

    /** El campo de casa en una sola línea (para el texto de la publicación). */
    public static final String CASA = "Camp Nou Los Olmos (Mutxamel)";

    // Prefijos que se quitan solo al principio, uno detrás de otro.
    private static final Pattern PREFIJO = Pattern.compile(
            "^\\s*(?:"
                    + "(?:campo|camp)\\s+(?:de\\s+)?f[uú]tbol(?:\\s+(?:municipal|mpal\\.?))?"
                    + "|(?:campo|camp)\\s+(?:municipal|mpal\\.?)(?:\\s+de\\s+f[uú]tbol)?"
                    + "|estadio\\s+(?:municipal|mpal\\.?)"
                    + ")(?![\\p{L}\\d])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Identificadores de terreno de juego, en cualquier posición.
    private static final Pattern TERRENO = Pattern.compile(
            "(?<![\\p{L}\\d])(?:F-?(?:11|8|7|5)|C\\.A\\.?|c[eé]sped(?:\\s+(?:artificial|natural))?)(?![\\p{L}\\d])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern SEPARADORES_EXTREMOS = Pattern.compile("^[\\s\\-–,.:;/]+|[\\s\\-–,:;/]+$");
    private static final Pattern ESPACIOS = Pattern.compile("\\s{2,}");

    // Un guion con espacio solo a un lado (" -Diputacion", "Alicante- ") es un separador mal escrito: pasa a " - ".
    private static final Pattern GUION_PEGADO = Pattern.compile("\\s+-(?=[^\\s-])|(?<=[^\\s-])-\\s+");

    // "olmos" u "oms" como palabra completa, sobre el texto normalizado.
    private static final Pattern ELS_OMS = Pattern.compile("(?<![a-z0-9])(?:olmos|oms)(?![a-z0-9])");
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");

    private CampoCartel() {
    }

    /**
     * El campo de un partido jugado fuera, recortado y con las mayúsculas originales: "Campo Mpal. Les Costeres F-11
     * Gata" → "Les Costeres Gata". Si al recortar no queda nada devuelve el texto original; vacío si no hay campo.
     */
    public static String recortar(String campo) {

        if (campo == null || campo.isBlank()) {
            return "";
        }

        String original = ESPACIOS.matcher(campo.trim()).replaceAll(" ");
        String texto = original;

        String anterior;

        do {
            anterior = texto;
            texto = PREFIJO.matcher(texto).replaceFirst("");
        } while (!texto.equals(anterior));

        texto = TERRENO.matcher(texto).replaceAll(" ");
        texto = SEPARADORES_EXTREMOS.matcher(texto).replaceAll("");
        texto = ESPACIOS.matcher(texto).replaceAll(" ").trim();
        texto = GUION_PEGADO.matcher(texto).replaceAll(" - ");
        texto = ESPACIOS.matcher(texto).replaceAll(" ").trim();

        return texto.isEmpty() ? original : texto;
    }

    /**
     * true si el partido se juega en Els Oms: campo vacío o nulo (partidos antiguos o sin campo) o que, normalizado
     * (minúsculas, sin acentos, sin puntos y con los espacios simplificados), contiene la palabra completa "olmos" u
     * "oms". "Hogar Provincial" o "Campo Mpal. de Mutxamel" no.
     */
    public static boolean esElsOms(String campo) {

        if (campo == null || campo.isBlank()) {
            return true;
        }

        String texto = Normalizer.normalize(campo.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD);
        texto = MARCAS.matcher(texto).replaceAll("").replace('.', ' ');
        texto = ESPACIOS.matcher(texto).replaceAll(" ").trim();

        return ELS_OMS.matcher(texto).find();
    }

    /** true si el Mutxamel juega en casa y en Els Oms (el campo enCasaElsOms de las respuestas). */
    public static boolean enCasaElsOms(boolean local, String campo) {
        return local && esElsOms(campo);
    }

    /** Lo que se escribe en el cartel: las líneas fijas de casa o el campo recortado en mayúsculas. */
    public static String paraCartel(boolean local, String campo) {

        if (enCasaElsOms(local, campo)) {
            return CASA_LINEAS;
        }

        return recortar(campo).toUpperCase(java.util.Locale.ROOT);
    }

    /** Lo que se escribe en el texto de la publicación (una sola línea, con las mayúsculas originales). */
    public static String paraTexto(boolean local, String campo) {
        return enCasaElsOms(local, campo) ? CASA : recortar(campo);
    }
}
