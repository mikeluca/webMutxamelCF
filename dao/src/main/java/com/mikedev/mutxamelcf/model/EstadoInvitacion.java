package com.mikedev.mutxamelcf.model;

/**
 * Estado de un jugador invitado a la convocatoria de otro equipo (CONVOCATORIA_JUGADOR.ESTADO_INVITACION, ver
 * docs/convocatoria-invitados.md). Los jugadores del propio equipo no tienen estado.
 */
public enum EstadoInvitacion {

    /** Esperando el visto bueno de coordinación. */
    PENDIENTE_COORDINACION,

    /** Aprobado, pero su entrenador lo tiene convocado el mismo día: tiene que liberarlo. */
    PENDIENTE_ENTRENADOR,

    /** Puede jugar: aparece en la lista de convocados, el calendario y la retransmisión. */
    AUTORIZADO,

    /** Coordinación lo ha rechazado; el entrenador que convocó elige a otro. */
    RECHAZADO;

    /** Pendiente de coordinación o de su entrenador. */
    public boolean esPendiente() {
        return this == PENDIENTE_COORDINACION || this == PENDIENTE_ENTRENADOR;
    }
}
