package com.mikedev.mutxamelcf.model;

import java.sql.Timestamp;
import java.util.List;

/**
 * Fila del listado de cuentas de la app móvil para el panel de
 * administración web (OFICINA/SUPER).
 */
public class UsuarioAppAdminResponse {

    private int id;
    private String email;
    private boolean activo;
    private Timestamp fechaAlta;
    private Timestamp fechaActivacion;
    private Timestamp fechaUltimoAcceso;
    private boolean tokenPendiente;
    private boolean tokenExpirado;
    private List<String> roles;
    private String vinculoTipo;
    private String vinculoNombre;

    public UsuarioAppAdminResponse() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Timestamp getFechaAlta() {
        return fechaAlta;
    }

    public void setFechaAlta(Timestamp fechaAlta) {
        this.fechaAlta = fechaAlta;
    }

    public Timestamp getFechaActivacion() {
        return fechaActivacion;
    }

    public void setFechaActivacion(Timestamp fechaActivacion) {
        this.fechaActivacion = fechaActivacion;
    }

    public Timestamp getFechaUltimoAcceso() {
        return fechaUltimoAcceso;
    }

    public void setFechaUltimoAcceso(Timestamp fechaUltimoAcceso) {
        this.fechaUltimoAcceso = fechaUltimoAcceso;
    }

    public boolean isTokenPendiente() {
        return tokenPendiente;
    }

    public void setTokenPendiente(boolean tokenPendiente) {
        this.tokenPendiente = tokenPendiente;
    }

    public boolean isTokenExpirado() {
        return tokenExpirado;
    }

    public void setTokenExpirado(boolean tokenExpirado) {
        this.tokenExpirado = tokenExpirado;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getVinculoTipo() {
        return vinculoTipo;
    }

    public void setVinculoTipo(String vinculoTipo) {
        this.vinculoTipo = vinculoTipo;
    }

    public String getVinculoNombre() {
        return vinculoNombre;
    }

    public void setVinculoNombre(String vinculoNombre) {
        this.vinculoNombre = vinculoNombre;
    }
}
