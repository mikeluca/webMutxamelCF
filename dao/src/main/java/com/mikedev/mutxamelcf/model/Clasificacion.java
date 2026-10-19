package com.mikedev.mutxamelcf.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una fila de la tabla de clasificación de un grupo de la FFCV. EQUIPO_ID es
 * el equipo del club dueño de la tabla (no el equipo de la fila); la fila del
 * propio club lleva {@code esNuestro}.
 */
public class Clasificacion implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long equipoId;
    private String competicion;
    private Integer jornada;
    private int posicion;
    private String nombreEquipo;
    private String letra;
    private boolean esNuestro;
    private int puntos;
    private int pj;
    private int pg;
    private int pe;
    private int pp;
    private int gf;
    private int gc;
    private LocalDate fechaUltPartido;
    private LocalDateTime fechaImportacion;

    public Clasificacion() {
        super();
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

    public String getCompeticion() {
        return competicion;
    }

    public void setCompeticion(String competicion) {
        this.competicion = competicion;
    }

    public Integer getJornada() {
        return jornada;
    }

    public void setJornada(Integer jornada) {
        this.jornada = jornada;
    }

    public int getPosicion() {
        return posicion;
    }

    public void setPosicion(int posicion) {
        this.posicion = posicion;
    }

    public String getNombreEquipo() {
        return nombreEquipo;
    }

    public void setNombreEquipo(String nombreEquipo) {
        this.nombreEquipo = nombreEquipo;
    }

    public String getLetra() {
        return letra;
    }

    public void setLetra(String letra) {
        this.letra = letra;
    }

    public boolean isEsNuestro() {
        return esNuestro;
    }

    public void setEsNuestro(boolean esNuestro) {
        this.esNuestro = esNuestro;
    }

    public int getPuntos() {
        return puntos;
    }

    public void setPuntos(int puntos) {
        this.puntos = puntos;
    }

    public int getPj() {
        return pj;
    }

    public void setPj(int pj) {
        this.pj = pj;
    }

    public int getPg() {
        return pg;
    }

    public void setPg(int pg) {
        this.pg = pg;
    }

    public int getPe() {
        return pe;
    }

    public void setPe(int pe) {
        this.pe = pe;
    }

    public int getPp() {
        return pp;
    }

    public void setPp(int pp) {
        this.pp = pp;
    }

    public int getGf() {
        return gf;
    }

    public void setGf(int gf) {
        this.gf = gf;
    }

    public int getGc() {
        return gc;
    }

    public void setGc(int gc) {
        this.gc = gc;
    }

    public LocalDate getFechaUltPartido() {
        return fechaUltPartido;
    }

    public void setFechaUltPartido(LocalDate fechaUltPartido) {
        this.fechaUltPartido = fechaUltPartido;
    }

    public LocalDateTime getFechaImportacion() {
        return fechaImportacion;
    }

    public void setFechaImportacion(LocalDateTime fechaImportacion) {
        this.fechaImportacion = fechaImportacion;
    }

}
