package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * Fila del listado de comunicaciones para la app móvil. Representa
 * tanto avisos grupales (equipo/categoría, tipo GRUPAL) como la
 * entrada-resumen de una conversación privada (tipo PRIVADA, una fila
 * por contraparte con el último mensaje y el número de no leídos).
 */
public class ComunicacionResponse {

    private Long id;
    private String tipo;
    private String titulo;
    private String contenido;
    private LocalDateTime fecha;
    private Long autorId;
    private String autorNombre;
    private String autorRol;
    private boolean leida;
    private Long contraparteId;
    private String contraparteNombre;
    private String contraparteRol;
    private int noLeidos;

    public ComunicacionResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public Long getAutorId() {
        return autorId;
    }

    public void setAutorId(Long autorId) {
        this.autorId = autorId;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public void setAutorNombre(String autorNombre) {
        this.autorNombre = autorNombre;
    }

    public String getAutorRol() {
        return autorRol;
    }

    public void setAutorRol(String autorRol) {
        this.autorRol = autorRol;
    }

    public boolean isLeida() {
        return leida;
    }

    public void setLeida(boolean leida) {
        this.leida = leida;
    }

    public Long getContraparteId() {
        return contraparteId;
    }

    public void setContraparteId(Long contraparteId) {
        this.contraparteId = contraparteId;
    }

    public String getContraparteNombre() {
        return contraparteNombre;
    }

    public void setContraparteNombre(String contraparteNombre) {
        this.contraparteNombre = contraparteNombre;
    }

    public String getContraparteRol() {
        return contraparteRol;
    }

    public void setContraparteRol(String contraparteRol) {
        this.contraparteRol = contraparteRol;
    }

    public int getNoLeidos() {
        return noLeidos;
    }

    public void setNoLeidos(int noLeidos) {
        this.noLeidos = noLeidos;
    }
}
