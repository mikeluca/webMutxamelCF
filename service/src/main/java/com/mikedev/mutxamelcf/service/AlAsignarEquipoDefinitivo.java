package com.mikedev.mutxamelcf.service;

/**
 * Hook "al asignar equipo definitivo": el reparto de septiembre lo llama por cada jugador asignado, dentro de la misma
 * transacción y cuando ya se han guardado el equipo y el estado. Se usará para generar las cuotas del jugador (salvo
 * PRUEBA); de momento solo hay una implementación que no hace nada.
 */
public interface AlAsignarEquipoDefinitivo {

	void alAsignar(AsignacionDefinitiva asignacion);

	/**
	 * @param eraPrueba el jugador estaba en estado PRUEBA y pasa a CONFIRMADO con esta asignación
	 */
	record AsignacionDefinitiva(Long jugadorId, Long equipoId, Long temporadaId, boolean eraPrueba) {
	}
}
