package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorTemporada;

@Repository
public class JugadorTemporadaDaoImpl implements JugadorTemporadaDao {

	private static final String SELECT = """
			SELECT JT.JUGADOR_ID, JT.TEMPORADA_ID, JT.EQUIPO_ID, JT.EQUIPO_NOMBRE, JT.CATEGORIA, JT.DORSAL,
			       JT.PENDIENTE_REVISION, JT.NOTA, JT.FECHA_CREACION, JT.FECHA_ACTUALIZACION,
			       JT.ESTADO, JT.CONTINUIDAD, JT.FECHA_CONTINUIDAD, JT.USUARIO_APP_CONTINUIDAD_ID, JT.EQUIPO_DEFINITIVO,
			       J.NOMBRE AS JUGADOR_NOMBRE, J.APELLIDOS AS JUGADOR_APELLIDOS, J.ANIO_NACIMIENTO,
			       J.EQUIPO_ID AS EQUIPO_ACTUAL_ID, EA.NOMBRE AS EQUIPO_ACTUAL_NOMBRE, T.NOMBRE AS TEMPORADA_NOMBRE,
			       CASE WHEN J.FECHA_BAJA IS NULL THEN 0 ELSE 1 END AS JUGADOR_DE_BAJA
			FROM JUGADOR_TEMPORADA JT
			JOIN JUGADORES J ON J.ID = JT.JUGADOR_ID
			JOIN TEMPORADAS T ON T.ID = JT.TEMPORADA_ID
			LEFT JOIN EQUIPO EA ON EA.ID = J.EQUIPO_ID
			""";

	private static final RowMapper<JugadorTemporada> MAPPER = JugadorTemporadaDaoImpl::mapRow;

	private final JdbcTemplate jdbcTemplate;

	public JugadorTemporadaDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public JugadorTemporada obtener(Long jugadorId, Long temporadaId) {
		List<JugadorTemporada> filas = jdbcTemplate.query(
				SELECT + " WHERE JT.JUGADOR_ID = ? AND JT.TEMPORADA_ID = ?", MAPPER, jugadorId, temporadaId);
		return filas.isEmpty() ? null : filas.get(0);
	}

	@Override
	public List<JugadorTemporada> listarPorTemporada(Long temporadaId) {
		return jdbcTemplate.query(SELECT + " WHERE JT.TEMPORADA_ID = ? ORDER BY J.APELLIDOS, J.NOMBRE, J.ID", MAPPER,
				temporadaId);
	}

	@Override
	public List<JugadorTemporada> listarPorJugador(Long jugadorId) {
		return jdbcTemplate.query(SELECT + " WHERE JT.JUGADOR_ID = ? ORDER BY T.FECHA_INICIO DESC", MAPPER, jugadorId);
	}

	@Override
	public void guardar(JugadorTemporada f) {
		Timestamp ahora = Timestamp.valueOf(LocalDateTime.now());
		// ESTADO es la única fuente de la baja (la columna BAJA ya no existe).
		String estado = f.getEstado() == null ? "CONFIRMADO" : f.getEstado();
		String continuidad = f.getContinuidad() == null ? "PENDIENTE" : f.getContinuidad();
		Timestamp fechaContinuidad = f.getFechaContinuidad() == null ? null : Timestamp.valueOf(f.getFechaContinuidad());

		int filas = jdbcTemplate.update("""
				UPDATE JUGADOR_TEMPORADA SET EQUIPO_ID = ?, EQUIPO_NOMBRE = ?, CATEGORIA = ?, DORSAL = ?, ESTADO = ?,
				       CONTINUIDAD = ?, FECHA_CONTINUIDAD = ?, USUARIO_APP_CONTINUIDAD_ID = ?, EQUIPO_DEFINITIVO = ?,
				       PENDIENTE_REVISION = ?, NOTA = ?, FECHA_ACTUALIZACION = ?
				WHERE JUGADOR_ID = ? AND TEMPORADA_ID = ?""",
				f.getEquipoId(), f.getEquipoNombre(), f.getCategoria(), f.getDorsal(), estado, continuidad,
				fechaContinuidad, f.getUsuarioAppContinuidadId(), f.isEquipoDefinitivo() ? 1 : 0,
				f.isPendienteRevision() ? 1 : 0, f.getNota(), ahora, f.getJugadorId(), f.getTemporadaId());

		if (filas == 0) {
			jdbcTemplate.update("""
					INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, EQUIPO_ID, EQUIPO_NOMBRE, CATEGORIA, DORSAL,
					       ESTADO, CONTINUIDAD, FECHA_CONTINUIDAD, USUARIO_APP_CONTINUIDAD_ID, EQUIPO_DEFINITIVO,
					       PENDIENTE_REVISION, NOTA, FECHA_CREACION, FECHA_ACTUALIZACION)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
					f.getJugadorId(), f.getTemporadaId(), f.getEquipoId(), f.getEquipoNombre(), f.getCategoria(),
					f.getDorsal(), estado, continuidad, fechaContinuidad, f.getUsuarioAppContinuidadId(),
					f.isEquipoDefinitivo() ? 1 : 0, f.isPendienteRevision() ? 1 : 0, f.getNota(), ahora, ahora);
		}
	}

	@Override
	public int eliminarPorTemporada(Long temporadaId) {
		return jdbcTemplate.update("DELETE FROM JUGADOR_TEMPORADA WHERE TEMPORADA_ID = ?", temporadaId);
	}

	@Override
	public int eliminarBorradorSinPrueba(Long temporadaId) {
		return jdbcTemplate.update("DELETE FROM JUGADOR_TEMPORADA WHERE TEMPORADA_ID = ? AND ESTADO <> 'PRUEBA'", temporadaId);
	}

	@Override
	public boolean esDePrueba(Long jugadorId) {
		// Solo en el borrador: una temporada no activa que empieza después de la activa
		Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM JUGADOR_TEMPORADA JTP"
				+ " JOIN TEMPORADAS TP ON TP.ID = JTP.TEMPORADA_ID"
				+ " WHERE JTP.JUGADOR_ID = ? AND JTP.ESTADO = 'PRUEBA' AND TP.ACTIVA = 0"
				+ " AND TP.FECHA_INICIO > (SELECT TA.FECHA_INICIO FROM TEMPORADAS TA WHERE TA.ACTIVA = 1)",
				Integer.class, jugadorId);
		return total != null && total > 0;
	}

	@Override
	public List<Jugador> jugadoresActivosSinFila(Long temporadaId) {
		return jdbcTemplate.query("""
				SELECT J.ID, J.NOMBRE, J.APELLIDOS, J.ANIO_NACIMIENTO, J.EQUIPO_ID, E.NOMBRE AS EQUIPO_ACTUAL
				FROM JUGADORES J
				LEFT JOIN EQUIPO E ON E.ID = J.EQUIPO_ID
				WHERE J.FECHA_BAJA IS NULL
				  AND NOT EXISTS (SELECT 1 FROM JUGADOR_TEMPORADA JT
				                   WHERE JT.JUGADOR_ID = J.ID AND JT.TEMPORADA_ID = ?)
				ORDER BY J.APELLIDOS, J.NOMBRE, J.ID""",
				(rs, n) -> {
					Jugador j = new Jugador();
					j.setId(rs.getLong("ID"));
					j.setNombre(rs.getString("NOMBRE"));
					j.setApellidos(rs.getString("APELLIDOS"));
					int anio = rs.getInt("ANIO_NACIMIENTO");
					j.setAnioNacimiento(rs.wasNull() ? null : anio);
					long equipoId = rs.getLong("EQUIPO_ID");
					j.setEquipoId(rs.wasNull() ? null : equipoId);
					j.setEquipo(rs.getString("EQUIPO_ACTUAL"));
					return j;
				},
				temporadaId);
	}

	@Override
	public int contarPorTemporada(Long temporadaId) {
		Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM JUGADOR_TEMPORADA WHERE TEMPORADA_ID = ?",
				Integer.class, temporadaId);
		return total == null ? 0 : total;
	}

	@Override
	public int contarPendientes(Long temporadaId) {
		Integer total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM JUGADOR_TEMPORADA WHERE TEMPORADA_ID = ? AND (PENDIENTE_REVISION = 1"
						+ " OR (ESTADO = 'CONFIRMADO' AND CONTINUIDAD IN ('PENDIENTE', 'DUDA')))",
				Integer.class, temporadaId);
		return total == null ? 0 : total;
	}

	private int contarEnLaActiva(Long equipoId, boolean soloDefinitivos) {
		Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM JUGADOR_TEMPORADA JT"
				+ " JOIN TEMPORADAS T ON T.ID = JT.TEMPORADA_ID"
				+ " WHERE T.ACTIVA = 1 AND JT.EQUIPO_ID = ? AND JT.ESTADO <> 'BAJA'"
				+ (soloDefinitivos ? " AND JT.EQUIPO_DEFINITIVO = 1" : ""), Integer.class, equipoId);
		return total == null ? 0 : total;
	}

	@Override
	public int contarDefinitivosEnLaTemporadaActiva(Long equipoId) {
		return contarEnLaActiva(equipoId, true);
	}

	@Override
	public int contarEnEquipoEnLaTemporadaActiva(Long equipoId) {
		return contarEnLaActiva(equipoId, false);
	}

	@Override
	public boolean existenParaEquipo(Long equipoId) {
		Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM JUGADOR_TEMPORADA WHERE EQUIPO_ID = ?",
				Integer.class, equipoId);
		return total != null && total > 0;
	}

	private static JugadorTemporada mapRow(ResultSet rs, int n) throws SQLException {
		JugadorTemporada f = new JugadorTemporada();
		f.setJugadorId(rs.getLong("JUGADOR_ID"));
		f.setTemporadaId(rs.getLong("TEMPORADA_ID"));
		long equipoId = rs.getLong("EQUIPO_ID");
		f.setEquipoId(rs.wasNull() ? null : equipoId);
		f.setEquipoNombre(rs.getString("EQUIPO_NOMBRE"));
		f.setCategoria(rs.getString("CATEGORIA"));
		int dorsal = rs.getInt("DORSAL");
		f.setDorsal(rs.wasNull() ? null : dorsal);
		f.setPendienteRevision(rs.getInt("PENDIENTE_REVISION") == 1);
		f.setEstado(rs.getString("ESTADO"));
		f.setContinuidad(rs.getString("CONTINUIDAD"));
		Timestamp continuidad = rs.getTimestamp("FECHA_CONTINUIDAD");
		f.setFechaContinuidad(continuidad == null ? null : continuidad.toLocalDateTime());
		long usuarioContinuidad = rs.getLong("USUARIO_APP_CONTINUIDAD_ID");
		f.setUsuarioAppContinuidadId(rs.wasNull() ? null : usuarioContinuidad);
		f.setEquipoDefinitivo(rs.getInt("EQUIPO_DEFINITIVO") == 1);
		f.setNota(rs.getString("NOTA"));
		Timestamp creada = rs.getTimestamp("FECHA_CREACION");
		f.setFechaCreacion(creada == null ? null : creada.toLocalDateTime());
		Timestamp actualizada = rs.getTimestamp("FECHA_ACTUALIZACION");
		f.setFechaActualizacion(actualizada == null ? null : actualizada.toLocalDateTime());
		f.setJugadorNombre(rs.getString("JUGADOR_NOMBRE"));
		f.setJugadorApellidos(rs.getString("JUGADOR_APELLIDOS"));
		int anio = rs.getInt("ANIO_NACIMIENTO");
		f.setAnioNacimiento(rs.wasNull() ? null : anio);
		long actualId = rs.getLong("EQUIPO_ACTUAL_ID");
		f.setEquipoActualId(rs.wasNull() ? null : actualId);
		f.setEquipoActualNombre(rs.getString("EQUIPO_ACTUAL_NOMBRE"));
		f.setTemporadaNombre(rs.getString("TEMPORADA_NOMBRE"));
		f.setJugadorDeBaja(rs.getInt("JUGADOR_DE_BAJA") == 1);
		return f;
	}
}
