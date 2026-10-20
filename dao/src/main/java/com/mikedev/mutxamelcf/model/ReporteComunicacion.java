package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/** Fila de REPORTES_COMUNICACION: un mensaje reportado y, cuando se resuelve, cómo. */
public record ReporteComunicacion(
        Long id,
        Long comunicacionId,
        Long autorId,
        Long reportadorId,
        String motivo,
        String tituloCopia,
        String contenidoCopia,
        LocalDateTime fechaMensaje,
        LocalDateTime fechaReporte,
        String estado,
        Long resueltoPorId,
        LocalDateTime fechaResolucion,
        String notaResolucion) {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String DESCARTADO = "DESCARTADO";
    public static final String MENSAJE_BORRADO = "MENSAJE_BORRADO";
    public static final String AUTOR_DESACTIVADO = "AUTOR_DESACTIVADO";
}
