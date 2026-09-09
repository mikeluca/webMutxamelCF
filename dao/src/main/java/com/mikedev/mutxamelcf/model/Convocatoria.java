package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Convocatoria {

    private Long id;
    private Long equipoId;

    private String rival;
    private String campo;

    private LocalDate fechaPartido;
    private String horaPartido;

    private String horaConvocatoria;
    private String lugarConvocatoria;

    private Long usuarioEntrenadorId;
    private LocalDateTime fechaCreacion;

    public Convocatoria() {
    }

    public Convocatoria(
            Long id,
            Long equipoId,
            String rival,
            String campo,
            LocalDate fechaPartido,
            String horaPartido,
            String horaConvocatoria,
            String lugarConvocatoria,
            Long usuarioEntrenadorId,
            LocalDateTime fechaCreacion) {

        this.id = id;
        this.equipoId = equipoId;
        this.rival = rival;
        this.campo = campo;
        this.fechaPartido = fechaPartido;
        this.horaPartido = horaPartido;
        this.horaConvocatoria = horaConvocatoria;
        this.lugarConvocatoria = lugarConvocatoria;
        this.usuarioEntrenadorId = usuarioEntrenadorId;
        this.fechaCreacion = fechaCreacion;
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

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}