package com.mikedev.mutxamelcf.dao.impl;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ID de la temporada activa para los INSERT que guardan TEMPORADA_ID. Sin temporada activa falla con un mensaje claro
 * en vez de dejar que la base de datos rechace el NULL (ORA-01400).
 */
final class TemporadaActivaId {

	private TemporadaActivaId() {
	}

	/** El ID de la temporada activa, o -1 si no hay ninguna (las lecturas entonces no filtran). */
	static long obtenerOMenosUno(JdbcTemplate jdbcTemplate) {
		List<Long> ids = jdbcTemplate.queryForList("SELECT ID FROM TEMPORADAS WHERE ACTIVA = 1", Long.class);

		return ids.isEmpty() ? -1L : ids.get(0);
	}

	/**
	 * La temporada anterior a la activa, solo mientras la activa no tenga ningún partido (el verano de después de un
	 * cierre: la web pública muestra entonces los equipos, resultados y clasificaciones de la anterior). -1 si la activa
	 * ya tiene partidos, si no hay activa o si no hay ninguna anterior.
	 */
	static long anteriorSiVerano(JdbcTemplate jdbcTemplate) {
		List<java.util.Map<String, Object>> activas = jdbcTemplate
				.queryForList("SELECT FECHA_INICIO, FECHA_FIN FROM TEMPORADAS WHERE ACTIVA = 1");

		if (activas.isEmpty() || activas.get(0).get("FECHA_INICIO") == null) {
			return -1L;
		}

		java.sql.Date inicio = new java.sql.Date(((java.util.Date) activas.get(0).get("FECHA_INICIO")).getTime());
		Object finActiva = activas.get(0).get("FECHA_FIN");
		java.sql.Date fin = finActiva == null ? java.sql.Date.valueOf("9999-12-31")
				: new java.sql.Date(((java.util.Date) finActiva).getTime());

		Integer partidos = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM PARTIDOS WHERE DIA BETWEEN ? AND ? AND CANCELADO = 0", Integer.class, inicio, fin);

		if (partidos != null && partidos > 0) {
			return -1L;
		}

		List<Long> anteriores = jdbcTemplate.queryForList(
				"SELECT ID FROM TEMPORADAS WHERE FECHA_INICIO < ? ORDER BY FECHA_INICIO DESC", Long.class, inicio);

		return anteriores.isEmpty() ? -1L : anteriores.get(0);
	}

	static long obtener(JdbcTemplate jdbcTemplate) {
		List<Long> ids = jdbcTemplate.queryForList("SELECT ID FROM TEMPORADAS WHERE ACTIVA = 1", Long.class);

		if (ids.isEmpty()) {
			throw new IllegalStateException("No hay ninguna temporada activa: actívala antes de guardar este dato");
		}

		return ids.get(0);
	}
}
