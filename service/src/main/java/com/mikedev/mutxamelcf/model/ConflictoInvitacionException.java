package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/**
 * El jugador ya está autorizado como invitado con otro equipo ese día (HTTP 409 con un código que la app entiende):
 * <ul>
 * <li>{@link #JUGADOR_INVITADO_ESE_DIA}: su propio entrenador (o cualquiera) intenta convocarlo ese día.</li>
 * <li>{@link #JUGADOR_YA_INVITADO_ESE_DIA}: otro equipo intenta invitarlo (o aprobar su invitación) ese día.</li>
 * </ul>
 */
public class ConflictoInvitacionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String JUGADOR_INVITADO_ESE_DIA = "JUGADOR_INVITADO_ESE_DIA";
    public static final String JUGADOR_YA_INVITADO_ESE_DIA = "JUGADOR_YA_INVITADO_ESE_DIA";

    private final String codigo;
    private final Long jugadorId;
    private final Long equipoDestinoId;
    private final String equipoDestino;
    private final LocalDate fecha;

    public ConflictoInvitacionException(
            String codigo, String mensaje, Long jugadorId, Long equipoDestinoId, String equipoDestino,
            LocalDate fecha) {
        super(mensaje);
        this.codigo = codigo;
        this.jugadorId = jugadorId;
        this.equipoDestinoId = equipoDestinoId;
        this.equipoDestino = equipoDestino;
        this.fecha = fecha;
    }

    public String getCodigo() {
        return codigo;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public Long getEquipoDestinoId() {
        return equipoDestinoId;
    }

    public String getEquipoDestino() {
        return equipoDestino;
    }

    public LocalDate getFecha() {
        return fecha;
    }
}
