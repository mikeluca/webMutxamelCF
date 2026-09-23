package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.PersonaVinculable;
import com.mikedev.mutxamelcf.model.VinculoUsuarioApp;

/**
 * Gestiona el vínculo entre una cuenta de la app móvil (USUARIOS_APP)
 * y la persona real del club a la que pertenece (jugador, familiar
 * o miembro del cuerpo técnico).
 */
public interface UsuarioAppVinculoDao {

    List<PersonaVinculable> obtenerJugadoresSinCuenta();

    List<PersonaVinculable> obtenerFamiliaresSinCuenta();

    List<PersonaVinculable> obtenerCuerpoTecnicoSinCuenta();

    boolean jugadorTieneCuenta(Long jugadorId);

    boolean familiarTieneCuenta(Long familiarId);

    boolean cuerpoTecnicoTieneCuenta(Long cuerpoTecnicoId);

    void vincularJugador(int usuarioAppId, Long jugadorId);

    void vincularFamiliar(int usuarioAppId, Long familiarId);

    void vincularCuerpoTecnico(int usuarioAppId, Long cuerpoTecnicoId);

    /**
     * Elimina cualquier vínculo (jugador, familiar o cuerpo técnico) que
     * tuviera esta cuenta, sin fallar si no tenía ninguno. Paso previo
     * obligatorio antes de borrar la propia cuenta.
     */
    void desvincularTodo(int usuarioAppId);

    /**
     * Quita un vínculo de jugador concreto (no toca los demás vínculos
     * que pueda tener la cuenta).
     */
    void desvincularJugador(int usuarioAppId, Long jugadorId);

    /**
     * Quita un vínculo de familiar concreto (no toca los demás vínculos
     * que pueda tener la cuenta).
     */
    void desvincularFamiliar(int usuarioAppId, Long familiarId);

    /**
     * Quita un vínculo de cuerpo técnico concreto (no toca los demás
     * vínculos que pueda tener la cuenta, p. ej. si entrena a más de un
     * equipo).
     */
    void desvincularCuerpoTecnico(int usuarioAppId, Long cuerpoTecnicoId);

    /**
     * Todos los vínculos (jugador, familiar, cuerpo técnico) que tiene
     * hoy la cuenta, en cualquier combinación. Lista vacía si no tiene
     * ninguno.
     */
    List<VinculoUsuarioApp> obtenerVinculos(int usuarioAppId);

    /**
     * Nombre completo de una persona (jugador/familiar/cuerpo técnico)
     * a partir de su tipo e id, para personalizar comunicaciones.
     * Devuelve null si no existe.
     */
    String obtenerNombrePersona(String tipo, Long personaId);

    /**
     * Email registrado de un familiar (tabla FAMILIARES). El email de
     * la invitación de un familiar siempre sale de aquí, nunca de lo
     * que escriba OFICINA, para que coincida con el dato de contacto
     * real y solo se pueda cambiar editando la ficha del familiar.
     * Devuelve null si el familiar no existe o no tiene email.
     */
    String obtenerEmailFamiliar(Long familiarId);
}
