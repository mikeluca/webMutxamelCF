package com.mikedev.mutxamelcf.service;

import java.util.Map;

/** Purga de las comunicaciones de hace más de dos temporadas (docs/transicion-temporada.md, decisión 21). */
public interface PurgaComunicacionesService {

	/**
	 * Borra las comunicaciones anteriores al inicio de la temporada de hace dos temporadas. No hace nada si no hay
	 * temporada activa o dos anteriores a ella.
	 *
	 * @return las filas borradas de cada tabla; vacío si no había nada que purgar
	 */
	Map<String, Integer> purgar();
}
