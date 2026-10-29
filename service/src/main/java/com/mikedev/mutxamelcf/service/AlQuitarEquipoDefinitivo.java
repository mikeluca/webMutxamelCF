package com.mikedev.mutxamelcf.service;

/**
 * Hook "al quitar equipo definitivo", pareja de {@link AlAsignarEquipoDefinitivo}: el reparto lo llama por cada jugador
 * que se devuelve a la pretemporada de su categoría, dentro de la misma transacción y cuando ya se han guardado los
 * cambios. Se usará para anular las cuotas generadas al asignar; de momento solo hay una implementación que no hace
 * nada.
 */
public interface AlQuitarEquipoDefinitivo {

	void alQuitar(QuitaDefinitiva quita);

	/**
	 * @param equipoAnteriorId el equipo definitivo que tenía antes de devolverlo
	 */
	record QuitaDefinitiva(Long jugadorId, Long equipoAnteriorId, Long temporadaId) {
	}
}
