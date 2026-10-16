package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

/**
 * Un equipo con relación FFCV que no juega el fin de semana importado.
 * {@code fecha} es el primer día de ese fin de semana.
 */
public class DescansoEquipo {

    private Long id;
    private Long equipoId;
    private LocalDate fecha;
    private Integer jornada;

    public DescansoEquipo() {
        super();
    }

    public DescansoEquipo(Long id, Long equipoId, LocalDate fecha, Integer jornada) {
        super();
        this.id = id;
        this.equipoId = equipoId;
        this.fecha = fecha;
        this.jornada = jornada;
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

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Integer getJornada() {
        return jornada;
    }

    public void setJornada(Integer jornada) {
        this.jornada = jornada;
    }

}
