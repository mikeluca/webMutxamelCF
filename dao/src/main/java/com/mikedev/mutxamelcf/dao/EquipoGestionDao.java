package com.mikedev.mutxamelcf.dao;

import java.util.List;

public interface EquipoGestionDao {

    boolean existeEquipo(Long equipoId);

    boolean puedeGestionarEquipo(Long usuarioAppId, Long equipoId);

    List<Long> obtenerJugadoresPorEquipo(Long equipoId);

    String obtenerNombreEquipo(Long equipoId);

    String obtenerNombreJugador(Long jugadorId);

    List<Long> obtenerCoordinadores();

    /** true si el usuario app tiene el rol COORDINADOR o ADMIN_APP. */
    boolean esCoordinadorOAdmin(Long usuarioAppId);

    /** Usuarios app activos con rol ADMIN_APP, por ID. */
    List<Long> obtenerAdministradoresApp();

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

    /**
     * ID del equipo de un jugador (su campo EQUIPO es el nombre del equipo),
     * o {@code null} si no tiene o no coincide con ninguno.
     */
    Long obtenerEquipoIdDeJugador(Long jugadorId);

    /**
     * true si el equipo registra estadísticas individuales de jugador
     * ({@link com.mikedev.mutxamelcf.model.Equipo#admiteEstadisticas()}: solo F11).
     * Un equipo inexistente no las admite.
     */
    boolean equipoAdmiteEstadisticas(Long equipoId);
}