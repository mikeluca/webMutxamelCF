package com.mikedev.mutxamelcf.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Las posiciones de un jugador: la lista cerrada que usan la API de la app, el panel y las respuestas. En
 * JUGADORES.POSICION se guarda el {@link #texto()} en castellano (es lo que ya hay en los datos y lo que muestra la web);
 * la app trabaja con el {@link #codigo()}.
 */
public enum PosicionJugador {

    PORTERO("Portero"),
    DEFENSA("Defensa"),
    CENTROCAMPISTA("Centrocampista"),
    EXTREMO("Extremo"),
    DELANTERO("Delantero");

    private final String texto;

    PosicionJugador(String texto) {
        this.texto = texto;
    }

    /** El código de la API (PORTERO, DEFENSA...). */
    public String codigo() {
        return name();
    }

    /** El texto en castellano que se guarda en JUGADORES.POSICION. */
    public String texto() {
        return texto;
    }

    /** Los textos de la lista, en orden (las opciones del desplegable del panel). */
    public static List<String> textos() {
        return Arrays.stream(values()).map(PosicionJugador::texto).toList();
    }

    /** El código exacto de la API (sin distinguir mayúsculas ni espacios de los lados), o null si no está en la lista. */
    public static PosicionJugador desdeCodigo(String codigo) {

        if (codigo == null) {
            return null;
        }

        String limpio = codigo.trim().toUpperCase(Locale.ROOT);

        return Arrays.stream(values()).filter(p -> p.name().equals(limpio)).findFirst().orElse(null);
    }

    /**
     * La posición a la que corresponde un texto guardado, comparando sin mayúsculas, acentos ni espacios de los lados
     * ("portero", "Centrocampista"), o null si el texto es nulo, vacío o no coincide con ninguna.
     */
    public static PosicionJugador desdeTexto(String texto) {

        if (texto == null) {
            return null;
        }

        String limpio = normalizar(texto);

        return Arrays.stream(values()).filter(p -> normalizar(p.texto).equals(limpio)).findFirst().orElse(null);
    }

    /** El código de un texto guardado, o null si no coincide con ninguna posición de la lista. */
    public static String codigoDeTexto(String texto) {

        PosicionJugador posicion = desdeTexto(texto);

        return posicion == null ? null : posicion.codigo();
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
