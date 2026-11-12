package com.mikedev.mutxamelcf.dao;

import java.util.Collection;
import java.util.List;

import com.mikedev.mutxamelcf.model.AlineacionJugador;
import com.mikedev.mutxamelcf.model.JugadorFicha;

/** Alineación guardada de un partido (PARTIDO_ALINEACION y PARTIDOS.FORMACION). */
public interface PartidoAlineacionDao {

    /** Titulares primero y luego suplentes, cada grupo por orden de elección. Vacía si no hay alineación. */
    List<AlineacionJugador> obtener(Long partidoId);

    /** La formación guardada ("4-4-2"), o null. */
    String obtenerFormacion(Long partidoId);

    /**
     * Sustituye la alineación del partido (y su formación, que puede ser null) por la nueva. Los ids van en orden de
     * elección.
     */
    void reemplazar(Long partidoId, String formacion, List<Long> titulares, List<Long> suplentes);

    /**
     * Jugadores de la ficha pública en una sola consulta. La foto solo se lee de quien es mayor de edad:
     * ANIO_NACIMIENTO &lt;= {@code anioMayorEdad}, o sin año pero con EQUIPO_ID = {@code primerEquipoId}. Para el
     * resto va null (el año de nacimiento nunca sale de aquí).
     */
    List<JugadorFicha> obtenerJugadoresFicha(Collection<Long> jugadorIds, int anioMayorEdad, Long primerEquipoId);
}
