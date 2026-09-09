package com.mikedev.mutxamelcf.dao;

import java.util.List;

public interface EquipoGestionDao {

    boolean existeEquipo(Long equipoId);

    boolean puedeGestionarEquipo(Long usuarioAppId, Long equipoId);

    List<Long> obtenerJugadoresPorEquipo(Long equipoId);

    String obtenerNombreEquipo(Long equipoId);

    String obtenerNombreJugador(Long jugadorId);

    List<Long> obtenerCoordinadores();

    List<Long> obtenerUsuariosPorJugador(Long jugadorId);

    List<Long> obtenerUsuariosFamiliaresPorJugador(Long jugadorId);

    boolean perteneceJugadorAEquipo(Long jugadorId, Long equipoId);
}