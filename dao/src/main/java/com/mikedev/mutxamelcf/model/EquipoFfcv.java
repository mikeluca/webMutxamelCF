package com.mikedev.mutxamelcf.model;

/**
 * Relación entre una competición de la FFCV (nombre normalizado, ver
 * FfcvCsvParser.normalizarCompeticion) más la letra del Mutxamel en el CSV
 * y un EQUIPO del club.
 */
public class EquipoFfcv {

    private Long id;
    private Long equipoId;
    private String competicion;
    private String letra;

    public EquipoFfcv() {
        super();
    }

    public EquipoFfcv(Long id, Long equipoId, String competicion, String letra) {
        super();
        this.id = id;
        this.equipoId = equipoId;
        this.competicion = competicion;
        this.letra = letra;
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

    public String getLetra() {
        return letra;
    }

    public void setLetra(String letra) {
        this.letra = letra;
    }

}
