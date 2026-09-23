package com.mikedev.mutxamelcf.model;

public class ComunicacionCategoria {

    private Long comunicacionId;
    private String categoria;

    public ComunicacionCategoria() {
    }

    public Long getComunicacionId() {
        return comunicacionId;
    }

    public void setComunicacionId(Long comunicacionId) {
        this.comunicacionId = comunicacionId;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }
}