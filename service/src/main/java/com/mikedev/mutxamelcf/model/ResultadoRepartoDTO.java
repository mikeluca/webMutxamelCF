package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Lo que ha pasado al asignar. Los {@code avisos} no impiden la asignación (por ejemplo, un dorsal repetido).
 *
 * @param equipoActivado      el equipo se ha activado en la temporada con esta asignación
 * @param pretemporadaVaciada el equipo de pretemporada se ha quedado sin jugadores y se ha desactivado
 */
public record ResultadoRepartoDTO(
		int asignados,
		List<String> avisos,
		boolean equipoActivado,
		boolean pretemporadaVaciada,
		List<String> pruebasConfirmadas) {
}
