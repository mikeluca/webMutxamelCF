package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

public class ConvocatoriaJugador {

    private Long id;
    private Long convocatoriaId;
    private Long jugadorId;

    /** "VA", "NO_PUEDE" o null (sin responder). */
    private String respuesta;
    private String motivo;
    private LocalDateTime fechaRespuesta;
    private Long usuarioAppRespuestaId;

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

    public Long getUsuarioAppRespuestaId() {
        return usuarioAppRespuestaId;
    }

    public void setUsuarioAppRespuestaId(Long usuarioAppRespuestaId) {
        this.usuarioAppRespuestaId = usuarioAppRespuestaId;
    }
}
