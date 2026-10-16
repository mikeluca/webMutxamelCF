package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Fila del CSV ya resuelta contra la base de datos. Al confirmar solo se
 * leen los datos del CSV (fecha...jornada): el estado y el equipo se
 * recalculan en el servidor, no se fían de lo que devuelva el navegador.
 *
 * @param equipoId     equipo resuelto vía EQUIPOS_FFCV, o null (SIN_EQUIPO)
 * @param partidoId    partido existente que se actualiza o con el que choca
 * @param cambios      descripción legible de lo que cambia (ACTUALIZADO)
 */
public record FfcvFilaPrevisualizacion(
        LocalDate fecha,
        String competicion,
        String letra,
        boolean local,
        String rival,
        String campo,
        String hora,
        Integer jornada,
        EstadoFilaFfcv estado,
        Long equipoId,
        String equipoNombre,
        Long partidoId,
        List<String> cambios) {

    public FfcvFilaCsv aFilaCsv() {
        return new FfcvFilaCsv(fecha, competicion, letra, local, rival, campo, hora, jornada);
    }
}
