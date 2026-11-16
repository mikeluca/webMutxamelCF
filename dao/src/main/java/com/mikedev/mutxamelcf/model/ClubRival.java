package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * Un club rival (no un equipo: el Alicante A, el B y el infantil son el mismo club y comparten escudo). El escudo en sí
 * (BLOB) no está aquí: las consultas de lista nunca lo leen, solo si hay y su versión.
 */
public class ClubRival {

    private Long id;
    private String nombre;
    private Integer codigoFfcv;
    private boolean tieneEscudo;
    private long escudoVersion;
    private LocalDateTime fechaActualizacion;
    private LocalDateTime fechaCreacion;

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

    /** Código de club de la FFCV; null si el club solo se conoce por nombre. */
    public Integer getCodigoFfcv() {
        return codigoFfcv;
    }

    public void setCodigoFfcv(Integer codigoFfcv) {
        this.codigoFfcv = codigoFfcv;
    }

    public boolean isTieneEscudo() {
        return tieneEscudo;
    }

    public void setTieneEscudo(boolean tieneEscudo) {
        this.tieneEscudo = tieneEscudo;
    }

    /** Sube cada vez que el escudo cambia o se quita. */
    public long getEscudoVersion() {
        return escudoVersion;
    }

    public void setEscudoVersion(long escudoVersion) {
        this.escudoVersion = escudoVersion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
