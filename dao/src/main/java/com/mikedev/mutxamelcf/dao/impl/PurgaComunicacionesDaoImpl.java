package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.PurgaComunicacionesDao;

@Repository
public class PurgaComunicacionesDaoImpl implements PurgaComunicacionesDao {

	/** Las comunicaciones que se purgan: las publicadas antes del límite (o creadas, si no tienen publicación). */
	private static final String ANTIGUAS = "SELECT ID FROM COMUNICACIONES WHERE COALESCE(FECHA_PUBLICACION, FECHA_CREACION) < ?";

	private final JdbcTemplate jdbcTemplate;

	public PurgaComunicacionesDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Timestamp inicioDeHaceDosTemporadas() {
		List<java.util.Date> activas = jdbcTemplate.queryForList(
				"SELECT FECHA_INICIO FROM TEMPORADAS WHERE ACTIVA = 1 AND FECHA_INICIO IS NOT NULL", java.util.Date.class);

		if (activas.isEmpty()) {
			return null;
		}

		Timestamp inicioActiva = aTimestamp(activas.get(0));

		// Las temporadas anteriores a la activa, de más reciente a más antigua: la segunda es la de hace dos temporadas
		List<java.util.Date> anteriores = jdbcTemplate.queryForList(
				"SELECT FECHA_INICIO FROM TEMPORADAS WHERE FECHA_INICIO < ? ORDER BY FECHA_INICIO DESC",
				java.util.Date.class, inicioActiva);

		return anteriores.size() < 2 ? null : aTimestamp(anteriores.get(1));
	}

	private static Timestamp aTimestamp(java.util.Date fecha) {
		return fecha instanceof java.sql.Date sql ? Timestamp.valueOf(sql.toLocalDate().atStartOfDay())
				: new Timestamp(fecha.getTime());
	}

	@Override
	public Map<String, Integer> purgarAnterioresA(Timestamp limite) {
		Map<String, Integer> borradas = new LinkedHashMap<>();

		for (String tabla : List.of("COMUNICACION_EQUIPO", "COMUNICACION_CATEGORIA", "COMUNICACION_USUARIO")) {
			borradas.put(tabla,
					jdbcTemplate.update("DELETE FROM " + tabla + " WHERE COMUNICACION_ID IN (" + ANTIGUAS + ")", limite));
		}

		// Sin FK a COMUNICACIONES: se miden por la fecha del mensaje reportado (o del reporte), no por su existencia
		borradas.put("REPORTES_COMUNICACION", jdbcTemplate.update(
				"DELETE FROM REPORTES_COMUNICACION WHERE ESTADO <> 'PENDIENTE'"
						+ " AND COALESCE(FECHA_MENSAJE, FECHA_REPORTE) < ?",
				limite));

		borradas.put("COMUNICACIONES", jdbcTemplate.update(
				"DELETE FROM COMUNICACIONES WHERE COALESCE(FECHA_PUBLICACION, FECHA_CREACION) < ?", limite));

		return borradas;
	}
}
