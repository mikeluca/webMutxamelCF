package com.mikedev.mutxamelcf.model;

/**
 * Persona del club (jugador, familiar o miembro del cuerpo técnico)
 * candidata a que se le cree una cuenta de la app móvil.
 */
public class PersonaVinculable {

    private Long id;
    private String nombreCompleto;
    private String equipo;
    private String email;

    public PersonaVinculable() {
    }

    public PersonaVinculable(Long id, String nombreCompleto, String equipo, String email) {
        this.id = id;
        this.nombreCompleto = nombreCompleto;
        this.equipo = equipo;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getEquipo() {
        return equipo;
    }

    public void setEquipo(String equipo) {
        this.equipo = equipo;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
