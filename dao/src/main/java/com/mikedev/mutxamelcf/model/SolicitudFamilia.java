package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * Una solicitud de acceso de un familiar (tabla SOLICITUDES_FAMILIA). Al caducar se borra entera (datos del familiar y de
 * los hijos): el estado CADUCADA existe en la tabla pero el backend nunca deja una fila así.
 */
public record SolicitudFamilia(
        Long id,
        Long usuarioAppId,
        String nombre,
        String apellidos,
        String email,
        String telefono,
        String parentesco,
        String estado,
        String motivoRechazo,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaRevision,
        Long revisadaPorId,
        String revisadaPorPanel) {

    public static final String PENDIENTE_EMAIL = "PENDIENTE_EMAIL";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String CADUCADA = "CADUCADA";

    public static final String MADRE = "MADRE";
    public static final String PADRE = "PADRE";
    public static final String TUTOR = "TUTOR";
    public static final String OTRO = "OTRO";
}
