package com.mikedev.mutxamelcf.model;

/**
 * Un jugador que se puede invitar a un partido.
 *
 * @param yaInvitado    ya está invitado (sin rechazar) a la convocatoria de este partido
 * @param bloqueado     no se puede invitar: ya está autorizado como invitado con otro equipo ese día
 * @param motivoBloqueo explicación para la app; null si no está bloqueado
 */
public record JugadorInvitableResponse(
        Long jugadorId,
        String nombre,
        Integer dorsal,
        boolean yaInvitado,
        boolean bloqueado,
        String motivoBloqueo) {
}
