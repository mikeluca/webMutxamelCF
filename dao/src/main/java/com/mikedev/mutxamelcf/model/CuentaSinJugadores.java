package com.mikedev.mutxamelcf.model;

import java.sql.Timestamp;

/**
 * Una cuenta de la app en el circuito de "sin jugadores activos".
 *
 * @param fechaAviso          cuándo se avisó de que se desactivaría; {@code null} si aún no se le ha avisado
 * @param desactivadaPorEsto  la desactivó el job (a diferencia de una desactivación manual del administrador)
 */
public record CuentaSinJugadores(long id, String email, String nombre, boolean activa, Timestamp fechaAviso,
		boolean desactivadaPorEsto) {
}
