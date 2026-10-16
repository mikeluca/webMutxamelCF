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

import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.model.EquipoFfcv;

@Repository
public class EquipoFfcvDaoImpl implements EquipoFfcvDao {

	private static final Logger logger = LoggerFactory.getLogger(EquipoFfcvDaoImpl.class);

	private static final RowMapper<EquipoFfcv> ROW_MAPPER = EquipoFfcvDaoImpl::mapRow;

	private static final String CAMPOS_SELECT = "ID, EQUIPO_ID, COMPETICION, LETRA";

	private final JdbcTemplate jdbcTemplate;

	public EquipoFfcvDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<EquipoFfcv> listarTodos() {
		logger.debug("Inicio listarTodos");

		List<EquipoFfcv> relaciones = jdbcTemplate.query(
				"SELECT " + CAMPOS_SELECT + " FROM EQUIPOS_FFCV ORDER BY COMPETICION, LETRA", ROW_MAPPER);

		logger.debug("Fin listarTodos: total={}", relaciones.size());
		return relaciones;
	}

	@Override
	public EquipoFfcv obtenerPorId(Long id) {
		List<EquipoFfcv> resultado = jdbcTemplate.query(
				"SELECT " + CAMPOS_SELECT + " FROM EQUIPOS_FFCV WHERE ID = ?", ROW_MAPPER, id);
		return resultado.isEmpty() ? null : resultado.get(0);
	}

	@Override
	public EquipoFfcv obtenerPorCompeticionYLetra(String competicion, String letra) {
		logger.debug("Inicio obtenerPorCompeticionYLetra: competicion={}, letra={}", competicion, letra);

		/*
		 * LETRA puede ser NULL (equipo sin letra, p.ej. el primer equipo) y
		 * "= NULL" nunca es cierto en SQL, de ahí las dos consultas.
		 */
		List<EquipoFfcv> resultado = letra == null
				? jdbcTemplate.query(
						"SELECT " + CAMPOS_SELECT + " FROM EQUIPOS_FFCV WHERE COMPETICION = ? AND LETRA IS NULL",
						ROW_MAPPER, competicion)
				: jdbcTemplate.query(
						"SELECT " + CAMPOS_SELECT + " FROM EQUIPOS_FFCV WHERE COMPETICION = ? AND LETRA = ?",
						ROW_MAPPER, competicion, letra);

		return resultado.isEmpty() ? null : resultado.get(0);
	}

	@Override
	public EquipoFfcv crear(EquipoFfcv equipoFfcv) {
		logger.debug("Inicio crear: equipoId={}", equipoFfcv.getEquipoId());

		KeyHolder keyHolder = new GeneratedKeyHolder();

		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
					"INSERT INTO EQUIPOS_FFCV (EQUIPO_ID, COMPETICION, LETRA) VALUES (?, ?, ?)",
					new String[] { "ID" });
			ps.setLong(1, equipoFfcv.getEquipoId());
			ps.setString(2, equipoFfcv.getCompeticion());
			if (equipoFfcv.getLetra() != null) {
				ps.setString(3, equipoFfcv.getLetra());
			} else {
				ps.setNull(3, Types.VARCHAR);
			}
			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();

		if (key == null) {
			throw new IllegalStateException("No se pudo obtener el ID generado de la relación FFCV");
		}

		equipoFfcv.setId(key.longValue());
		logger.info("Relación FFCV creada: id={}, equipoId={}", equipoFfcv.getId(), equipoFfcv.getEquipoId());
		return equipoFfcv;
	}

	@Override
	public void actualizar(EquipoFfcv equipoFfcv) {
		int filas = jdbcTemplate.update(
				"UPDATE EQUIPOS_FFCV SET EQUIPO_ID = ?, COMPETICION = ?, LETRA = ? WHERE ID = ?",
				equipoFfcv.getEquipoId(), equipoFfcv.getCompeticion(), equipoFfcv.getLetra(), equipoFfcv.getId());
		logger.info("Relación FFCV actualizada: id={}, filasAfectadas={}", equipoFfcv.getId(), filas);
	}

	@Override
	public void eliminar(Long id) {
		int filas = jdbcTemplate.update("DELETE FROM EQUIPOS_FFCV WHERE ID = ?", id);
		logger.info("Relación FFCV eliminada: id={}, filasAfectadas={}", id, filas);
	}

	private static EquipoFfcv mapRow(ResultSet rs, int rowNum) throws SQLException {
		EquipoFfcv relacion = new EquipoFfcv();
		relacion.setId(rs.getLong("ID"));
		relacion.setEquipoId(rs.getLong("EQUIPO_ID"));
		relacion.setCompeticion(rs.getString("COMPETICION"));
		relacion.setLetra(rs.getString("LETRA"));
		return relacion;
	}

}
