package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuotaJugador;

public interface CuotaJugadorDao {

    boolean guardarCuota(CuotaJugador cuota);

    CuotaJugador obtenerPorId(Long id);

    List<CuotaJugador> obtenerPorJugador(Long jugadorId);

    List<CuotaJugador> obtenerPorEstado(String estado);

    List<CuotaJugador> obtenerTodos();

    // Cuotas cuyo concepto de pago pertenece a la temporada indicada (filtrado
    // en SQL, sin traer las de otras temporadas)
    List<CuotaJugador> obtenerPorTemporada(Long temporadaId);

    void actualizarEstado(Long id);

    void eliminar(Long id);

    void eliminarEnLote(List<Long> ids);
}