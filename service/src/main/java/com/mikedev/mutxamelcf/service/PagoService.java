package com.mikedev.mutxamelcf.service;

import java.math.BigDecimal;
import java.util.List;

import com.mikedev.mutxamelcf.model.PagoDTO;

public interface PagoService {

    boolean guardarPago(PagoDTO pago);

    PagoDTO obtenerPorId(Long id);

    List<PagoDTO> obtenerPorCuota(Long cuotaJugadorId);

    List<PagoDTO> obtenerPorCuotas(List<Long> cuotaJugadorIds);

    BigDecimal obtenerTotalPagado(Long cuotaJugadorId);

    void eliminar(Long id);
}