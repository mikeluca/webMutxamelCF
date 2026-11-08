package com.mikedev.mutxamelcf.model;

/**
 * Lo mínimo de un jugador para elegirlo (sin foto): id, dorsal y nombre.
 *
 * @param nombreRetransmision JUGADORES.NOMBRE_RETRANSMISION: cómo se le nombra en el directo (null o vacío = nombre y
 *                            apellidos)
 */
public record JugadorBasico(Long id, Integer dorsal, String nombre, String apellidos, String nombreRetransmision) {

    public JugadorBasico(Long id, Integer dorsal, String nombre, String apellidos) {
        this(id, dorsal, nombre, apellidos, null);
    }

    public String nombreCompleto() {
        return ((nombre == null ? "" : nombre) + " " + (apellidos == null ? "" : apellidos)).trim();
    }

    /**
     * El nombre con el que sale el jugador hacia fuera del directo (push de gol, goleadores del final, alineación y
     * portada): el de retransmisión si lo tiene y, si no, nombre y apellidos. Es el único sitio que lo decide.
     */
    public String nombreEnDirecto() {

        if (nombreRetransmision != null && !nombreRetransmision.isBlank()) {
            return nombreRetransmision.trim();
        }

        return nombreCompleto();
    }
}
