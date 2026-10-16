package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.DescansoEquipoDao;
import com.mikedev.mutxamelcf.model.DescansoEquipo;

@Repository
public class DescansoEquipoDaoImpl implements DescansoEquipoDao {

	private static final Logger logger = LoggerFactory.getLogger(DescansoEquipoDaoImpl.class);

	private static final RowMapper<DescansoEquipo> ROW_MAPPER = DescansoEquipoDaoImpl::mapRow;

	private static final String CAMPOS_SELECT = "ID, EQUIPO_ID, FECHA, JORNADA";

	private final JdbcTemplate jdbcTemplate;

	public DescansoEquipoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<DescansoEquipo> obtenerPorRangoFechas(LocalDate desde, LocalDate hasta) {
		logger.debug("Inicio obtenerPorRangoFechas: desde={}, hasta={}", desde, hasta);

		List<DescansoEquipo> descansos = jdbcTemplate.query(
				"SELECT " + CAMPOS_SELECT
						+ " FROM DESCANSOS_EQUIPO WHERE FECHA BETWEEN ? AND ? ORDER BY FECHA, EQUIPO_ID",
				ROW_MAPPER, java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta));

		logger.debug("Fin obtenerPorRangoFechas: total={}", descansos.size());
		return descansos;
	}

	@Override
	public List<DescansoEquipo> obtenerDesde(Long equipoId, LocalDate desde) {
		return jdbcTemplate.query(
				"SELECT " + CAMPOS_SELECT
						+ " FROM DESCANSOS_EQUIPO WHERE EQUIPO_ID = ? AND FECHA >= ? ORDER BY FECHA",
				ROW_MAPPER, equipoId, java.sql.Date.valueOf(desde));
	}

	@Override
	public void crear(DescansoEquipo descanso) {
		jdbcTemplate.update(
				"INSERT INTO DESCANSOS_EQUIPO (EQUIPO_ID, FECHA, JORNADA) VALUES (?, ?, ?)",
				descanso.getEquipoId(), java.sql.Date.valueOf(descanso.getFecha()), descanso.getJornada());
		logger.info("Descanso creado: equipoId={}, fecha={}", descanso.getEquipoId(), descanso.getFecha());
	}

	@Override
	public void eliminar(Long id) {
		int filas = jdbcTemplate.update("DELETE FROM DESCANSOS_EQUIPO WHERE ID = ?", id);
		logger.info("Descanso eliminado: id={}, filasAfectadas={}", id, filas);
	}

	private static DescansoEquipo mapRow(ResultSet rs, int rowNum) throws SQLException {
		DescansoEquipo descanso = new DescansoEquipo();
		descanso.setId(rs.getLong("ID"));
		descanso.setEquipoId(rs.getLong("EQUIPO_ID"));
		descanso.setFecha(rs.getDate("FECHA").toLocalDate());
		int jornada = rs.getInt("JORNADA");
		descanso.setJornada(rs.wasNull() ? null : jornada);
		return descanso;
	}

}
