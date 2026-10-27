package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.CoordinacionDao;
import com.mikedev.mutxamelcf.model.EquipoCoordinacion;
import com.mikedev.mutxamelcf.model.IncidenciaEntrenamiento;
import com.mikedev.mutxamelcf.model.PartidoCoordinacion;
import com.mikedev.mutxamelcf.model.SeguimientoJugador;

@Repository
public class CoordinacionDaoImpl implements CoordinacionDao {

	private static final String NOMBRE_JUGADOR = "TRIM(J.NOMBRE || ' ' || COALESCE(J.APELLIDOS, ''))";

	private static final String SELECT_SEGUIMIENTO = """
			SELECT J.ID, %s AS NOMBRE_JUGADOR, EQ.NOMBRE AS EQUIPO,
			       COALESCE(SUM(CASE WHEN A.ESTADO = 'FALTA' THEN 1 ELSE 0 END), 0) AS FALTAS,
			       COALESCE(SUM(CASE WHEN A.ESTADO = 'FALTA_JUSTIFICADA' THEN 1 ELSE 0 END), 0) AS JUSTIFICADAS,
			       COALESCE(SUM(CASE WHEN A.ESTADO = 'MAL_COMPORTAMIENTO' THEN 1 ELSE 0 END), 0) AS MAL,
			       COALESCE(SUM(CASE WHEN A.ESTADO = 'RETRASO' THEN 1 ELSE 0 END), 0) AS RETRASOS,
			       MAX(CASE WHEN A.ESTADO IN ('FALTA', 'MAL_COMPORTAMIENTO') THEN E.FECHA END) AS ULTIMA
			FROM JUGADORES J
			LEFT JOIN EQUIPO EQ ON EQ.ID = J.EQUIPO_ID
			JOIN ENTRENAMIENTO_ASISTENCIA A ON A.JUGADOR_ID = J.ID
			JOIN ENTRENAMIENTOS E ON E.ID = A.ENTRENAMIENTO_ID
			WHERE E.FECHA BETWEEN ? AND ?
			""".formatted(NOMBRE_JUGADOR);

	private static final RowMapper<SeguimientoJugador> SEGUIMIENTO = (rs, n) -> {
		Date ultima = rs.getDate("ULTIMA");

		return new SeguimientoJugador(
				rs.getLong("ID"), rs.getString("NOMBRE_JUGADOR"), rs.getString("EQUIPO"),
				rs.getInt("FALTAS"), rs.getInt("JUSTIFICADAS"), rs.getInt("MAL"), rs.getInt("RETRASOS"),
				ultima == null ? null : ultima.toLocalDate());
	};

	private final JdbcTemplate jdbcTemplate;

	public CoordinacionDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<PartidoCoordinacion> partidosEntre(LocalDate desde, LocalDate hasta) {
		return jdbcTemplate.query("""
				SELECT P.ID, P.EQUIPO_ID, E.NOMBRE, P.RIVAL, P.DIA, P.HORA, P.ES_LOCAL, P.CANCELADO,
				       P.GOLES_FAVOR, P.RESULTADO,
				       CASE WHEN EXISTS (SELECT 1 FROM CONVOCATORIAS C WHERE C.PARTIDO_ID = P.ID)
				            THEN 1 ELSE 0 END AS CON_CONVOCATORIA
				FROM PARTIDOS P
				JOIN EQUIPO E ON E.ID = P.EQUIPO_ID
				WHERE P.DIA BETWEEN ? AND ?
				ORDER BY E.ORDEN, E.NOMBRE, P.DIA, P.ID
				""",
				(rs, n) -> {
					int esLocal = rs.getInt("ES_LOCAL");
					boolean local = rs.wasNull() || esLocal == 1;
					int golesFavor = rs.getInt("GOLES_FAVOR");
					boolean sinGoles = rs.wasNull();

					return new PartidoCoordinacion(
							rs.getLong("ID"), rs.getLong("EQUIPO_ID"), rs.getString("NOMBRE"), rs.getString("RIVAL"),
							rs.getDate("DIA").toLocalDate(), rs.getString("HORA"), local,
							rs.getInt("CANCELADO") == 1, sinGoles ? null : golesFavor, rs.getString("RESULTADO"),
							rs.getInt("CON_CONVOCATORIA") == 1);
				},
				Date.valueOf(desde), Date.valueOf(hasta));
	}

	@Override
	public List<EquipoCoordinacion> descansosEntre(LocalDate desde, LocalDate hasta) {
		return jdbcTemplate.query("""
				SELECT E.ID, E.NOMBRE, D.JORNADA, D.FECHA
				FROM DESCANSOS_EQUIPO D
				JOIN EQUIPO E ON E.ID = D.EQUIPO_ID
				WHERE D.FECHA BETWEEN ? AND ?
				ORDER BY E.ORDEN, E.NOMBRE, D.FECHA
				""",
				(rs, n) -> {
					int jornada = rs.getInt("JORNADA");
					Integer valor = rs.wasNull() ? null : jornada;

					return new EquipoCoordinacion(
							rs.getLong("ID"), rs.getString("NOMBRE"), valor, rs.getDate("FECHA").toLocalDate());
				},
				Date.valueOf(desde), Date.valueOf(hasta));
	}

	@Override
	public List<EquipoCoordinacion> equiposConRelacionFfcv() {
		return jdbcTemplate.query("""
				SELECT E.ID, E.NOMBRE, E.ORDEN
				FROM EQUIPO E
				WHERE EXISTS (SELECT 1 FROM EQUIPOS_FFCV F WHERE F.EQUIPO_ID = E.ID)
				ORDER BY E.ORDEN, E.NOMBRE
				""",
				(rs, n) -> new EquipoCoordinacion(rs.getLong("ID"), rs.getString("NOMBRE"), null, null));
	}

	@Override
	public LocalDateTime ultimaImportacionFfcv() {
		Timestamp ultima = jdbcTemplate.queryForObject(
				"SELECT MAX(FECHA_IMPORTACION) FROM PARTIDOS WHERE ORIGEN = 'FFCV'", Timestamp.class);

		return ultima == null ? null : ultima.toLocalDateTime();
	}

	@Override
	public List<SeguimientoJugador> seguimientoJugadores(
			LocalDate desde, LocalDate hasta, int umbralFaltas, int umbralMalComportamiento) {

		// Las bajas quedan fuera del seguimiento de la plantilla actual.
		return jdbcTemplate.query(SELECT_SEGUIMIENTO + """
				AND J.FECHA_BAJA IS NULL
				GROUP BY J.ID, J.NOMBRE, J.APELLIDOS, EQ.NOMBRE
				HAVING SUM(CASE WHEN A.ESTADO = 'FALTA' THEN 1 ELSE 0 END) >= ?
				    OR SUM(CASE WHEN A.ESTADO = 'MAL_COMPORTAMIENTO' THEN 1 ELSE 0 END) >= ?
				""",
				SEGUIMIENTO,
				Date.valueOf(desde), Date.valueOf(hasta), umbralFaltas, umbralMalComportamiento);
	}

	@Override
	public SeguimientoJugador seguimientoDeJugador(Long jugadorId, LocalDate desde, LocalDate hasta) {
		List<SeguimientoJugador> filas = jdbcTemplate.query(SELECT_SEGUIMIENTO + """
				AND J.ID = ?
				GROUP BY J.ID, J.NOMBRE, J.APELLIDOS, EQ.NOMBRE
				""",
				SEGUIMIENTO,
				Date.valueOf(desde), Date.valueOf(hasta), jugadorId);

		if (!filas.isEmpty()) {
			return filas.get(0);
		}

		// Sin asistencias en el periodo: el jugador existe con todo a cero.
		List<SeguimientoJugador> sinDatos = jdbcTemplate.query(
				"SELECT J.ID, " + NOMBRE_JUGADOR + " AS NOMBRE_JUGADOR, EQ.NOMBRE AS EQUIPO, 0 AS FALTAS, 0 AS JUSTIFICADAS,"
						+ " 0 AS MAL, 0 AS RETRASOS, CAST(NULL AS DATE) AS ULTIMA FROM JUGADORES J"
						+ " LEFT JOIN EQUIPO EQ ON EQ.ID = J.EQUIPO_ID WHERE J.ID = ?",
				SEGUIMIENTO, jugadorId);

		return sinDatos.isEmpty() ? null : sinDatos.get(0);
	}

	@Override
	public List<IncidenciaEntrenamiento> incidenciasDeJugador(Long jugadorId, LocalDate desde, LocalDate hasta) {
		return jdbcTemplate.query("""
				SELECT E.FECHA, A.ESTADO
				FROM ENTRENAMIENTO_ASISTENCIA A
				JOIN ENTRENAMIENTOS E ON E.ID = A.ENTRENAMIENTO_ID
				WHERE A.JUGADOR_ID = ?
				  AND E.FECHA BETWEEN ? AND ?
				  AND A.ESTADO <> 'PRESENTE'
				ORDER BY E.FECHA DESC, E.ID DESC
				""",
				(rs, n) -> new IncidenciaEntrenamiento(rs.getDate("FECHA").toLocalDate(), rs.getString("ESTADO")),
				jugadorId, Date.valueOf(desde), Date.valueOf(hasta));
	}
}
