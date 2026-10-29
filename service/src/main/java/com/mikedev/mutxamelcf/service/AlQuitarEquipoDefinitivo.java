package com.mikedev.mutxamelcf.service;

import java.util.List;

/**
 * Hook "al quitar equipo definitivo", pareja de {@link AlAsignarEquipoDefinitivo}: el reparto lo llama por cada jugador
 * que se devuelve a la pretemporada, dentro de la misma transacción y cuando ya se han guardado los cambios. Anula las
 * cuotas generadas al asignar.
 */
public interface AlQuitarEquipoDefinitivo {

	/**
	 * @return avisos para quien devuelve al jugador (se muestran en la respuesta); vacío si no hay nada que decir
	 */
	List<String> alQuitar(QuitaDefinitiva quita);

	/**
	 * @param equipoAnteriorId el equipo definitivo que tenía antes de devolverlo
	 */
	record QuitaDefinitiva(Long jugadorId, Long equipoAnteriorId, Long temporadaId) {
	}
}
