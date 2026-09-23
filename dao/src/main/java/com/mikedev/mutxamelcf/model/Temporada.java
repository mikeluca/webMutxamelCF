package com.mikedev.mutxamelcf.model;

import java.util.Date;

public class Temporada {

    private Long id;
    private String nombre;
    private Date fechaInicio;
    private Date fechaFin;
    private Integer activa;

    public Temporada() {
    }

    public Temporada(Long id, String nombre, Date fechaInicio, Date fechaFin, Integer activa) {
        this.id = id;
        this.nombre = nombre;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.activa = activa;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Date getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(Date fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public Date getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(Date fechaFin) {
        this.fechaFin = fechaFin;
    }

    public Integer getActiva() {
        return activa;
    }

    public void setActiva(Integer activa) {
        this.activa = activa;
    }
}