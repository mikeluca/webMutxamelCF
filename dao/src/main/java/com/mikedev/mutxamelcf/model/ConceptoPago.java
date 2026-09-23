package com.mikedev.mutxamelcf.model;

import java.math.BigDecimal;

public class ConceptoPago {

    private Long id;
    private Long temporadaId;
    private String nombre;
    private String descripcion;
    private BigDecimal importe;
    private Integer activo;

    public ConceptoPago() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTemporadaId() {
        return temporadaId;
    }

    public void setTemporadaId(Long temporadaId) {
        this.temporadaId = temporadaId;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public Integer getActivo() {
        return activo;
    }

    public void setActivo(Integer activo) {
        this.activo = activo;
    }
}