package com.mikedev.mutxamelcf.model;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Una cuota de uno de los jugadores vinculados a la cuenta que consulta
 * (jugador propio o hijo/a como familiar), para la pantalla "Cuotas" de
 * la app. {@code vencida} es un cálculo de la app (no un estado
 * guardado en base de datos): pendiente/parcial cuya fecha límite ya
 * ha pasado.
 */
public class CuotaFamiliarResponse {

    private Long id;
    private Long jugadorId;
    private String jugadorNombre;
    private String concepto;
    private String periodo;
    private BigDecimal importe;
    private String estado;
    private Date fechaLimite;
    private String periodoFormateado;
    private boolean vencida;
    private List<PagoCuotaResponse> pagos;

    public CuotaFamiliarResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJugadorId() {
        return jugadorId;
    }

    public void setJugadorId(Long jugadorId) {
        this.jugadorId = jugadorId;
    }

    public String getJugadorNombre() {
        return jugadorNombre;
    }

    public void setJugadorNombre(String jugadorNombre) {
        this.jugadorNombre = jugadorNombre;
    }

    public String getConcepto() {
        return concepto;
    }

    public void setConcepto(String concepto) {
        this.concepto = concepto;
    }

    public String getPeriodo() {
        return periodo;
    }

    public void setPeriodo(String periodo) {
        this.periodo = periodo;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Date getFechaLimite() {
        return fechaLimite;
    }

    public void setFechaLimite(Date fechaLimite) {
        this.fechaLimite = fechaLimite;
    }

    public String getPeriodoFormateado() {
        return periodoFormateado;
    }

    public void setPeriodoFormateado(String periodoFormateado) {
        this.periodoFormateado = periodoFormateado;
    }

    public boolean isVencida() {
        return vencida;
    }

    public void setVencida(boolean vencida) {
        this.vencida = vencida;
    }

    public List<PagoCuotaResponse> getPagos() {
        return pagos;
    }

    public void setPagos(List<PagoCuotaResponse> pagos) {
        this.pagos = pagos;
    }
}
