package com.mikedev.mutxamelcf.dao;

import java.math.BigDecimal;
import java.util.List;

import com.mikedev.mutxamelcf.model.Pago;

public interface PagoDao {

    boolean guardarPago(Pago pago);

    Pago obtenerPorId(Long id);

    List<Pago> obtenerPorCuota(Long cuotaJugadorId);

    BigDecimal obtenerTotalPagado(Long cuotaJugadorId);

    void eliminar(Long id);
}