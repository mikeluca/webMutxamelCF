package com.mikedev.mutxamelcf.model;

import java.sql.Timestamp;
import java.util.List;

/**
 * Cuenta de la app invitada que todavía no se ha activado (ADMIN_APP).
 * {@code vinculos} son los nombres de los jugadores, familiares o equipos a los
 * que está ligada.
 */
public record UsuarioPendienteResponse(
        int id,
        String email,
        String nombre,
        String apellidos,
        List<String> roles,
        List<String> vinculos,
        Timestamp fechaAlta,
        boolean tokenCaducado,
        int intentosActivacion) {
}
