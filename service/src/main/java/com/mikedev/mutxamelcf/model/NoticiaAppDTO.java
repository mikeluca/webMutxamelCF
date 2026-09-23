package com.mikedev.mutxamelcf.model;

import java.util.Date;

public class NoticiaAppDTO {

    private int id;
    private String titulo;
    private String contenido;
    private Date fecha;
    private String imagenUrl;

    public NoticiaAppDTO() {
        super();
    }

    public NoticiaAppDTO(
            int id,
            String titulo,
            String contenido,
            Date fecha,
            String imagenUrl) {

        super();
        this.id = id;
        this.titulo = titulo;
        this.contenido = contenido;
        this.fecha = fecha;
        this.imagenUrl = imagenUrl;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
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

    public Date getFecha() {
        return fecha;
    }

    public void setFecha(Date fecha) {
        this.fecha = fecha;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }
}