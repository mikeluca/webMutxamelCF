package com.mikedev.mutxamelcf.service;

import java.math.BigDecimal;
import java.util.List;

import com.mikedev.mutxamelcf.model.PagoDTO;

public interface PagoService {

    boolean guardarPago(PagoDTO pago);

    /**
     * BE-01: registra (o edita, si datos.getId() no es nulo) un pago de
     * forma atomica -- bloquea la cuota, valida que el importe no supere
     * el pendiente y actualiza el estado de la cuota, todo en la misma
     * transaccion. Sustituye a la validacion que antes hacia
     * PagosController leyendo el pendiente ANTES de insertar, lo que
     * permitia sobrepago con dos peticiones simultaneas.
     *
     * @throws IllegalArgumentException si la cuota o el pago no existen,
     *                                   el pago no pertenece a la cuota
     *                                   indicada, o el importe no es
     *                                   valido.
     */
    PagoDTO registrarPago(PagoDTO datos);

    PagoDTO obtenerPorId(Long id);

    List<PagoDTO> obtenerPorCuota(Long cuotaJugadorId);

    List<PagoDTO> obtenerPorCuotas(List<Long> cuotaJugadorIds);

    BigDecimal obtenerTotalPagado(Long cuotaJugadorId);

    void eliminar(Long id);
}