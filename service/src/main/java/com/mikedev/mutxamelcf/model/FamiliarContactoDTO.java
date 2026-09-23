package com.mikedev.mutxamelcf.model;

public class FamiliarContactoDTO {

    private Long id;
    private String nombre;
    private String apellidos;
    private String telefono;
    private String email;
    private Integer whatsappActivo;
    private String parentesco;
    private Integer esPrincipal;

    public FamiliarContactoDTO() {
    }

    public FamiliarContactoDTO(
            Long id,
            String nombre,
            String apellidos,
            String telefono,
            String email,
            Integer whatsappActivo,
            String parentesco,
            Integer esPrincipal) {

        this.id = id;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.telefono = telefono;
        this.email = email;
        this.whatsappActivo = whatsappActivo;
        this.parentesco = parentesco;
        this.esPrincipal = esPrincipal;
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

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getWhatsappActivo() {
        return whatsappActivo;
    }

    public void setWhatsappActivo(Integer whatsappActivo) {
        this.whatsappActivo = whatsappActivo;
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