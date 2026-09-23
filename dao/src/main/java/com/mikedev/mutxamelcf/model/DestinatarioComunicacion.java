package com.mikedev.mutxamelcf.model;

public class DestinatarioComunicacion {

    private Long id;
    private String nombre;
    private String apellidos;
    private String rol;

    public DestinatarioComunicacion() {
    }

    public DestinatarioComunicacion(
            Long id,
            String nombre,
            String apellidos,
            String rol) {
        this.id = id;
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.rol = rol;
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

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }
}