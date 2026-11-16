package com.mikedev.mutxamelcf.serviceimpl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.mikedev.mutxamelcf.model.PartidoLiveEvento;

/**
 * Lectura de las tarjetas de los eventos de un partido. La doble amarilla no se guarda aparte: es la segunda
 * TARJETA_AMARILLA no anulada del mismo jugador (expulsión), así que deshacer la segunda la quita sin más.
 *
 * <p>Las tarjetas del rival (RIVAL = 1) se agrupan aparte, por DORSAL del rival, y nunca se mezclan con las nuestras
 * (por jugador). Una tarjeta del rival sin dorsal no se puede atribuir a nadie: cada una cuenta suelta. Las del rival
 * no cuentan en estadísticas, sanciones, avisos ni en quién está en el campo: {@link #expulsados} y
 * {@link #amarillasQueCuentan} las ignoran.
 */
final class TarjetasEventos {

    private TarjetasEventos() {
    }

    /**
     * Ids de las TARJETA_AMARILLA que son la segunda del jugador (expulsión por doble amarilla), o la segunda del
     * mismo dorsal del rival.
     */
    static Set<Long> dobles(List<PartidoLiveEvento> eventosEnOrden) {

        Map<Long, Integer> amarillas = new HashMap<>();
        Map<Integer, Integer> amarillasRival = new HashMap<>();
        Set<Long> dobles = new HashSet<>();

        for (PartidoLiveEvento evento : eventosEnOrden) {

            if (!PartidoLiveEvento.TARJETA_AMARILLA.equals(evento.getTipo())) {
                continue;
            }

            if (evento.isRival()) {

                if (evento.getDorsalRival() != null
                        && amarillasRival.merge(evento.getDorsalRival(), 1, Integer::sum) == 2) {
                    dobles.add(evento.getId());
                }

            } else if (amarillas.merge(evento.getJugadorId(), 1, Integer::sum) == 2) {
                dobles.add(evento.getId());
            }
        }

        return dobles;
    }

    /**
     * Amarillas de cada jugador nuestro que cuentan para las estadísticas y la acumulación: todas las suyas salvo las
     * de una expulsión por doble amarilla (si el jugador tiene una, ninguna de sus dos amarillas cuenta; esa expulsión
     * va como roja). Solo salen los jugadores con alguna amarilla que cuente. Ignora al rival.
     */
    static Map<Long, Integer> amarillasQueCuentan(List<PartidoLiveEvento> eventosEnOrden) {

        Set<Long> dobles = dobles(eventosEnOrden);
        Set<Long> expulsadosPorDoble = new HashSet<>();

        for (PartidoLiveEvento evento : eventosEnOrden) {
            if (!evento.isRival() && dobles.contains(evento.getId())) {
                expulsadosPorDoble.add(evento.getJugadorId());
            }
        }

        Map<Long, Integer> amarillas = new HashMap<>();

        for (PartidoLiveEvento evento : eventosEnOrden) {

            if (!evento.isRival() && PartidoLiveEvento.TARJETA_AMARILLA.equals(evento.getTipo())
                    && !expulsadosPorDoble.contains(evento.getJugadorId())) {
                amarillas.merge(evento.getJugadorId(), 1, Integer::sum);
            }
        }

        return amarillas;
    }

    /** Jugadores nuestros expulsados: los de una roja directa y los de una doble amarilla. Ignora al rival. */
    static Set<Long> expulsados(List<PartidoLiveEvento> eventosEnOrden) {

        Set<Long> dobles = dobles(eventosEnOrden);
        Set<Long> expulsados = new HashSet<>();

        for (PartidoLiveEvento evento : eventosEnOrden) {

            if (!evento.isRival()
                    && (PartidoLiveEvento.TARJETA_ROJA.equals(evento.getTipo()) || dobles.contains(evento.getId()))) {
                expulsados.add(evento.getJugadorId());
            }
        }

        return expulsados;
    }

    /**
     * Dorsales del rival con exactamente una amarilla (aún no expulsados por doble amarilla), de menor a mayor: la
     * app avisa de que la siguiente amarilla a ese dorsal es expulsión.
     */
    static List<Integer> dorsalesRivalConAmarilla(List<PartidoLiveEvento> eventosEnOrden) {

        Map<Integer, Integer> amarillas = new HashMap<>();

        for (PartidoLiveEvento evento : eventosEnOrden) {

            if (evento.isRival() && evento.getDorsalRival() != null
                    && PartidoLiveEvento.TARJETA_AMARILLA.equals(evento.getTipo())) {
                amarillas.merge(evento.getDorsalRival(), 1, Integer::sum);
            }
        }

        Set<Integer> conUna = new TreeSet<>();
        amarillas.forEach((dorsal, n) -> {
            if (n == 1) {
                conUna.add(dorsal);
            }
        });

        return List.copyOf(conUna);
    }

    static boolean esTarjeta(String tipo) {
        return PartidoLiveEvento.TARJETA_AMARILLA.equals(tipo) || PartidoLiveEvento.TARJETA_ROJA.equals(tipo);
    }
}
