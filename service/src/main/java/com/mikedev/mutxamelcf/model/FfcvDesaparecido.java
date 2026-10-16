package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/**
 * Partido de origen FFCV dentro del fin de semana importado que ya no viene
 * en el archivo ("¿aplazado?"). Nunca se borra automáticamente.
 */
public record FfcvDesaparecido(
        Long partidoId,
        Long equipoId,
        String equipoNombre,
        LocalDate fecha,
        String rival,
        String hora,
        boolean tieneConvocatoria) {
}
