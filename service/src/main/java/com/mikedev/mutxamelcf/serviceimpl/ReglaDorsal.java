package com.mikedev.mutxamelcf.serviceimpl;

import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.model.DorsalRepetidoException;
import com.mikedev.mutxamelcf.model.Jugador;

/**
 * Un dorsal no se puede repetir entre los jugadores activos (sin baja) de un mismo equipo. La comprobación bloquea las
 * filas del equipo hasta el final de la transacción, así que dos cambios a la vez no dejan dorsales repetidos. Se usa
 * desde la app y desde el panel.
 */
final class ReglaDorsal {

    private ReglaDorsal() {
    }

    /**
     * @throws DorsalRepetidoException "El dorsal 7 ya lo tiene Nombre Apellido" si lo tiene otro jugador activo del equipo
     */
    static void comprobar(JugadorDao jugadorDao, Long equipoId, Long jugadorId, Integer dorsal) {

        if (equipoId == null || dorsal == null) {
            return;
        }

        for (Jugador otro : jugadorDao.bloquearActivosDelEquipo(equipoId)) {

            if (dorsal.equals(otro.getDorsal()) && (jugadorId == null || !jugadorId.equals(otro.getId()))) {
                throw new DorsalRepetidoException("El dorsal " + dorsal + " ya lo tiene " + nombreCorto(otro));
            }
        }
    }

    /** Nombre y primer apellido. */
    static String nombreCorto(Jugador jugador) {

        String nombre = jugador.getNombre() == null ? "" : jugador.getNombre().trim();
        String apellidos = jugador.getApellidos() == null ? "" : jugador.getApellidos().trim();
        String primerApellido = apellidos.isEmpty() ? "" : apellidos.split("\s+")[0];

        return (nombre + " " + primerApellido).trim();
    }
}
