package com.mikedev.mutxamelcf.serviceimpl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mikedev.mutxamelcf.model.PartidoLiveEvento;

/**
 * Lectura de las tarjetas de los eventos de un partido. La doble amarilla no se guarda aparte: es la segunda
 * TARJETA_AMARILLA no anulada del mismo jugador (expulsión), así que deshacer la segunda la quita sin más.
 */
final class TarjetasEventos {

    private TarjetasEventos() {
    }

    /** Ids de las TARJETA_AMARILLA que son la segunda del jugador (expulsión por doble amarilla). */
    static Set<Long> dobles(List<PartidoLiveEvento> eventosEnOrden) {

        Map<Long, Integer> amarillas = new HashMap<>();
        Set<Long> dobles = new HashSet<>();

        for (PartidoLiveEvento evento : eventosEnOrden) {

            if (PartidoLiveEvento.TARJETA_AMARILLA.equals(evento.getTipo())
                    && amarillas.merge(evento.getJugadorId(), 1, Integer::sum) == 2) {
                dobles.add(evento.getId());
            }
        }

        return dobles;
    }

    /** Jugadores expulsados: los de una roja directa y los de una doble amarilla. */
    static Set<Long> expulsados(List<PartidoLiveEvento> eventosEnOrden) {

        Set<Long> dobles = dobles(eventosEnOrden);
        Set<Long> expulsados = new HashSet<>();

        for (PartidoLiveEvento evento : eventosEnOrden) {

            if (PartidoLiveEvento.TARJETA_ROJA.equals(evento.getTipo()) || dobles.contains(evento.getId())) {
                expulsados.add(evento.getJugadorId());
            }
        }

        return expulsados;
    }

    static boolean esTarjeta(String tipo) {
        return PartidoLiveEvento.TARJETA_AMARILLA.equals(tipo) || PartidoLiveEvento.TARJETA_ROJA.equals(tipo);
    }
}
