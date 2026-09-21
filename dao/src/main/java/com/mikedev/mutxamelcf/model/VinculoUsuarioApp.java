package com.mikedev.mutxamelcf.model;

/**
 * Describe a qué persona real del club está vinculada una cuenta
 * de la app móvil (USUARIOS_APP), si es que lo está.
 */
public class VinculoUsuarioApp {

    private String tipo;
    private Long personaId;
    private String nombreCompleto;

    public VinculoUsuarioApp() {
    }

    public VinculoUsuarioApp(String tipo, Long personaId, String nombreCompleto) {
        this.tipo = tipo;
        this.personaId = personaId;
        this.nombreCompleto = nombreCompleto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Long getPersonaId() {
        return personaId;
    }

    public void setPersonaId(Long personaId) {
        this.personaId = personaId;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }
}
