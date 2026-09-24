package com.mikedev.mutxamelcf.model;

import java.sql.Timestamp;
import java.util.Date;

public class Partido {

    private Long id;
    private Long equipoId;
    private String rival;
    private Date dia;
    private String hora;
    private String campo;
    private String resultado;
    private String tipo;
    private Long usuarioActualizoId;
    private Timestamp fechaActualizacion;

    public Partido() {
        super();
    }

    public Partido(
            Long id,
            Long equipoId,
            String rival,
            Date dia,
            String hora,
            String campo,
            String resultado,
            String tipo,
            Long usuarioActualizoId,
            Timestamp fechaActualizacion) {

        super();
        this.id = id;
        this.equipoId = equipoId;
        this.rival = rival;
        this.dia = dia;
        this.hora = hora;
        this.campo = campo;
        this.resultado = resultado;
        this.tipo = tipo;
        this.usuarioActualizoId = usuarioActualizoId;
        this.fechaActualizacion = fechaActualizacion;
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

    public Date getDia() {
        return dia;
    }

    public void setDia(Date dia) {
        this.dia = dia;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public String getResultado() {
        return resultado;
    }

    public void setResultado(String resultado) {
        this.resultado = resultado;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Long getUsuarioActualizoId() {
        return usuarioActualizoId;
    }

    public void setUsuarioActualizoId(Long usuarioActualizoId) {
        this.usuarioActualizoId = usuarioActualizoId;
    }

    public Timestamp getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(Timestamp fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

}
