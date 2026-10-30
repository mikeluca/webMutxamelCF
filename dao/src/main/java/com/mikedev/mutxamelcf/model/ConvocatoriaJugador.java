package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * Un jugador de una convocatoria. Los del propio equipo solo llevan los tres primeros campos; los invitados de otro
 * equipo (EQUIPO_ORIGEN_ID no nulo) llevan además los de la invitación.
 */
public class ConvocatoriaJugador {

    private Long id;
    private Long convocatoriaId;
    private Long jugadorId;

    private Long equipoOrigenId;
    private EstadoInvitacion estadoInvitacion;
    private LocalDateTime fechaSolicitud;
    private Long usuarioInvitaId;
    private Long usuarioCoordinadorId;
    private LocalDateTime fechaCoordinacion;
    private LocalDateTime fechaPeticionEntrenador;
    private boolean aviso12hEnviado;
    private LocalDateTime fechaAutorizacion;
    private String motivoRechazo;

    public ConvocatoriaJugador() {
    }

    public ConvocatoriaJugador(
            Long id,
            Long convocatoriaId,
            Long jugadorId) {

        this.id = id;
        this.convocatoriaId = convocatoriaId;
        this.jugadorId = jugadorId;
    }

    /** true si es un jugador de otro equipo invitado a esta convocatoria. */
    public boolean isInvitado() {
        return equipoOrigenId != null;
    }

    /** true si juega: es del propio equipo o es un invitado autorizado. */
    public boolean isConvocadoEfectivo() {
        return equipoOrigenId == null || estadoInvitacion == EstadoInvitacion.AUTORIZADO;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConvocatoriaId() {
        return convocatoriaId;
    }

    public void setConvocatoriaId(Long convocatoriaId) {
        this.convocatoriaId = convocatoriaId;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public Long getEquipoOrigenId() {
        return equipoOrigenId;
    }

    public void setEquipoOrigenId(Long equipoOrigenId) {
        this.equipoOrigenId = equipoOrigenId;
    }

    public EstadoInvitacion getEstadoInvitacion() {
        return estadoInvitacion;
    }

    public void setEstadoInvitacion(EstadoInvitacion estadoInvitacion) {
        this.estadoInvitacion = estadoInvitacion;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(LocalDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public Long getUsuarioInvitaId() {
        return usuarioInvitaId;
    }

    public void setUsuarioInvitaId(Long usuarioInvitaId) {
        this.usuarioInvitaId = usuarioInvitaId;
    }

    public Long getUsuarioCoordinadorId() {
        return usuarioCoordinadorId;
    }

    public void setUsuarioCoordinadorId(Long usuarioCoordinadorId) {
        this.usuarioCoordinadorId = usuarioCoordinadorId;
    }

    public LocalDateTime getFechaCoordinacion() {
        return fechaCoordinacion;
    }

    public void setFechaCoordinacion(LocalDateTime fechaCoordinacion) {
        this.fechaCoordinacion = fechaCoordinacion;
    }

    public LocalDateTime getFechaPeticionEntrenador() {
        return fechaPeticionEntrenador;
    }

    public void setFechaPeticionEntrenador(LocalDateTime fechaPeticionEntrenador) {
        this.fechaPeticionEntrenador = fechaPeticionEntrenador;
    }

    public boolean isAviso12hEnviado() {
        return aviso12hEnviado;
    }

    public void setAviso12hEnviado(boolean aviso12hEnviado) {
        this.aviso12hEnviado = aviso12hEnviado;
    }

    public LocalDateTime getFechaAutorizacion() {
        return fechaAutorizacion;
    }

    public void setFechaAutorizacion(LocalDateTime fechaAutorizacion) {
        this.fechaAutorizacion = fechaAutorizacion;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }
}
