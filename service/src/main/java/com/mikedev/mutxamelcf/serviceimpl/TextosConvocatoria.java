package com.mikedev.mutxamelcf.serviceimpl;

import com.mikedev.mutxamelcf.model.Equipacion;

/** Frases que llevan los avisos de convocatoria (bandeja y push). */
final class TextosConvocatoria {

    /**
     * Última frase, en párrafo aparte, de los avisos que convocan al jugador. Lleva un emoji fuera del plano básico (4
     * bytes en UTF-8): la bandeja lo guarda en COMUNICACIONES.CONTENIDO, que tiene que ser de un tipo y un juego de
     * caracteres de la BD que lo admitan (comprobación en sql/2026-11-03_equipajes_convocatoria.sql). Si no, dejar aquí
     * solo el texto.
     */
    static final String LINEA_DUCHA = "🚿 Ducha recomendable después del partido";

    private TextosConvocatoria() {
    }

    static String conDucha(String texto) {
        return texto + "\n\n" + LINEA_DUCHA;
    }

    /**
     * "Equipaje: Segunda." y, si hay mochila, "En la mochila: Primera."; vacío sin equipaje. Como mucho unos 130
     * caracteres (los nombres tienen un máximo de 50).
     */
    static String fraseEquipaje(Equipacion equipacion, Equipacion mochila) {

        if (equipacion == null) {
            return "";
        }

        return "Equipaje: " + equipacion.getNombre() + "."
                + (mochila != null ? " En la mochila: " + mochila.getNombre() + "." : "");
    }
}
