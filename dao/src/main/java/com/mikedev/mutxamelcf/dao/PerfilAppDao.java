package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.Jugador;

public interface PerfilAppDao {

    /**
     * Obtiene el familiar asociado al usuario de la aplicación.
     */
    Familiar obtenerFamiliarPorUsuario(int usuarioAppId);

    /**
     * Obtiene los jugadores asociados al usuario.
     *
     * Se contemplan dos relaciones:
     *
     * 1. USUARIOS_APP_JUGADORES
     * 2. USUARIOS_APP_FAMILIARES
     *    -> FAMILIARES_JUGADOR
     */
    List<Jugador> obtenerJugadoresPorUsuario(int usuarioAppId);

    /**
     * Obtiene los miembros del cuerpo técnico asociados
     * al usuario.
     */
    List<CuerpoTecnico> obtenerCuerpoTecnicoPorUsuario(int usuarioAppId);

    /**
     * Obtiene los equipos asociados al cuerpo técnico
     * del usuario.
     */
    List<Equipo> obtenerEquiposPorUsuario(int usuarioAppId);

    /**
     * Los jugadores activos (sin fecha de baja) a los que la cuenta está vinculada directamente
     * (USUARIOS_APP_JUGADORES): ella misma como jugador.
     */
    List<Jugador> obtenerJugadoresPropiosActivos(int usuarioAppId);

    /**
     * Los jugadores activos (sin fecha de baja) de los que la cuenta es familiar (USUARIOS_APP_FAMILIARES ->
     * FAMILIARES_JUGADOR): sus hijos.
     */
    List<Jugador> obtenerHijosActivos(int usuarioAppId);
}