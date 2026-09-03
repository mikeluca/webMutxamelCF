package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;

public interface CuotaJugadorService {

    boolean guardarCuota(CuotaJugadorDTO cuota);

    CuotaJugadorDTO obtenerPorId(Long id);

    List<CuotaJugadorDTO> obtenerPorJugador(Long jugadorId);

    List<CuotaJugadorDTO> obtenerPorEstado(String estado);

    List<CuotaJugadorDTO> obtenerTodos();

    void actualizarEstado(Long id);

    void eliminar(Long id);

    void eliminarEnLote(List<Long> ids);
}