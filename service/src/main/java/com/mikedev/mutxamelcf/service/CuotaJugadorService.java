package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;

public interface CuotaJugadorService {

    boolean guardarCuota(CuotaJugadorDTO cuota);

    CuotaJugadorDTO obtenerPorId(Long id);

    List<CuotaJugadorDTO> obtenerPorJugador(Long jugadorId);

    List<CuotaJugadorDTO> obtenerPorEstado(String estado);

    List<CuotaJugadorDTO> obtenerTodos();

    List<CuotaJugadorDTO> obtenerPorTemporada(Long temporadaId);

    void actualizarEstado(Long id);

    /** Cuotas de una temporada con jugador, equipo de esa temporada y lo pagado (Morosidad). */
    List<com.mikedev.mutxamelcf.model.CuotaMorosidad> listarParaMorosidad(Long temporadaId, boolean incluirPagadas);

    /** PENDIENTE o PARCIAL -> INCOBRABLE; devuelve las filas cambiadas. */
    int marcarIncobrable(Long id);

    /** INCOBRABLE -> el estado que le toca por sus pagos; devuelve las filas cambiadas. */
    int deshacerIncobrable(Long id);

    void eliminar(Long id);

    void eliminarEnLote(List<Long> ids);
}