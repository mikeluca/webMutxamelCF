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

    /** Nombre propio de la cuenta (solo se usa si no tiene ficha vinculada). */
    private String nombre;
    private String apellidos;
    private boolean activo;
    private Timestamp fechaAlta;
    private Timestamp fechaActivacion;
    private Timestamp fechaUltimoAcceso;
    private boolean tokenPendiente;
    private boolean tokenExpirado;
    private List<String> roles;

    /**
     * Todos los vínculos a persona que tiene la cuenta hoy (puede ser
     * más de uno: jugador y familiar a la vez, entrenador de varios
     * equipos...). Lista vacía si no tiene ninguno (p. ej. una cuenta
     * solo con rol COORDINADOR).
     */
    private List<VinculoUsuarioApp> vinculos;

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

    /**
     * Nombre propio + apellidos de la cuenta, o null si no tiene nombre.
     */
    public String getNombreCuenta() {
        String completo = ((nombre == null ? "" : nombre) + " " + (apellidos == null ? "" : apellidos)).trim();
        return completo.isEmpty() ? null : completo;
    }

    /**
     * La cuenta tiene una ficha vinculada (jugador, familiar o entrenador):
     * su nombre sale de ella y no se pide ni se edita aquí.
     */
    public boolean isTieneFicha() {
        return vinculos != null && !vinculos.isEmpty();
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

    public List<VinculoUsuarioApp> getVinculos() {
        return vinculos;
    }

    public void setVinculos(List<VinculoUsuarioApp> vinculos) {
        this.vinculos = vinculos;
    }

    /*
     * Usados por la vista de administración para deshabilitar, al editar
     * una cuenta, las casillas de los tipos que ya tiene (Jugador/Familiar/
     * Coordinador son 1:1 por cuenta; Entrenador no, admite varios equipos).
     */

    public boolean isTieneJugador() {
        return vinculos != null && vinculos.stream().anyMatch(v -> "JUGADOR".equals(v.getTipo()));
    }

    public boolean isTieneFamiliar() {
        return vinculos != null && vinculos.stream().anyMatch(v -> "FAMILIAR".equals(v.getTipo()));
    }

    public boolean isTieneCoordinador() {
        return roles != null && roles.contains("COORDINADOR");
    }

    public boolean isTieneRetransmision() {
        return roles != null && roles.contains("RETRANSMISION");
    }
}
