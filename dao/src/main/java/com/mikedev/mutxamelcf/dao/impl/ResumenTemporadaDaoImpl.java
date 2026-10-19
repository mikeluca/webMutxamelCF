package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ResumenTemporadaDao;
import com.mikedev.mutxamelcf.model.AsistenciaEntrenamientosJugador;
import com.mikedev.mutxamelcf.model.EstadisticasPartidosJugador;
import com.mikedev.mutxamelcf.model.RespuestasConvocatoriaJugador;

@Repository
public class ResumenTemporadaDaoImpl implements ResumenTemporadaDao {

	/*
	 * Mismo criterio de "sin resultado" que el resto de la aplicación
	 * (PartidoDaoImpl.CONDICION_SIN_RESULTADO): ni goles ni texto de resultado
	 * (un guion suelto cuenta como vacío).
	 */
	private static final String SIN_RESULTADO = """
			(P.GOLES_FAVOR IS NULL
			 AND (P.RESULTADO IS NULL OR TRIM(REPLACE(P.RESULTADO, '-', '')) IS NULL))
			""";

	private final JdbcTemplate jdbcTemplate;

	public ResumenTemporadaDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public EstadisticasPartidosJugador estadisticasPartidos(
			Long jugadorId, LocalDate desde, LocalDate hasta, LocalDate hoy) {

		Integer convocado = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM CONVOCATORIA_JUGADOR CJ
				JOIN CONVOCATORIAS C ON C.ID = CJ.CONVOCATORIA_ID
				JOIN PARTIDOS P ON P.ID = C.PARTIDO_ID
				WHERE CJ.JUGADOR_ID = ?
				  AND P.CANCELADO = 0
				  AND P.DIA BETWEEN ? AND ?
				  AND (P.DIA < ? OR (P.DIA = ? AND NOT %s))
				""".formatted(SIN_RESULTADO),
				Integer.class,
				jugadorId, Date.valueOf(desde), Date.valueOf(hasta), Date.valueOf(hoy), Date.valueOf(hoy));

		return jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(EJ.GOLES), 0),
				       COALESCE(SUM(EJ.ASISTENCIAS), 0),
				       COALESCE(SUM(EJ.TARJETAS_AMARILLAS), 0),
				       COALESCE(SUM(EJ.TARJETA_ROJA), 0)
				FROM PARTIDO_ESTADISTICAS_JUGADOR EJ
				JOIN PARTIDOS P ON P.ID = EJ.PARTIDO_ID
				WHERE EJ.JUGADOR_ID = ?
				  AND P.CANCELADO = 0
				  AND P.DIA BETWEEN ? AND ?
				""",
				(rs, n) -> new EstadisticasPartidosJugador(
						convocado == null ? 0 : convocado, rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)),
				jugadorId, Date.valueOf(desde), Date.valueOf(hasta));
	}

	@Override
	public AsistenciaEntrenamientosJugador asistenciaEntrenamientos(Long jugadorId, LocalDate desde, LocalDate hasta) {

		return jdbcTemplate.queryForObject("""
				SELECT COUNT(*),
				       COALESCE(SUM(CASE WHEN A.ESTADO = 'PRESENTE' THEN 1 ELSE 0 END), 0),
				       COALESCE(SUM(CASE WHEN A.ESTADO = 'RETRASO' THEN 1 ELSE 0 END), 0),
				       COALESCE(SUM(CASE WHEN A.ESTADO = 'MAL_COMPORTAMIENTO' THEN 1 ELSE 0 END), 0),
				       COALESCE(SUM(CASE WHEN A.ESTADO = 'FALTA' THEN 1 ELSE 0 END), 0),
				       COALESCE(SUM(CASE WHEN A.ESTADO = 'FALTA_JUSTIFICADA' THEN 1 ELSE 0 END), 0)
				FROM ENTRENAMIENTO_ASISTENCIA A
				JOIN ENTRENAMIENTOS E ON E.ID = A.ENTRENAMIENTO_ID
				WHERE A.JUGADOR_ID = ?
				  AND E.FECHA BETWEEN ? AND ?
				""",
				(rs, n) -> new AsistenciaEntrenamientosJugador(
						rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)),
				jugadorId, Date.valueOf(desde), Date.valueOf(hasta));
	}

	@Override
	public RespuestasConvocatoriaJugador respuestasConvocatoria(Long jugadorId, LocalDate desde, LocalDate hasta) {

		return jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(CASE WHEN CJ.RESPUESTA = 'VA' THEN 1 ELSE 0 END), 0),
				       COALESCE(SUM(CASE WHEN CJ.RESPUESTA = 'NO_PUEDE' THEN 1 ELSE 0 END), 0),
				       COALESCE(SUM(CASE WHEN CJ.RESPUESTA IS NULL THEN 1 ELSE 0 END), 0)
				FROM CONVOCATORIA_JUGADOR CJ
				JOIN CONVOCATORIAS C ON C.ID = CJ.CONVOCATORIA_ID
				JOIN PARTIDOS P ON P.ID = C.PARTIDO_ID
				WHERE CJ.JUGADOR_ID = ?
				  AND P.CANCELADO = 0
				  AND P.DIA BETWEEN ? AND ?
				""",
				(rs, n) -> new RespuestasConvocatoriaJugador(rs.getInt(1), rs.getInt(2), rs.getInt(3)),
				jugadorId, Date.valueOf(desde), Date.valueOf(hasta));
	}

}
