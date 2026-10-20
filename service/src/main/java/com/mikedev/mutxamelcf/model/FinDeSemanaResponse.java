package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Panel del fin de semana para coordinación. {@code desde} y {@code hasta} son el
 * viernes y el domingo del fin de semana de referencia.
 */
public record FinDeSemanaResponse(
        LocalDate desde,
        LocalDate hasta,
        List<PartidoSinConvocatoria> sinConvocatoria,
        List<ResultadoPendiente> resultadosPendientes,
        List<Descanso> descansan,
        List<SinPartido> sinPartido,
        Resumen resumen) {

    public record PartidoSinConvocatoria(
            Long equipoId, String equipo, Long partidoId, String rival, LocalDate dia, String hora, boolean local) {
    }

    public record ResultadoPendiente(
            Long equipoId, String equipo, Long partidoId, String rival, LocalDate dia, String hora,
            int diasDeRetraso) {
    }

    public record Descanso(Long equipoId, String equipo, Integer jornada) {
    }

    public record SinPartido(Long equipoId, String equipo) {
    }

    /** {@code ultimaImportacionFfcv} es null si no se puede saber. */
    public record Resumen(
            int sinConvocatoria, int resultadosPendientes, int descansan, int sinPartido,
            LocalDateTime ultimaImportacionFfcv) {
    }
}
