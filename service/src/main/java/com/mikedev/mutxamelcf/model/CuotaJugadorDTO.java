package com.mikedev.mutxamelcf.model;

import java.math.BigDecimal;
import java.util.Date;

public class CuotaJugadorDTO {

    private Long id;

    private Long jugadorId;

    private Long conceptoPagoId;

    private String periodo;

    private BigDecimal importe;

    private String estado;

    private Date fechaLimite;

    private String observaciones;

    public CuotaJugadorDTO() {
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

    public Long getConceptoPagoId() {
        return conceptoPagoId;
    }

    public void setConceptoPagoId(Long conceptoPagoId) {
        this.conceptoPagoId = conceptoPagoId;
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

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}