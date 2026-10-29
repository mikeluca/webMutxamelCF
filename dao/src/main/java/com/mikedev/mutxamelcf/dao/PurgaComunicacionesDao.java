package com.mikedev.mutxamelcf.dao;

import java.sql.Timestamp;
import java.util.Map;

/** Borrado de las comunicaciones antiguas (docs/transicion-temporada.md, decisión 21). */
public interface PurgaComunicacionesDao {

	/**
	 * Inicio de la temporada de hace dos temporadas: con la activa 2028/2029, el de 2026/2027. {@code null} si no hay
	 * temporada activa o no hay dos temporadas anteriores a ella.
	 */
	Timestamp inicioDeHaceDosTemporadas();

	/**
	 * Borra las comunicaciones publicadas antes de {@code limite} con sus filas dependientes (equipos, categorías y
	 * destinatarios), y los reportes de moderación ya resueltos de esas fechas. Los reportes PENDIENTES no se tocan:
	 * REPORTES_COMUNICACION no tiene FK a COMUNICACIONES y conserva su copia del texto.
	 *
	 * @return las filas borradas de cada tabla, en el orden en que se borraron
	 */
	Map<String, Integer> purgarAnterioresA(Timestamp limite);
}
