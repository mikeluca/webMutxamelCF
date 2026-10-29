package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ResumenTemporadaDao;
import com.mikedev.mutxamelcf.model.AsistenciaEntrenamientosJugador;
import com.mikedev.mutxamelcf.model.AsistenciaJugadorEquipo;
import com.mikedev.mutxamelcf.model.EntrenamientoDelDiaDeJugador;
import com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo;
import com.mikedev.mutxamelcf.model.PartidosEquipo;
import com.mikedev.mutxamelcf.model.EntrenamientoDelDia;
import com.mikedev.mutxamelcf.model.EstadisticasPartidosJugador;

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
	public int ausenciasJustificadas(Long jugadorId, LocalDate desde, LocalDate hasta, LocalDate hoy) {

		Integer total = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM JUSTIFICACIONES_FALTA_PARTIDO J
				JOIN PARTIDOS P ON P.ID = J.PARTIDO_ID
				WHERE J.JUGADOR_ID = ?
				  AND P.CANCELADO = 0
				  AND P.DIA BETWEEN ? AND ?
				  AND P.DIA <= ?
				""",
				Integer.class,
				jugadorId, Date.valueOf(desde), Date.valueOf(hasta), Date.valueOf(hoy));

		return total == null ? 0 : total;
	}

	@Override
	public List<EntrenamientoDelDia> entrenamientosDelDia(Long jugadorId, LocalDate fecha) {

		return jdbcTemplate.query("""
				SELECT S.HORA, A.ESTADO
				FROM ENTRENAMIENTO_ASISTENCIA A
				JOIN ENTRENAMIENTOS E ON E.ID = A.ENTRENAMIENTO_ID
				LEFT JOIN SESIONES_ENTRENAMIENTO S ON S.ID = E.SESION_ENTRENAMIENTO_ID
				WHERE A.JUGADOR_ID = ?
				  AND E.FECHA = ?
				""",
				(rs, n) -> new EntrenamientoDelDia(rs.getString(1), rs.getString(2)),
				jugadorId, Date.valueOf(fecha));
	}


	private static String nombreCompleto(java.sql.ResultSet rs) throws java.sql.SQLException {
		String nombre = rs.getString("NOMBRE") == null ? "" : rs.getString("NOMBRE");
		String apellidos = rs.getString("APELLIDOS") == null ? "" : rs.getString("APELLIDOS");

		return (nombre + " " + apellidos).trim();
	}

	private static Integer dorsal(java.sql.ResultSet rs) throws java.sql.SQLException {
		int dorsal = rs.getInt("DORSAL");

		return rs.wasNull() ? null : dorsal;
	}

	@Override
	public List<EstadisticasJugadorEquipo> estadisticasJugadoresDelEquipo(
			Long equipoId, LocalDate desde, LocalDate hasta, LocalDate hoy) {

		return jdbcTemplate.query("""
				SELECT J.ID, J.NOMBRE, J.APELLIDOS, COALESCE(JT.DORSAL, J.DORSAL) AS DORSAL,
				       COALESCE(SUM(EJ.GOLES), 0) AS GOLES,
				       COALESCE(SUM(EJ.ASISTENCIAS), 0) AS ASISTENCIAS,
				       COALESCE(SUM(EJ.TARJETAS_AMARILLAS), 0) AS AMARILLAS,
				       COALESCE(SUM(EJ.TARJETA_ROJA), 0) AS ROJAS,
				       (SELECT COUNT(*)
				          FROM CONVOCATORIA_JUGADOR CJ
				          JOIN CONVOCATORIAS C ON C.ID = CJ.CONVOCATORIA_ID
				          JOIN PARTIDOS P ON P.ID = C.PARTIDO_ID
				         WHERE CJ.JUGADOR_ID = J.ID
				           AND P.CANCELADO = 0
				           AND P.DIA BETWEEN ? AND ?
				           AND P.EQUIPO_ID = ?
				           AND (P.DIA < ? OR (P.DIA = ? AND NOT %s))) AS PARTIDOS
				FROM JUGADORES J
				LEFT JOIN JUGADOR_TEMPORADA JT ON JT.JUGADOR_ID = J.ID AND JT.TEMPORADA_ID = ?
				LEFT JOIN PARTIDO_ESTADISTICAS_JUGADOR EJ
				       ON EJ.JUGADOR_ID = J.ID
				      AND EJ.PARTIDO_ID IN (SELECT PP.ID FROM PARTIDOS PP
				                             WHERE PP.CANCELADO = 0 AND PP.EQUIPO_ID = ?
				                               AND PP.DIA BETWEEN ? AND ?)
				WHERE ((JT.ID IS NOT NULL AND JT.EQUIPO_ID = ? AND JT.ESTADO <> 'BAJA')
				    OR (JT.ID IS NULL AND J.EQUIPO_ID = ?))
				GROUP BY J.ID, J.NOMBRE, J.APELLIDOS, COALESCE(JT.DORSAL, J.DORSAL)
				""".formatted(SIN_RESULTADO),
				(rs, n) -> new EstadisticasJugadorEquipo(
						rs.getLong("ID"), nombreCompleto(rs), dorsal(rs),
						rs.getInt("GOLES"), rs.getInt("ASISTENCIAS"), rs.getInt("AMARILLAS"), rs.getInt("ROJAS"),
						rs.getInt("PARTIDOS")),
				// Los partidos se filtran por P.EQUIPO_ID = el equipo consultado (un jugador puede
				// estar convocado en otro equipo y la sanción va por competición).
				// Orden: subconsulta de convocatorias, temporada del JOIN de JUGADOR_TEMPORADA,
				// partidos del JOIN de estadísticas y, al final, los dos equipos del WHERE.
				Date.valueOf(desde), Date.valueOf(hasta), equipoId, Date.valueOf(hoy), Date.valueOf(hoy),
				temporadaId(desde),
				equipoId, Date.valueOf(desde), Date.valueOf(hasta),
				equipoId, equipoId);
	}

	// La temporada que contiene la fecha (la más reciente si hubiera varias); -1 si ninguna.
	// Se resuelve aparte: una subconsulta en el ON del LEFT JOIN puede dar ORA-01799 en Oracle.
	private long temporadaId(LocalDate fecha) {

		List<Long> ids = jdbcTemplate.queryForList("""
				SELECT ID FROM TEMPORADAS
				WHERE FECHA_INICIO <= ? AND (FECHA_FIN IS NULL OR FECHA_FIN >= ?)
				ORDER BY FECHA_INICIO DESC
				""",
				Long.class, Date.valueOf(fecha), Date.valueOf(fecha));

		return ids.isEmpty() ? -1L : ids.get(0);
	}

	@Override
	public List<AsistenciaJugadorEquipo> asistenciaEntrenamientosDelEquipo(
			Long equipoId, LocalDate desde, LocalDate hasta) {

		return jdbcTemplate.query("""
				SELECT J.ID, J.NOMBRE, J.APELLIDOS, COALESCE(JT.DORSAL, J.DORSAL) AS DORSAL,
				       COUNT(A.ENTRENAMIENTO_ID) AS TOTAL,
				       COALESCE(SUM(CASE WHEN A.ESTADO IN ('PRESENTE', 'RETRASO', 'MAL_COMPORTAMIENTO')
				                         THEN 1 ELSE 0 END), 0) AS ASISTIDOS
				FROM JUGADORES J
				LEFT JOIN JUGADOR_TEMPORADA JT ON JT.JUGADOR_ID = J.ID AND JT.TEMPORADA_ID = ?
				LEFT JOIN ENTRENAMIENTO_ASISTENCIA A
				       ON A.JUGADOR_ID = J.ID
				      AND A.ENTRENAMIENTO_ID IN (SELECT EN.ID FROM ENTRENAMIENTOS EN
				                                  WHERE EN.EQUIPO_ID = ? AND EN.LISTA_REVISADA = 1
				                                    AND EN.FECHA BETWEEN ? AND ?)
				WHERE ((JT.ID IS NOT NULL AND JT.EQUIPO_ID = ? AND JT.ESTADO <> 'BAJA')
				    OR (JT.ID IS NULL AND J.EQUIPO_ID = ?))
				GROUP BY J.ID, J.NOMBRE, J.APELLIDOS, COALESCE(JT.DORSAL, J.DORSAL)
				""",
				(rs, n) -> new AsistenciaJugadorEquipo(
						rs.getLong("ID"), nombreCompleto(rs), dorsal(rs), rs.getInt("TOTAL"), rs.getInt("ASISTIDOS")),
				// Orden: temporada del JOIN de JUGADOR_TEMPORADA, entrenamientos del JOIN de asistencia
				// y los dos equipos del WHERE.
				temporadaId(desde),
				equipoId, Date.valueOf(desde), Date.valueOf(hasta),
				equipoId, equipoId);
	}

	@Override
	public int contarEntrenamientosDelEquipo(Long equipoId, LocalDate desde, LocalDate hasta, boolean soloRevisados) {

		Integer total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ENTRENAMIENTOS WHERE EQUIPO_ID = ? AND FECHA BETWEEN ? AND ?"
						+ (soloRevisados ? " AND LISTA_REVISADA = 1" : ""),
				Integer.class,
				equipoId, Date.valueOf(desde), Date.valueOf(hasta));

		return total == null ? 0 : total;
	}

	@Override
	public List<EntrenamientoDelDiaDeJugador> entrenamientosDelDiaDelEquipo(Long equipoId, LocalDate fecha) {

		return jdbcTemplate.query("""
				SELECT E.ID AS ENTRENAMIENTO_ID, A.JUGADOR_ID, S.HORA, A.ESTADO, E.LISTA_REVISADA
				FROM ENTRENAMIENTOS E
				JOIN ENTRENAMIENTO_ASISTENCIA A ON A.ENTRENAMIENTO_ID = E.ID
				JOIN JUGADORES J ON J.ID = A.JUGADOR_ID
				LEFT JOIN SESIONES_ENTRENAMIENTO S ON S.ID = E.SESION_ENTRENAMIENTO_ID
				WHERE E.EQUIPO_ID = ?
				  AND E.FECHA = ?
				""",
				(rs, n) -> new EntrenamientoDelDiaDeJugador(
						rs.getLong("ENTRENAMIENTO_ID"), rs.getLong("JUGADOR_ID"), rs.getString("HORA"),
						rs.getString("ESTADO"), rs.getInt("LISTA_REVISADA") == 1),
				equipoId, Date.valueOf(fecha));
	}

	@Override
	public PartidosEquipo partidosDelEquipo(Long equipoId, LocalDate desde, LocalDate hasta) {

		return jdbcTemplate.queryForObject("""
				SELECT COUNT(*),
				       COALESCE(SUM(CASE WHEN GOLES_FAVOR > GOLES_CONTRA THEN 1 ELSE 0 END), 0),
				       COALESCE(SUM(CASE WHEN GOLES_FAVOR = GOLES_CONTRA THEN 1 ELSE 0 END), 0),
				       COALESCE(SUM(CASE WHEN GOLES_FAVOR < GOLES_CONTRA THEN 1 ELSE 0 END), 0),
				       COALESCE(SUM(GOLES_FAVOR), 0),
				       COALESCE(SUM(GOLES_CONTRA), 0)
				FROM PARTIDOS
				WHERE EQUIPO_ID = ?
				  AND CANCELADO = 0
				  AND GOLES_FAVOR IS NOT NULL
				  AND GOLES_CONTRA IS NOT NULL
				  AND DIA BETWEEN ? AND ?
				""",
				(rs, n) -> new PartidosEquipo(
						rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)),
				equipoId, Date.valueOf(desde), Date.valueOf(hasta));
	}
}
