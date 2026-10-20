package com.mikedev.mutxamelcf.model;

public class ConvocatoriaJugadorResponse {

    private Long jugadorId;
    private String jugador;

    /** true si el jugador ha justificado su ausencia a este partido (sigue en la convocatoria hasta que el entrenador lo sustituya). */
    private boolean ausenciaJustificada;
    private String motivoAusencia;

    public ConvocatoriaJugadorResponse() {
    }

    public ConvocatoriaJugadorResponse(
            Long jugadorId,
            String jugador) {

        this.jugadorId = jugadorId;
        this.jugador = jugador;
    }

    public ConvocatoriaJugadorResponse(
            Long jugadorId,
            String jugador,
            boolean ausenciaJustificada,
            String motivoAusencia) {

        this(jugadorId, jugador);
        this.ausenciaJustificada = ausenciaJustificada;
        this.motivoAusencia = motivoAusencia;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getJugador() {
        return jugador;
    }

    public void setJugador(String jugador) {
        this.jugador = jugador;
    }

    public boolean isAusenciaJustificada() {
        return ausenciaJustificada;
    }

    public void setAusenciaJustificada(boolean ausenciaJustificada) {
        this.ausenciaJustificada = ausenciaJustificada;
    }

    public String getMotivoAusencia() {
        return motivoAusencia;
    }

    public void setMotivoAusencia(String motivoAusencia) {
        this.motivoAusencia = motivoAusencia;
    }
}
