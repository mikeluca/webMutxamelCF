package com.mikedev.mutxamelcf.dao;

import java.util.List;

public interface EquipoGestionDao {

    boolean existeEquipo(Long equipoId);

    boolean puedeGestionarEquipo(Long usuarioAppId, Long equipoId);

    List<Long> obtenerJugadoresPorEquipo(Long equipoId);

    String obtenerNombreEquipo(Long equipoId);

    String obtenerNombreJugador(Long jugadorId);

    List<Long> obtenerCoordinadores();

    /**
     * Usuarios app con rol ENTRENADOR asignados como cuerpo técnico del
     * equipo indicado (mismo criterio de emparejamiento
     * CUERPO_TECNICO.EQUIPO = EQUIPO.NOMBRE, normalizado con
     * UPPER/TRIM, que {@link #puedeGestionarEquipo(Long, Long)}).
     */
    List<Long> obtenerEntrenadoresPorEquipo(Long equipoId);

    List<Long> obtenerUsuariosPorJugador(Long jugadorId);

    List<Long> obtenerUsuariosFamiliaresPorJugador(Long jugadorId);

    boolean perteneceJugadorAEquipo(Long jugadorId, Long equipoId);
}