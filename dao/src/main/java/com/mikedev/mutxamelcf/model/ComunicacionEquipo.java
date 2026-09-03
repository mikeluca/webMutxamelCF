package com.mikedev.mutxamelcf.model;

public class ComunicacionEquipo {

    private Long comunicacionId;
    private Long equipoId;

    public ComunicacionEquipo() {
    }

    public Long getComunicacionId() {
        return comunicacionId;
    }

    public void setComunicacionId(Long comunicacionId) {
        this.comunicacionId = comunicacionId;
    }

    public Long getEquipoId() {
        return equipoId;
    }

    public void setEquipoId(Long equipoId) {
        this.equipoId = equipoId;
    }
}