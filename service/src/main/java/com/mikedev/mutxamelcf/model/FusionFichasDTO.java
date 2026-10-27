package com.mikedev.mutxamelcf.model;

import java.util.List;

/**
 * Lo que va a pasar al fusionar fichas de cuerpo técnico. El nombre, los apellidos, la foto y el puesto general son
 * los de la ficha que se conserva.
 *
 * @param equiposQueSePasan   equipos de las duplicadas que la conservada todavía no lleva (con su puesto final)
 * @param equiposYaPresentes  equipos que la conservada ya llevaba: se mantiene su puesto
 * @param vinculosAMover      cuentas de la app que pasan a la ficha conservada
 */
public record FusionFichasDTO(
		String conservada,
		List<String> duplicadas,
		List<String> equiposQueSePasan,
		List<String> equiposYaPresentes,
		int vinculosAMover) {
}
