package com.mikedev.mutxamelcf.model;

public class PreferenciasNotificacionResponse {

    private Long usuarioAppId;

    private boolean notificacionesActivadas;
    private boolean noticiasActivadas;
    private boolean comunicacionesActivadas;
    private boolean mensajesActivados;
    private boolean resultadosActivados;

    public PreferenciasNotificacionResponse() {
    }

    public Long getUsuarioAppId() {
        return usuarioAppId;
    }

    public void setUsuarioAppId(Long usuarioAppId) {
        this.usuarioAppId = usuarioAppId;
    }

    public boolean isNotificacionesActivadas() {
        return notificacionesActivadas;
    }

    public void setNotificacionesActivadas(
            boolean notificacionesActivadas) {
        this.notificacionesActivadas = notificacionesActivadas;
    }

    public boolean isNoticiasActivadas() {
        return noticiasActivadas;
    }

    public void setNoticiasActivadas(
            boolean noticiasActivadas) {
        this.noticiasActivadas = noticiasActivadas;
    }

    public boolean isComunicacionesActivadas() {
        return comunicacionesActivadas;
    }

    public void setComunicacionesActivadas(
            boolean comunicacionesActivadas) {
        this.comunicacionesActivadas = comunicacionesActivadas;
    }

    public boolean isMensajesActivados() {
        return mensajesActivados;
    }

    public void setMensajesActivados(
            boolean mensajesActivados) {
        this.mensajesActivados = mensajesActivados;
    }

    public boolean isResultadosActivados() {
        return resultadosActivados;
    }

    public void setResultadosActivados(
            boolean resultadosActivados) {
        this.resultadosActivados = resultadosActivados;
    }
}