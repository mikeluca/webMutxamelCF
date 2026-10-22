package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Invitación desde la app (ADMIN_APP): los mismos datos que la del panel web
 * ({@link InvitarUsuarioAppRequest}) pero con los roles que no salen de un vínculo
 * (COORDINADOR, RETRANSMISION) aparte, en {@code roles}.
 */
public class InvitacionAppRequest {

    private String email;
    private String nombre;
    private String apellidos;

    /** Personas del club a vincular: tipo JUGADOR, FAMILIAR o ENTRENADOR + personaId. */
    private List<VinculoSolicitado> vinculos;

    /** Roles sin persona: COORDINADOR, RETRANSMISION... */
    private List<String> roles;

    public InvitacionAppRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public List<VinculoSolicitado> getVinculos() {
        return vinculos;
    }

    public void setVinculos(List<VinculoSolicitado> vinculos) {
        this.vinculos = vinculos;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
