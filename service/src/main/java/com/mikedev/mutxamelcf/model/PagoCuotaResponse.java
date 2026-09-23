package com.mikedev.mutxamelcf.model;

import java.math.BigDecimal;

/**
 * Un pago registrado contra una cuota, para la pantalla "Cuotas" de la
 * app (se muestra al pulsar sobre una cuota que ya tiene algún pago).
 */
public class PagoCuotaResponse {

    private BigDecimal importe;
    private String fechaPagoFormateada;
    private String metodoPago;

    public PagoCuotaResponse() {
    }

    public PagoCuotaResponse(BigDecimal importe, String fechaPagoFormateada, String metodoPago) {
        this.importe = importe;
        this.fechaPagoFormateada = fechaPagoFormateada;
        this.metodoPago = metodoPago;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public String getFechaPagoFormateada() {
        return fechaPagoFormateada;
    }

    public void setFechaPagoFormateada(String fechaPagoFormateada) {
        this.fechaPagoFormateada = fechaPagoFormateada;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }
}
