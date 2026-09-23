package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * Un mensaje dentro del hilo de una conversación privada (chat 1:1).
 */
public class MensajeConversacionResponse {

    private Long id;
    private String contenido;
    private LocalDateTime fecha;
    private Long autorId;
    private boolean esMia;
    private boolean leida;

    public MensajeConversacionResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public Long getAutorId() {
        return autorId;
    }

    public void setAutorId(Long autorId) {
        this.autorId = autorId;
    }

    public boolean isEsMia() {
        return esMia;
    }

    public void setEsMia(boolean esMia) {
        this.esMia = esMia;
    }

    public boolean isLeida() {
        return leida;
    }

    public void setLeida(boolean leida) {
        this.leida = leida;
    }
}
