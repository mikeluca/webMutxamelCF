package com.mikedev.mutxamelcf.serviceimpl;

import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.model.DorsalRepetidoException;
import com.mikedev.mutxamelcf.model.Jugador;

/**
 * Un dorsal no se puede repetir entre los jugadores activos (sin baja) de un mismo equipo. Solo se comprueba cuando el
 * dorsal es nuevo para el jugador (cambia de dorsal o de equipo): así un jugador cuyo dorsal ya estaba repetido por datos
 * antiguos puede seguir guardando otros cambios (foto, año, posición...) hasta que se corrija. Cuando se comprueba, se
 * bloquean las filas del equipo hasta el final de la transacción, así que dos cambios a la vez no dejan dorsales
 * repetidos. Se usa desde la app y desde el panel.
 */
final class ReglaDorsal {

    private ReglaDorsal() {
    }

    /**
     * @param actual el jugador tal como está guardado (null si es nuevo)
     * @throws DorsalRepetidoException "El dorsal 7 ya lo tiene Nombre Apellido" si lo tiene otro jugador activo del equipo
     */
    static void comprobar(JugadorDao jugadorDao, Long equipoId, Long jugadorId, Integer dorsal, Jugador actual) {

        if (equipoId == null || dorsal == null) {
            return;
        }

        // Ni el dorsal ni el equipo cambian: lo que ya había se respeta, aunque estuviera repetido.
        if (actual != null && dorsal.equals(actual.getDorsal()) && equipoId.equals(actual.getEquipoId())) {
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
