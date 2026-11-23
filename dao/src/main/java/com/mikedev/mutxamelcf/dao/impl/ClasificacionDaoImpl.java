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

	/*
	 * El club rival de la fila por su código de club (CODIGO_FFCV es único: una fila como máximo) y la versión de su
	 * escudo si lo tiene. Nunca se lee el BLOB: solo "ESCUDO IS NOT NULL".
	 */
	private static final String CLUB_RIVAL_ID_POR_CODIGO = "(SELECT CR.ID FROM CLUBES_RIVALES CR WHERE CR.CODIGO_FFCV = CLASIFICACIONES.CODIGO_CLUB)";

	private static final String ESCUDO_VERSION_POR_CODIGO = "(SELECT CASE WHEN CR.ESCUDO IS NOT NULL THEN CR.ESCUDO_VERSION END "
			+ "FROM CLUBES_RIVALES CR WHERE CR.CODIGO_FFCV = CLASIFICACIONES.CODIGO_CLUB)";

	private static final String CAMPOS_SELECT = "ID, EQUIPO_ID, COMPETICION, JORNADA, POSICION, NOMBRE_EQUIPO, LETRA, "
			+ "ES_NUESTRO, PUNTOS, PJ, PG, PE, PP, GF, GC, FECHA_ULT_PARTIDO, FECHA_IMPORTACION, CODIGO_CLUB, "
			+ CLUB_RIVAL_ID_POR_CODIGO + " AS CLUB_RIVAL_ID, " + ESCUDO_VERSION_POR_CODIGO + " AS ESCUDO_VERSION";

	private static final String INSERT = "INSERT INTO CLASIFICACIONES (EQUIPO_ID, COMPETICION, JORNADA, POSICION, "
			+ "NOMBRE_EQUIPO, LETRA, ES_NUESTRO, PUNTOS, PJ, PG, PE, PP, GF, GC, FECHA_ULT_PARTIDO, FECHA_IMPORTACION, "
			+ "TEMPORADA_ID, CODIGO_CLUB) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	private final JdbcTemplate jdbcTemplate;

	public ClasificacionDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<Clasificacion> obtenerPorEquipo(Long equipoId) {
		logger.debug("Inicio obtenerPorEquipo: equipoId={}", equipoId);

		// La de la temporada activa (sin temporada activa no se filtra)
		long temporadaId = TemporadaActivaId.obtenerOMenosUno(jdbcTemplate);

		List<Clasificacion> filas = jdbcTemplate.query(
				"SELECT " + CAMPOS_SELECT + " FROM CLASIFICACIONES WHERE EQUIPO_ID = ? AND (? < 0 OR TEMPORADA_ID = ?)"
						+ " ORDER BY POSICION, NOMBRE_EQUIPO, ID",
				ROW_MAPPER, equipoId, temporadaId, temporadaId);

		// Verano: sin partidos en la activa y sin tabla propia, se muestra la final de la temporada anterior
		if (filas.isEmpty()) {
			long anterior = TemporadaActivaId.anteriorSiVerano(jdbcTemplate);

			if (anterior >= 0) {
				filas = obtenerPorEquipoYTemporada(equipoId, anterior);
			}
		}

		logger.debug("Fin obtenerPorEquipo: total={}", filas.size());
		return filas;
	}

	@Override
	public List<Clasificacion> obtenerPorEquipoYTemporada(Long equipoId, Long temporadaId) {
		return jdbcTemplate.query("SELECT " + CAMPOS_SELECT
				+ " FROM CLASIFICACIONES WHERE EQUIPO_ID = ? AND TEMPORADA_ID = ? ORDER BY POSICION, NOMBRE_EQUIPO, ID",
				ROW_MAPPER, equipoId, temporadaId);
	}

	@Override
	public List<Clasificacion> listarPorTemporada(Long temporadaId) {
		return jdbcTemplate.query("SELECT " + CAMPOS_SELECT
				+ " FROM CLASIFICACIONES WHERE TEMPORADA_ID = ? ORDER BY EQUIPO_ID, POSICION, NOMBRE_EQUIPO, ID",
				ROW_MAPPER, temporadaId);
	}

	@Override
	public int eliminarPorEquipo(Long equipoId) {
		// Solo la de la temporada activa: la de las anteriores se conserva
		int filas = jdbcTemplate.update("DELETE FROM CLASIFICACIONES WHERE EQUIPO_ID = ? AND TEMPORADA_ID = ?", equipoId,
				TemporadaActivaId.obtener(jdbcTemplate));
		logger.debug("Clasificación eliminada: equipoId={}, filas={}", equipoId, filas);
		return filas;
	}

	@Override
	public int eliminarPorEquipoYCompeticion(Long equipoId, String competicion) {
		int filas = jdbcTemplate.update(
				"DELETE FROM CLASIFICACIONES WHERE EQUIPO_ID = ? AND COMPETICION = ? AND TEMPORADA_ID = ?", equipoId,
				competicion, TemporadaActivaId.obtener(jdbcTemplate));
		logger.debug("Clasificación de una competición eliminada: equipoId={}, filas={}", equipoId, filas);
		return filas;
	}

	@Override
	public void insertarTodas(List<Clasificacion> filas) {
		logger.debug("Inicio insertarTodas: total={}", filas.size());

		if (filas.isEmpty()) {
			return;
		}

		long temporadaId = TemporadaActivaId.obtener(jdbcTemplate);

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
			ps.setLong(17, temporadaId);
			if (fila.getCodigoClub() != null) {
				ps.setInt(18, fila.getCodigoClub());
			} else {
				ps.setNull(18, Types.NUMERIC);
			}
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
		int codigoClub = rs.getInt("CODIGO_CLUB");
		fila.setCodigoClub(rs.wasNull() ? null : codigoClub);
		long clubRivalId = rs.getLong("CLUB_RIVAL_ID");
		fila.setClubRivalId(rs.wasNull() ? null : clubRivalId);
		long escudoVersion = rs.getLong("ESCUDO_VERSION");
		fila.setEscudoVersion(rs.wasNull() ? null : escudoVersion);
		return fila;
	}

}
