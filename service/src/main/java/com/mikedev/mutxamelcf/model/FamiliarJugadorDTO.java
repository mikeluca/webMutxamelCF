package com.mikedev.mutxamelcf.model;

public class FamiliarJugadorDTO {

    private Long id;
    private Long familiarId;
    private Long jugadorId;
    private String parentesco;
    private Integer esPrincipal;

    // Campos enriquecidos para vista/consulta
    private String familiarNombre;
    private String familiarApellidos;
    private String familiarTelefono;
    private String familiarEmail;

    private String jugadorNombre;
    private String jugadorApellidos;
    private String jugadorEquipo;
    private String jugadorCategoria;
    private Integer jugadorDorsal;
    private String jugadorPosicion;

    public FamiliarJugadorDTO() {
        super();
    }

    public FamiliarJugadorDTO(Long id, Long familiarId, Long jugadorId,
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

    public String getFamiliarNombre() {
        return familiarNombre;
    }

    public void setFamiliarNombre(String familiarNombre) {
        this.familiarNombre = familiarNombre;
    }

    public String getFamiliarApellidos() {
        return familiarApellidos;
    }

    public void setFamiliarApellidos(String familiarApellidos) {
        this.familiarApellidos = familiarApellidos;
    }

    public String getFamiliarTelefono() {
        return familiarTelefono;
    }

    public void setFamiliarTelefono(String familiarTelefono) {
        this.familiarTelefono = familiarTelefono;
    }

    public String getFamiliarEmail() {
        return familiarEmail;
    }

    public void setFamiliarEmail(String familiarEmail) {
        this.familiarEmail = familiarEmail;
    }

    public String getJugadorNombre() {
        return jugadorNombre;
    }

    public void setJugadorNombre(String jugadorNombre) {
        this.jugadorNombre = jugadorNombre;
    }

    public String getJugadorApellidos() {
        return jugadorApellidos;
    }

    public void setJugadorApellidos(String jugadorApellidos) {
        this.jugadorApellidos = jugadorApellidos;
    }

    public String getJugadorEquipo() {
        return jugadorEquipo;
    }

    public void setJugadorEquipo(String jugadorEquipo) {
        this.jugadorEquipo = jugadorEquipo;
    }

    public String getJugadorCategoria() {
        return jugadorCategoria;
    }

    public void setJugadorCategoria(String jugadorCategoria) {
        this.jugadorCategoria = jugadorCategoria;
    }

    public Integer getJugadorDorsal() {
        return jugadorDorsal;
    }

    public void setJugadorDorsal(Integer jugadorDorsal) {
        this.jugadorDorsal = jugadorDorsal;
    }

    public String getJugadorPosicion() {
        return jugadorPosicion;
    }

    public void setJugadorPosicion(String jugadorPosicion) {
        this.jugadorPosicion = jugadorPosicion;
    }
}