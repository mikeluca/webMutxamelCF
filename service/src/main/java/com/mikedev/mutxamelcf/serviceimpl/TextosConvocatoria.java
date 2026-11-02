package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalTime;

import com.mikedev.mutxamelcf.model.Equipacion;

/**
 * Cómo se montan los avisos de convocatoria (nueva, cambio, convocado con otro equipo): el texto base; después, si hay
 * equipaje, un párrafo con el equipaje (y la mochila en la misma línea); y al final, en párrafo aparte, la ducha.
 */
final class TextosConvocatoria {

    private TextosConvocatoria() {
    }

    /**
     * "Equipaje: Segunda. En la mochila: Primera." en el idioma; vacío sin equipaje principal (la mochila no se indica
     * sin él). Como mucho unos 130 caracteres (los nombres tienen un máximo de 50).
     */
    static String parrafoEquipaje(TextosAvisos textos, String idioma, Equipacion equipacion, Equipacion mochila) {

        if (equipacion == null) {
            return "";
        }

        String principal = textos.texto(idioma, "equipaje.principal", "equipacion", equipacion.getNombre());

        return mochila == null
                ? principal
                : principal + " " + textos.texto(idioma, "equipaje.mochila", "equipacion", mochila.getNombre());
    }

    /**
     * El texto base, el párrafo del equipaje (si no está vacío) y la ducha, cada uno en su párrafo. La ducha lleva un
     * emoji fuera del plano básico (4 bytes en UTF-8): la bandeja lo guarda en COMUNICACIONES.CONTENIDO, que tiene que
     * ser de un tipo y un juego de caracteres de la BD que lo admitan (comprobación en
     * sql/2026-11-03_equipajes_convocatoria.sql).
     */
    static String componer(TextosAvisos textos, String idioma, String base, String parrafoEquipaje) {

        StringBuilder texto = new StringBuilder(base);

        if (parrafoEquipaje != null && !parrafoEquipaje.isEmpty()) {
            texto.append("\n\n").append(parrafoEquipaje);
        }

        return texto.append("\n\n").append(textos.texto(idioma, "ducha")).toString();
    }

    /** La hora guardada como texto, como HH:mm; si no tiene forma de hora, el texto tal cual (vacío si es nulo). */
    static String hora(TextosAvisos textos, String horaGuardada) {

        if (horaGuardada == null) {
            return "";
        }

        LocalTime hora = HoraTexto.leer(horaGuardada);

        return hora != null ? textos.hora(hora) : horaGuardada.trim();
    }
}
