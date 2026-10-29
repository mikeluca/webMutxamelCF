package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuotaJugador;

public interface CuotaJugadorDao {

    boolean guardarCuota(CuotaJugador cuota);

    CuotaJugador obtenerPorId(Long id);

    // BE-01: bloquea la fila (SELECT ... FOR UPDATE) dentro de una
    // transaccion para serializar los registros de pago concurrentes
    // sobre la misma cuota y evitar el sobrepago por carrera.
    CuotaJugador bloquearPorId(Long id);

    List<CuotaJugador> obtenerPorJugador(Long jugadorId);

    List<CuotaJugador> obtenerPorEstado(String estado);

    List<CuotaJugador> obtenerTodos();

    // Cuotas cuyo concepto de pago pertenece a la temporada indicada (filtrado
    // en SQL, sin traer las de otras temporadas)
    List<CuotaJugador> obtenerPorTemporada(Long temporadaId);

    void actualizarEstado(Long id);

    // Morosidad: las cuotas de una temporada (por su concepto) que no estan pagadas -o todas, con
    // incluirPagadas-, con el nombre del jugador, su equipo de esa temporada (JUGADOR_TEMPORADA) y lo pagado.
    List<com.mikedev.mutxamelcf.model.CuotaMorosidad> listarParaMorosidad(Long temporadaId, boolean incluirPagadas);

    // PENDIENTE o PARCIAL -> INCOBRABLE. Devuelve las filas cambiadas (0 si la cuota no existe, esta pagada
    // o ya era incobrable).
    int marcarIncobrable(Long id);

    // INCOBRABLE -> el estado que le toca por sus pagos (PENDIENTE, PARCIAL o PAGADO). Devuelve las filas
    // cambiadas (0 si no era incobrable).
    int deshacerIncobrable(Long id);

    void eliminar(Long id);

    void eliminarEnLote(List<Long> ids);
}