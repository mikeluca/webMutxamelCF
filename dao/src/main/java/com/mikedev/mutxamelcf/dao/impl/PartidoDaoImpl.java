package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.Partido;

@Repository
public class PartidoDaoImpl implements PartidoDao {

	private static final Logger logger = LoggerFactory.getLogger(PartidoDaoImpl.class);

	private static final RowMapper<Partido> PARTIDO_ROW_MAPPER = PartidoDaoImpl::mapRow;

	private static final String CAMPOS_SELECT = """
			P.ID, P.EQUIPO_ID, P.RIVAL, P.DIA, P.HORA, P.CAMPO, P.RESULTADO,
			P.USUARIO_ACTUALIZO_ID, P.FECHA_ACTUALIZACION
			""";

	private final JdbcTemplate jdbcTemplate;

	public PartidoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Partido crear(Partido partido) {
		logger.debug("Inicio crear: equipoId={}, rival={}", partido.getEquipoId(), partido.getRival());

		String sql = """
				INSERT INTO PARTIDOS (
				    EQUIPO_ID,
				    RIVAL,
				    DIA,
				    HORA,
				    CAMPO,
				    RESULTADO
				)
				VALUES (?, ?, ?, ?, ?, ?)
				""";

		KeyHolder keyHolder = new GeneratedKeyHolder();

		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
					sql,
					new String[] { "ID" });

			ps.setLong(1, partido.getEquipoId());
			ps.setString(2, partido.getRival());

			if (partido.getDia() != null) {
				ps.setDate(3, new java.sql.Date(partido.getDia().getTime()));
			} else {
				ps.setNull(3, Types.DATE);
			}

			ps.setString(4, partido.getHora());
			ps.setString(5, partido.getCampo());
			ps.setString(6, partido.getResultado());

			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();

		if (key == null) {
			throw new IllegalStateException(
					"No se pudo obtener el ID generado del partido");
		}

		partido.setId(key.longValue());

		logger.info("Partido creado: id={}, equipoId={}", partido.getId(), partido.getEquipoId());
		logger.debug("Fin crear: id={}", partido.getId());

		return partido;
	}

	@Override
	public void actualizar(Partido partido) {
		logger.debug("Inicio actualizar: id={}", partido.getId());

		String sql = """
				UPDATE PARTIDOS
				SET RIVAL = ?,
				    DIA = ?,
				    HORA = ?,
				    CAMPO = ?,
				    RESULTADO = ?,
				    USUARIO_ACTUALIZO_ID = ?,
				    FECHA_ACTUALIZACION = ?
				WHERE ID = ?
				""";

		java.sql.Date diaSQL = partido.getDia() != null ? new java.sql.Date(partido.getDia().getTime()) : null;

		int filasAfectadas = jdbcTemplate.update(
				sql,
				partido.getRival(),
				diaSQL,
				partido.getHora(),
				partido.getCampo(),
				partido.getResultado(),
				partido.getUsuarioActualizoId(),
				partido.getFechaActualizacion(),
				partido.getId());

		logger.info("Partido actualizado: id={}, filasAfectadas={}", partido.getId(), filasAfectadas);
		logger.debug("Fin actualizar: id={}", partido.getId());
	}

	@Override
	public Partido obtenerPorId(Long id) {
		logger.debug("Inicio obtenerPorId: id={}", id);

		String sql = """
				SELECT ID, EQUIPO_ID, RIVAL, DIA, HORA, CAMPO, RESULTADO,
				       USUARIO_ACTUALIZO_ID, FECHA_ACTUALIZACION
				FROM PARTIDOS
				WHERE ID = ?
				""";

		List<Partido> resultado = jdbcTemplate.query(sql, PARTIDO_ROW_MAPPER, id);

		Partido partido = resultado.isEmpty() ? null : resultado.get(0);

		logger.debug("Fin obtenerPorId: id={}, encontrado={}", id, partido != null);

		return partido;
	}

	@Override
	public List<Partido> obtenerPorEquipo(Long equipoId) {
		logger.debug("Inicio obtenerPorEquipo: equipoId={}", equipoId);

		String sql = """
				SELECT ID, EQUIPO_ID, RIVAL, DIA, HORA, CAMPO, RESULTADO,
				       USUARIO_ACTUALIZO_ID, FECHA_ACTUALIZACION
				FROM PARTIDOS
				WHERE EQUIPO_ID = ?
				ORDER BY DIA DESC, ID DESC
				""";

		List<Partido> partidos = jdbcTemplate.query(sql, PARTIDO_ROW_MAPPER, equipoId);

		logger.debug("Fin obtenerPorEquipo: equipoId={}, total={}", equipoId, partidos.size());

		return partidos;
	}

	@Override
	public List<Partido> obtenerUltimosPorEquipo(Long equipoId, int limite) {
		logger.debug("Inicio obtenerUltimosPorEquipo: equipoId={}, limite={}", equipoId, limite);

		String sql = """
				SELECT ID, EQUIPO_ID, RIVAL, DIA, HORA, CAMPO, RESULTADO,
				       USUARIO_ACTUALIZO_ID, FECHA_ACTUALIZACION
				FROM PARTIDOS
				WHERE EQUIPO_ID = ?
				ORDER BY DIA DESC, ID DESC
				FETCH FIRST ? ROWS ONLY
				""";

		List<Partido> partidos = jdbcTemplate.query(sql, PARTIDO_ROW_MAPPER, equipoId, limite);

		logger.debug("Fin obtenerUltimosPorEquipo: equipoId={}, total={}", equipoId, partidos.size());

		return partidos;
	}

	@Override
	public List<Partido> obtenerPorDeporte(String deporte, int limitePorEquipo) {
		logger.debug("Inicio obtenerPorDeporte: deporte={}, limitePorEquipo={}", deporte, limitePorEquipo);

		/*
		 * Acotamos a un máximo de 'limitePorEquipo' partidos por equipo
		 * mediante una subconsulta correlacionada (portable entre motores
		 * SQL, sin depender de funciones de ventana): contamos cuántos
		 * partidos del mismo equipo son "más recientes" que el partido
		 * actual y solo nos quedamos con los que ocupan una posición por
		 * debajo del límite.
		 */
		String sql = "SELECT " + CAMPOS_SELECT + """
				FROM PARTIDOS P
				INNER JOIN EQUIPO E ON E.ID = P.EQUIPO_ID
				WHERE E.DEPORTE = ?
				  AND (
				        SELECT COUNT(*)
				        FROM PARTIDOS P2
				        WHERE P2.EQUIPO_ID = P.EQUIPO_ID
				          AND (P2.DIA > P.DIA OR (P2.DIA = P.DIA AND P2.ID > P.ID))
				      ) < ?
				ORDER BY E.ORDEN, E.NOMBRE, P.DIA DESC, P.ID DESC
				""";

		List<Partido> partidos = jdbcTemplate.query(sql, PARTIDO_ROW_MAPPER, deporte, limitePorEquipo);

		logger.debug("Fin obtenerPorDeporte: deporte={}, total={}", deporte, partidos.size());

		return partidos;
	}

	@Override
	public Partido obtenerMasRelevantePorEquipoNombre(String equipoNombre, String categoria) {
		logger.debug("Inicio obtenerMasRelevantePorEquipoNombre: equipoNombre={}, categoria={}", equipoNombre,
				categoria);

		/*
		 * Criterio: el próximo partido sin resultado (si lo hay) o, si no
		 * hay ninguno futuro, el último jugado.
		 */
		String sqlProximo = "SELECT " + CAMPOS_SELECT + """
				FROM PARTIDOS P
				INNER JOIN EQUIPO E ON E.ID = P.EQUIPO_ID
				WHERE UPPER(TRIM(E.NOMBRE)) = UPPER(TRIM(?))
				  AND E.CATEGORIA = ?
				  AND P.RESULTADO IS NULL
				  AND P.DIA >= TRUNC(SYSDATE)
				ORDER BY P.DIA ASC, P.ID ASC
				FETCH FIRST 1 ROW ONLY
				""";

		List<Partido> proximos = jdbcTemplate.query(sqlProximo, PARTIDO_ROW_MAPPER, equipoNombre, categoria);

		if (!proximos.isEmpty()) {
			logger.debug("Fin obtenerMasRelevantePorEquipoNombre: encontrado=proximo");
			return proximos.get(0);
		}

		String sqlUltimo = "SELECT " + CAMPOS_SELECT + """
				FROM PARTIDOS P
				INNER JOIN EQUIPO E ON E.ID = P.EQUIPO_ID
				WHERE UPPER(TRIM(E.NOMBRE)) = UPPER(TRIM(?))
				  AND E.CATEGORIA = ?
				  AND P.RESULTADO IS NOT NULL
				ORDER BY P.DIA DESC, P.ID DESC
				FETCH FIRST 1 ROW ONLY
				""";

		List<Partido> ultimos = jdbcTemplate.query(sqlUltimo, PARTIDO_ROW_MAPPER, equipoNombre, categoria);

		Partido resultado = ultimos.isEmpty() ? null : ultimos.get(0);

		logger.debug("Fin obtenerMasRelevantePorEquipoNombre: encontrado={}", resultado != null);

		return resultado;
	}

	private static Partido mapRow(ResultSet rs, int rowNum) throws SQLException {

		Partido partido = new Partido();

		partido.setId(rs.getLong("ID"));
		partido.setEquipoId(rs.getLong("EQUIPO_ID"));
		partido.setRival(rs.getString("RIVAL"));
		partido.setDia(rs.getDate("DIA"));
		partido.setHora(rs.getString("HORA"));
		partido.setCampo(rs.getString("CAMPO"));
		partido.setResultado(rs.getString("RESULTADO"));

		long usuarioActualizoId = rs.getLong("USUARIO_ACTUALIZO_ID");
		partido.setUsuarioActualizoId(rs.wasNull() ? null : usuarioActualizoId);

		partido.setFechaActualizacion(rs.getTimestamp("FECHA_ACTUALIZACION"));

		return partido;
	}

}
