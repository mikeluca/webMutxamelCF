package com.mikedev.mutxamelcf.dao;

import java.math.BigDecimal;
import java.util.List;

import com.mikedev.mutxamelcf.model.Pago;

public interface PagoDao {

    boolean guardarPago(Pago pago);

    Pago obtenerPorId(Long id);

    List<Pago> obtenerPorCuota(Long cuotaJugadorId);

    // Trae en una sola tanda (o pocas, si hay muchos ids) los pagos de varias
    // cuotas, para evitar una consulta por cuota
    List<Pago> obtenerPorCuotas(List<Long> cuotaJugadorIds);

    BigDecimal obtenerTotalPagado(Long cuotaJugadorId);

    void eliminar(Long id);
}