package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.util.List;

public class ConvocatoriaResponse {

    private Long id;
    private Long equipoId;
    private String equipo;
    private String rival;
    private String campo;
    private LocalDate fechaPartido;
    private String horaPartido;
    private String horaConvocatoria;
    private String lugarConvocatoria;
    private Long usuarioEntrenadorId;
    private List<ConvocatoriaJugadorResponse> jugadores;

    public ConvocatoriaResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEquipoId() {
        return equipoId;
    }

    public void setEquipoId(Long equipoId) {
        this.equipoId = equipoId;
    }

    public String getEquipo() {
        return equipo;
    }

    public void setEquipo(String equipo) {
        this.equipo = equipo;
    }

    public String getRival() {
        return rival;
    }

    public void setRival(String rival) {
        this.rival = rival;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public LocalDate getFechaPartido() {
        return fechaPartido;
    }

    public void setFechaPartido(LocalDate fechaPartido) {
        this.fechaPartido = fechaPartido;
    }

    public String getHoraPartido() {
        return horaPartido;
    }

    public void setHoraPartido(String horaPartido) {
        this.horaPartido = horaPartido;
    }

    public String getHoraConvocatoria() {
        return horaConvocatoria;
    }

    public void setHoraConvocatoria(String horaConvocatoria) {
        this.horaConvocatoria = horaConvocatoria;
    }

    public String getLugarConvocatoria() {
        return lugarConvocatoria;
    }

    public void setLugarConvocatoria(String lugarConvocatoria) {
        this.lugarConvocatoria = lugarConvocatoria;
    }

    public Long getUsuarioEntrenadorId() {
        return usuarioEntrenadorId;
    }

    public void setUsuarioEntrenadorId(Long usuarioEntrenadorId) {
        this.usuarioEntrenadorId = usuarioEntrenadorId;
    }

    public List<ConvocatoriaJugadorResponse> getJugadores() {
        return jugadores;
    }

    public void setJugadores(
            List<ConvocatoriaJugadorResponse> jugadores) {

        this.jugadores = jugadores;
    }
}