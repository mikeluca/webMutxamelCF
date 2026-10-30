package com.mikedev.mutxamelcf.model;

/** El motivo que acompaña a rechazar (opcional) o desestimar (obligatorio) una invitación. */
public class MotivoInvitacionRequest {

    private String motivo;

    public MotivoInvitacionRequest() {
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
