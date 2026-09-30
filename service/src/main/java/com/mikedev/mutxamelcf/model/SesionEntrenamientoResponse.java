package com.mikedev.mutxamelcf.model;

import java.time.LocalDate;

public class SesionEntrenamientoResponse {

    private Long id;
    private Long equipoId;
    private String equipo;
    private Long horarioId;
    private LocalDate fecha;
    private String hora;
    private String lugar;
    private String estado;

    /*
     * Solo se rellenan cuando la consulta se hizo indicando un
     * jugadorId concreto (vista jugador/familiar): indican si ESE
     * jugador ya ha justificado su falta a esta sesión. En el resto de
     * casos (vista de gestión del entrenador) quedan a null/false.
     */
    private boolean justificado;
    private String motivoJustificacion;

    /** Solo relleno cuando estado = "CANCELADA". */
    private String motivoCancelacion;

    /**
     * Estado de asistencia del jugador (PRESENTE/FALTA/FALTA_JUSTIFICADA/
     * TARDANZA...) al ENTRENAMIENTO generado para esta sesión. Solo se
     * rellena, igual que justificado/motivoJustificacion, cuando la
     * consulta se hizo con un jugadorId concreto Y la sesión ya ha
     * pasado (la asistencia se registra durante/después de la sesión,
     * no tiene sentido para una sesión futura).
     */
    private String asistencia;

    public SesionEntrenamientoResponse() {
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

    public Long getHorarioId() {
        return horarioId;
    }

    public void setHorarioId(Long horarioId) {
        this.horarioId = horarioId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public String getHora() {
        return hora;
    }

    public void setHora(String hora) {
        this.hora = hora;
    }

    public String getLugar() {
        return lugar;
    }

    public void setLugar(String lugar) {
        this.lugar = lugar;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public boolean isJustificado() {
        return justificado;
    }

    public void setJustificado(boolean justificado) {
        this.justificado = justificado;
    }

    public String getMotivoJustificacion() {
        return motivoJustificacion;
    }

    public void setMotivoJustificacion(String motivoJustificacion) {
        this.motivoJustificacion = motivoJustificacion;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public void setMotivoCancelacion(String motivoCancelacion) {
        this.motivoCancelacion = motivoCancelacion;
    }

    public String getAsistencia() {
        return asistencia;
    }

    public void setAsistencia(String asistencia) {
        this.asistencia = asistencia;
    }
}
