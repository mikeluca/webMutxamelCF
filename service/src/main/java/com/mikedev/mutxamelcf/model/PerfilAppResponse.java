package com.mikedev.mutxamelcf.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class PerfilAppResponse {

    private int usuarioId;
    private String email;
    private List<String> roles;

    private String nombre;
    private String apellidos;
    private String telefono;

    private Timestamp fechaAlta;
    private Timestamp fechaActivacion;
    private Timestamp fechaUltimoAcceso;

    private List<PerfilJugadorAppResponse> jugadores;
    private List<PerfilEquipoAppResponse> equipos;

    public PerfilAppResponse() {
        this.roles = new ArrayList<>();
        this.jugadores = new ArrayList<>();
        this.equipos = new ArrayList<>();
    }

    public int getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(int usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
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

    public List<PerfilJugadorAppResponse> getJugadores() {
        return jugadores;
    }

    public void setJugadores(List<PerfilJugadorAppResponse> jugadores) {
        this.jugadores = jugadores;
    }

    public List<PerfilEquipoAppResponse> getEquipos() {
        return equipos;
    }

    public void setEquipos(List<PerfilEquipoAppResponse> equipos) {
        this.equipos = equipos;
    }
}