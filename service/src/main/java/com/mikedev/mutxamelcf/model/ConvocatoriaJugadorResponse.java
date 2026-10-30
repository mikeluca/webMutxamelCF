package com.mikedev.mutxamelcf.model;

public class ConvocatoriaJugadorResponse {

    private Long jugadorId;
    private String jugador;

    /** true si el jugador ha justificado su ausencia a este partido (sigue en la convocatoria hasta que el entrenador lo sustituya). */
    private boolean ausenciaJustificada;
    private String motivoAusencia;

    /** true si es un jugador de otro equipo invitado a esta convocatoria. */
    private boolean invitado;
    private Long equipoOrigenId;
    private String equipoOrigen;

    /** PENDIENTE_COORDINACION, PENDIENTE_ENTRENADOR, AUTORIZADO o RECHAZADO; null en los jugadores del propio equipo. */
    private String estadoInvitacion;
    private String motivoRechazo;

    public ConvocatoriaJugadorResponse() {
    }

    public ConvocatoriaJugadorResponse(
            Long jugadorId,
            String jugador) {

        this.jugadorId = jugadorId;
        this.jugador = jugador;
    }

    public ConvocatoriaJugadorResponse(
            Long jugadorId,
            String jugador,
            boolean ausenciaJustificada,
            String motivoAusencia) {

        this(jugadorId, jugador);
        this.ausenciaJustificada = ausenciaJustificada;
        this.motivoAusencia = motivoAusencia;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getJugador() {
        return jugador;
    }

    public void setJugador(String jugador) {
        this.jugador = jugador;
    }

    public boolean isAusenciaJustificada() {
        return ausenciaJustificada;
    }

    public void setAusenciaJustificada(boolean ausenciaJustificada) {
        this.ausenciaJustificada = ausenciaJustificada;
    }

    public boolean isInvitado() {
        return invitado;
    }

    public void setInvitado(boolean invitado) {
        this.invitado = invitado;
    }

    public Long getEquipoOrigenId() {
        return equipoOrigenId;
    }

    public void setEquipoOrigenId(Long equipoOrigenId) {
        this.equipoOrigenId = equipoOrigenId;
    }

    public String getEquipoOrigen() {
        return equipoOrigen;
    }

    public void setEquipoOrigen(String equipoOrigen) {
        this.equipoOrigen = equipoOrigen;
    }

    public String getEstadoInvitacion() {
        return estadoInvitacion;
    }

    public void setEstadoInvitacion(String estadoInvitacion) {
        this.estadoInvitacion = estadoInvitacion;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public String getMotivoAusencia() {
        return motivoAusencia;
    }

    public void setMotivoAusencia(String motivoAusencia) {
        this.motivoAusencia = motivoAusencia;
    }
}
