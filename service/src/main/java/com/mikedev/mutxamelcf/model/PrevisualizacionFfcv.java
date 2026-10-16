package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

/**
 * @param desde primer día que aparece en el archivo (null si no hay partidos)
 * @param hasta último día que aparece en el archivo
 */
public record PrevisualizacionFfcv(
        LocalDate desde,
        LocalDate hasta,
        List<FfcvFilaPrevisualizacion> filas,
        List<FfcvDescanso> descansan,
        List<FfcvDesaparecido> desaparecidos,
        List<FfcvErrorLinea> errores) {
}
