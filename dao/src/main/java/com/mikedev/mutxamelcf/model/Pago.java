package com.mikedev.mutxamelcf.model;

import java.math.BigDecimal;
import java.util.Date;

public class Pago {

    private Long id;
    private Long cuotaJugadorId;
    private BigDecimal importe;
    private Date fechaPago;
    private String metodoPago;
    private String referencia;
    private String observaciones;

    public Pago() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCuotaJugadorId() {
        return cuotaJugadorId;
    }

    public void setCuotaJugadorId(Long cuotaJugadorId) {
        this.cuotaJugadorId = cuotaJugadorId;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public Date getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(Date fechaPago) {
        this.fechaPago = fechaPago;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}