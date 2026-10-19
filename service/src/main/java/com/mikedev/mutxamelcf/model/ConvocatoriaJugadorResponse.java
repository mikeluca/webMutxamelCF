package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

public class ConvocatoriaJugadorResponse {

    private Long jugadorId;
    private String jugador;
    private String respuesta;
    private String motivo;
    private LocalDateTime fechaRespuesta;

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
            String respuesta,
            String motivo,
            LocalDateTime fechaRespuesta) {

        this(jugadorId, jugador);
        this.respuesta = respuesta;
        this.motivo = motivo;
        this.fechaRespuesta = fechaRespuesta;
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

    /** "VA", "NO_PUEDE" o null (sin responder). */
    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFechaRespuesta() {
        return fechaRespuesta;
    }

    public void setFechaRespuesta(LocalDateTime fechaRespuesta) {
        this.fechaRespuesta = fechaRespuesta;
    }
}
