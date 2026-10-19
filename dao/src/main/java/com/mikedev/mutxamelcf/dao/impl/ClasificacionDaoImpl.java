package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ClasificacionDao;
import com.mikedev.mutxamelcf.model.Clasificacion;

@Repository
public class ClasificacionDaoImpl implements ClasificacionDao {

	private static final Logger logger = LoggerFactory.getLogger(ClasificacionDaoImpl.class);

	private static final RowMapper<Clasificacion> ROW_MAPPER = ClasificacionDaoImpl::mapRow;

	private static final String CAMPOS_SELECT = "ID, EQUIPO_ID, COMPETICION, JORNADA, POSICION, NOMBRE_EQUIPO, LETRA, "
			+ "ES_NUESTRO, PUNTOS, PJ, PG, PE, PP, GF, GC, FECHA_ULT_PARTIDO, FECHA_IMPORTACION";

	private static final String INSERT = "INSERT INTO CLASIFICACIONES (EQUIPO_ID, COMPETICION, JORNADA, POSICION, "
			+ "NOMBRE_EQUIPO, LETRA, ES_NUESTRO, PUNTOS, PJ, PG, PE, PP, GF, GC, FECHA_ULT_PARTIDO, FECHA_IMPORTACION) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	private final JdbcTemplate jdbcTemplate;

	public ClasificacionDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<Clasificacion> obtenerPorEquipo(Long equipoId) {
		logger.debug("Inicio obtenerPorEquipo: equipoId={}", equipoId);

		List<Clasificacion> filas = jdbcTemplate.query(
				"SELECT " + CAMPOS_SELECT + " FROM CLASIFICACIONES WHERE EQUIPO_ID = ? ORDER BY POSICION, NOMBRE_EQUIPO, ID",
				ROW_MAPPER, equipoId);

		logger.debug("Fin obtenerPorEquipo: total={}", filas.size());
		return filas;
	}

	@Override
	public int eliminarPorEquipo(Long equipoId) {
		int filas = jdbcTemplate.update("DELETE FROM CLASIFICACIONES WHERE EQUIPO_ID = ?", equipoId);
		logger.debug("Clasificación eliminada: equipoId={}, filas={}", equipoId, filas);
		return filas;
	}

	@Override
	public void insertarTodas(List<Clasificacion> filas) {
		logger.debug("Inicio insertarTodas: total={}", filas.size());

		if (filas.isEmpty()) {
			return;
		}

		jdbcTemplate.batchUpdate(INSERT, filas, filas.size(), (ps, fila) -> {
			ps.setLong(1, fila.getEquipoId());
			ps.setString(2, fila.getCompeticion());
			if (fila.getJornada() != null) {
				ps.setInt(3, fila.getJornada());
			} else {
				ps.setNull(3, Types.NUMERIC);
			}
			ps.setInt(4, fila.getPosicion());
			ps.setString(5, fila.getNombreEquipo());
			if (fila.getLetra() != null) {
				ps.setString(6, fila.getLetra());
			} else {
				ps.setNull(6, Types.VARCHAR);
			}
			ps.setInt(7, fila.isEsNuestro() ? 1 : 0);
			ps.setInt(8, fila.getPuntos());
			ps.setInt(9, fila.getPj());
			ps.setInt(10, fila.getPg());
			ps.setInt(11, fila.getPe());
			ps.setInt(12, fila.getPp());
			ps.setInt(13, fila.getGf());
			ps.setInt(14, fila.getGc());
			if (fila.getFechaUltPartido() != null) {
				ps.setDate(15, Date.valueOf(fila.getFechaUltPartido()));
			} else {
				ps.setNull(15, Types.DATE);
			}
			ps.setTimestamp(16, Timestamp.valueOf(fila.getFechaImportacion()));
		});

		logger.debug("Fin insertarTodas");
	}

	private static Clasificacion mapRow(ResultSet rs, int rowNum) throws SQLException {
		Clasificacion fila = new Clasificacion();
		fila.setId(rs.getLong("ID"));
		fila.setEquipoId(rs.getLong("EQUIPO_ID"));
		fila.setCompeticion(rs.getString("COMPETICION"));
		int jornada = rs.getInt("JORNADA");
		fila.setJornada(rs.wasNull() ? null : jornada);
		fila.setPosicion(rs.getInt("POSICION"));
		fila.setNombreEquipo(rs.getString("NOMBRE_EQUIPO"));
		fila.setLetra(rs.getString("LETRA"));
		fila.setEsNuestro(rs.getInt("ES_NUESTRO") == 1);
		fila.setPuntos(rs.getInt("PUNTOS"));
		fila.setPj(rs.getInt("PJ"));
		fila.setPg(rs.getInt("PG"));
		fila.setPe(rs.getInt("PE"));
		fila.setPp(rs.getInt("PP"));
		fila.setGf(rs.getInt("GF"));
		fila.setGc(rs.getInt("GC"));
		Date ultPartido = rs.getDate("FECHA_ULT_PARTIDO");
		fila.setFechaUltPartido(ultPartido == null ? null : ultPartido.toLocalDate());
		Timestamp importacion = rs.getTimestamp("FECHA_IMPORTACION");
		fila.setFechaImportacion(importacion == null ? null : importacion.toLocalDateTime());
		return fila;
	}

}
