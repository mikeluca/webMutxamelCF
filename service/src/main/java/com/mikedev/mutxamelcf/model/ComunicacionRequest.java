package com.mikedev.mutxamelcf.model;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public class ComunicacionRequest {

    @NotBlank(message = "El título es obligatorio")
    private String titulo;

    @NotBlank(message = "El contenido es obligatorio")
    private String contenido;

    private List<Long> equipoIds;

    private List<String> categorias;

    private List<Long> destinatariosIds;

    public ComunicacionRequest() {
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

    public List<Long> getEquipoIds() {
        return equipoIds;
    }

    public void setEquipoIds(
            List<Long> equipoIds) {

        this.equipoIds = equipoIds;
    }

    public List<String> getCategorias() {
        return categorias;
    }

    public List<Long> getDestinatariosIds() {
        return destinatariosIds;
    }

    public void setCategorias(
            List<String> categorias) {

        this.categorias = categorias;
    }

    public void setDestinatariosIds(
            List<Long> destinatariosIds) {

        this.destinatariosIds = destinatariosIds;
    }
}