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

	static long obtener(JdbcTemplate jdbcTemplate) {
		List<Long> ids = jdbcTemplate.queryForList("SELECT ID FROM TEMPORADAS WHERE ACTIVA = 1", Long.class);

		if (ids.isEmpty()) {
			throw new IllegalStateException("No hay ninguna temporada activa: actívala antes de guardar este dato");
		}

		return ids.get(0);
	}
}
