package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Detalle de la convocatoria de un partido para un jugador o un familiar suyo. Sin
 * datos de contacto ni motivos de ausencias: solo lo que necesita la familia.
 *
 * @param convocado si el jugador por el que se pregunta está en la convocatoria
 * @param convocados los convocados, ordenados por dorsal
 */
public record ConvocatoriaPartidoResponse(
        Long convocatoriaId,
        String rival,
        LocalDate fecha,
        String hora,
        String campo,
        boolean local,
        String horaCitacion,
        String lugarCitacion,
        boolean convocado,
        List<Convocado> convocados) {

    public record Convocado(String nombre, String apellidos, Integer dorsal) {
    }
}
