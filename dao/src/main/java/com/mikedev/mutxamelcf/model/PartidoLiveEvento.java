package com.mikedev.mutxamelcf.model;

import java.time.LocalDateTime;

/**
 * Un hecho del partido en directo (inicio, gol, descanso...). El marcador y la
 * fase se calculan con los eventos no anulados; deshacer solo marca ANULADO.
 */
public class PartidoLiveEvento {

    public static final String INICIO = "INICIO";
    public static final String GOL_FAVOR = "GOL_FAVOR";
    public static final String GOL_CONTRA = "GOL_CONTRA";
    public static final String DESCANSO = "DESCANSO";
    public static final String SEGUNDA_PARTE = "SEGUNDA_PARTE";
    public static final String FINAL = "FINAL";

    private Long id;
    private Long partidoId;
    private String tipo;
    private Long jugadorId;
    private String autorTexto;
    private Long usuarioAppId;
    private LocalDateTime fecha;
    private boolean anulado;
    private LocalDateTime fechaAnulacion;

    // Solo en un FINAL: lo que había en PARTIDOS antes de guardar el resultado.
    private String resultadoAnterior;
    private Integer golesFavorAnterior;
    private Integer golesContraAnterior;
    private Boolean avisoAnterior;
    private boolean estadisticasGuardadas;

    public PartidoLiveEvento() {
        super();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPartidoId() {
        return partidoId;
    }

    public void setPartidoId(Long partidoId) {
        this.partidoId = partidoId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getAutorTexto() {
        return autorTexto;
    }

    public void setAutorTexto(String autorTexto) {
        this.autorTexto = autorTexto;
    }

    public Long getUsuarioAppId() {
        return usuarioAppId;
    }

    public void setUsuarioAppId(Long usuarioAppId) {
        this.usuarioAppId = usuarioAppId;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public boolean isAnulado() {
        return anulado;
    }

    public void setAnulado(boolean anulado) {
        this.anulado = anulado;
    }

    public LocalDateTime getFechaAnulacion() {
        return fechaAnulacion;
    }

    public void setFechaAnulacion(LocalDateTime fechaAnulacion) {
        this.fechaAnulacion = fechaAnulacion;
    }

    public String getResultadoAnterior() {
        return resultadoAnterior;
    }

    public void setResultadoAnterior(String resultadoAnterior) {
        this.resultadoAnterior = resultadoAnterior;
    }

    public Integer getGolesFavorAnterior() {
        return golesFavorAnterior;
    }

    public void setGolesFavorAnterior(Integer golesFavorAnterior) {
        this.golesFavorAnterior = golesFavorAnterior;
    }

    public Integer getGolesContraAnterior() {
        return golesContraAnterior;
    }

    public void setGolesContraAnterior(Integer golesContraAnterior) {
        this.golesContraAnterior = golesContraAnterior;
    }

    public Boolean getAvisoAnterior() {
        return avisoAnterior;
    }

    public void setAvisoAnterior(Boolean avisoAnterior) {
        this.avisoAnterior = avisoAnterior;
    }

    public boolean isEstadisticasGuardadas() {
        return estadisticasGuardadas;
    }

    public void setEstadisticasGuardadas(boolean estadisticasGuardadas) {
        this.estadisticasGuardadas = estadisticasGuardadas;
    }

}
