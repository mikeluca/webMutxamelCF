package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

public class Comunicacion {

    private Long id;
    private String titulo;
    private String contenido;
    private Long usuarioAutorId;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaPublicacion;
    private Integer activa;
    private String tipo;

    /**
     * Id de la otra persona de la conversación privada, desde el punto
     * de vista del usuario consultado. Solo se rellena en las
     * consultas de conversaciones/resumen de chats privados; en el
     * resto de consultas queda null.
     */
    private Long contraparteId;

    public Comunicacion() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public Long getUsuarioAutorId() {
        return usuarioAutorId;
    }

    public void setUsuarioAutorId(Long usuarioAutorId) {
        this.usuarioAutorId = usuarioAutorId;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaPublicacion() {
        return fechaPublicacion;
    }

    public void setFechaPublicacion(LocalDateTime fechaPublicacion) {
        this.fechaPublicacion = fechaPublicacion;
    }

    public Integer getActiva() {
        return activa;
    }

    public void setActiva(Integer activa) {
        this.activa = activa;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Long getContraparteId() {
        return contraparteId;
    }

    public void setContraparteId(Long contraparteId) {
        this.contraparteId = contraparteId;
    }
}