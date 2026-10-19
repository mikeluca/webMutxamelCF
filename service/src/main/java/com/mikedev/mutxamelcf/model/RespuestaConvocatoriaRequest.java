package com.mikedev.mutxamelcf.model;

/**
 * Cuerpo de PUT /api/app/convocatorias/{id}/jugadores/{jugadorId}/respuesta.
 * respuesta: "VA" o "NO_PUEDE"; el motivo es obligatorio con NO_PUEDE y se
 * ignora con VA.
 */
public class RespuestaConvocatoriaRequest {

    private String respuesta;
    private String motivo;

    public RespuestaConvocatoriaRequest() {
    }

    public RespuestaConvocatoriaRequest(String respuesta, String motivo) {
        this.respuesta = respuesta;
        this.motivo = motivo;
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
}
