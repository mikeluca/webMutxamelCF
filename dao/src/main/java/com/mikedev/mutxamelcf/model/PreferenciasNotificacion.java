package com.mikedev.mutxamelcf.model;

public class PreferenciasNotificacion {

    private Long id;
    private Long usuarioAppId;

    private Integer notificacionesActivadas;
    private Integer noticiasActivadas;
    private Integer comunicacionesActivadas;
    private Integer mensajesActivados;
    private Integer resultadosActivados;

    public PreferenciasNotificacion() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUsuarioAppId() {
        return usuarioAppId;
    }

    public void setUsuarioAppId(Long usuarioAppId) {
        this.usuarioAppId = usuarioAppId;
    }

    public Integer getNotificacionesActivadas() {
        return notificacionesActivadas;
    }

    public void setNotificacionesActivadas(Integer notificacionesActivadas) {
        this.notificacionesActivadas = notificacionesActivadas;
    }

    public Integer getNoticiasActivadas() {
        return noticiasActivadas;
    }

    public void setNoticiasActivadas(Integer noticiasActivadas) {
        this.noticiasActivadas = noticiasActivadas;
    }

    public Integer getComunicacionesActivadas() {
        return comunicacionesActivadas;
    }

    public void setComunicacionesActivadas(Integer comunicacionesActivadas) {
        this.comunicacionesActivadas = comunicacionesActivadas;
    }

    public Integer getMensajesActivados() {
        return mensajesActivados;
    }

    public void setMensajesActivados(Integer mensajesActivados) {
        this.mensajesActivados = mensajesActivados;
    }

    public Integer getResultadosActivados() {
        return resultadosActivados;
    }

    public void setResultadosActivados(Integer resultadosActivados) {
        this.resultadosActivados = resultadosActivados;
    }
}