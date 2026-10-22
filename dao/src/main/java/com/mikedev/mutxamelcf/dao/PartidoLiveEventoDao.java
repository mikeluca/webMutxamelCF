package com.mikedev.mutxamelcf.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import com.mikedev.mutxamelcf.model.JugadorBasico;
import com.mikedev.mutxamelcf.model.PartidoLiveEvento;

public interface PartidoLiveEventoDao {

    /** Inserta el evento (el ID lo genera la base de datos y se devuelve en el propio objeto). */
    PartidoLiveEvento crear(PartidoLiveEvento evento);

    PartidoLiveEvento obtenerPorId(Long id);

    /** Eventos no anulados del partido, del más antiguo al más reciente (empates por ID). */
    List<PartidoLiveEvento> obtenerNoAnuladosPorPartido(Long partidoId);

    /** Marca el evento como anulado; devuelve las filas modificadas (0 si ya lo estaba). */
    int anular(Long id, LocalDateTime fechaAnulacion);

    /** Corrige el minuto de un evento no anulado; devuelve las filas modificadas. */
    int actualizarMinuto(Long id, Integer minuto);

    /** Jugadores (sin foto) con esos ids. */
    List<JugadorBasico> obtenerJugadoresBasicos(Collection<Long> jugadorIds);

    /** Si el jugador figura como goleador en algún evento (anulado o no). */
    boolean existePorJugador(Long jugadorId);
}
