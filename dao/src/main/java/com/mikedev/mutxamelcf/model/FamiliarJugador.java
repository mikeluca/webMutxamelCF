package com.mikedev.mutxamelcf.model;

public class FamiliarJugador {

    private Long id;
    private Long familiarId;
    private Long jugadorId;
    private String parentesco;
    private Integer esPrincipal;

    public FamiliarJugador() {
        super();
    }

    public FamiliarJugador(Long id, Long familiarId, Long jugadorId,
                           String parentesco, Integer esPrincipal) {
        super();
        this.id = id;
        this.familiarId = familiarId;
        this.jugadorId = jugadorId;
        this.parentesco = parentesco;
        this.esPrincipal = esPrincipal;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFamiliarId() {
        return familiarId;
    }

    public void setFamiliarId(Long familiarId) {
        this.familiarId = familiarId;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getParentesco() {
        return parentesco;
    }

    public void setParentesco(String parentesco) {
        this.parentesco = parentesco;
    }

    public Integer getEsPrincipal() {
        return esPrincipal;
    }

    public void setEsPrincipal(Integer esPrincipal) {
        this.esPrincipal = esPrincipal;
    }
}