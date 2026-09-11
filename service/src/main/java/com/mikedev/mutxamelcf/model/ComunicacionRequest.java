package com.mikedev.mutxamelcf.model;

import java.util.List;

public class ComunicacionRequest {

    private String titulo;

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