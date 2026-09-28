package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Respuesta combinada del calendario de un equipo: sesiones de
 * entrenamiento y partidos dentro de un mismo rango de fechas, para
 * que la app pueda pintar un mes completo con una sola llamada.
 */
public class CalendarioResponse {

    private List<SesionEntrenamientoResponse> sesiones;
    private List<PartidoDTO> partidos;

    public CalendarioResponse() {
    }

    public CalendarioResponse(List<SesionEntrenamientoResponse> sesiones, List<PartidoDTO> partidos) {
        this.sesiones = sesiones;
        this.partidos = partidos;
    }

    public List<SesionEntrenamientoResponse> getSesiones() {
        return sesiones;
    }

    public void setSesiones(List<SesionEntrenamientoResponse> sesiones) {
        this.sesiones = sesiones;
    }

    public List<PartidoDTO> getPartidos() {
        return partidos;
    }

    public void setPartidos(List<PartidoDTO> partidos) {
        this.partidos = partidos;
    }
}
