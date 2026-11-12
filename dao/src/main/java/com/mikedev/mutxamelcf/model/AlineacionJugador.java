package com.mikedev.mutxamelcf.model;

/**
 * Una fila de la alineación guardada de un partido (PARTIDO_ALINEACION).
 *
 * @param orden orden de elección dentro de su grupo (titulares por un lado, suplentes por otro), desde 1; en los
 *              titulares da la posición en el campo
 */
public record AlineacionJugador(Long jugadorId, boolean titular, int orden) {
}
