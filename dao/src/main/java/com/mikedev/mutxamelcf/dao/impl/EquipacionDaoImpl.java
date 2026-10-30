package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.EquipacionDao;
import com.mikedev.mutxamelcf.model.Equipacion;

@Repository
public class EquipacionDaoImpl implements EquipacionDao {

	private static final String COLUMNAS = "E.ID, E.NOMBRE, E.CAMISETA, E.PANTALON, E.MEDIAS, E.COLOR_HEX, E.ORDEN, E.ACTIVA";

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<Equipacion> rowMapper = (rs, n) -> {
		Equipacion e = new Equipacion();
		e.setId(rs.getLong("ID"));
		e.setNombre(rs.getString("NOMBRE"));
		e.setCamiseta(rs.getString("CAMISETA"));
		e.setPantalon(rs.getString("PANTALON"));
		e.setMedias(rs.getString("MEDIAS"));
		e.setColorHex(rs.getString("COLOR_HEX"));
		e.setOrden(rs.getInt("ORDEN"));
		e.setActiva(rs.getInt("ACTIVA") == 1);
		return e;
	};

	public EquipacionDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<Equipacion> obtenerTodas() {
		return jdbcTemplate.query("SELECT " + COLUMNAS + ", ("
				+ "SELECT COUNT(*) FROM CONVOCATORIAS C WHERE C.EQUIPACION_ID = E.ID OR C.EQUIPACION_MOCHILA_ID = E.ID"
				+ ") AS USADAS FROM EQUIPACIONES E ORDER BY E.ORDEN, E.NOMBRE, E.ID", (rs, n) -> {
					Equipacion e = rowMapper.mapRow(rs, n);
					e.setConvocatoriasUsadas(rs.getInt("USADAS"));
					return e;
				});
	}

	@Override
	public List<Equipacion> obtenerActivas() {
		return jdbcTemplate.query(
				"SELECT " + COLUMNAS + " FROM EQUIPACIONES E WHERE E.ACTIVA = 1 ORDER BY E.ORDEN, E.NOMBRE, E.ID",
				rowMapper);
	}

	@Override
	public Equipacion obtenerPorId(Long id) {
		List<Equipacion> r = jdbcTemplate.query("SELECT " + COLUMNAS + " FROM EQUIPACIONES E WHERE E.ID = ?", rowMapper,
				id);
		return r.isEmpty() ? null : r.get(0);
	}

	@Override
	public Equipacion obtenerPorNombre(String nombre) {
		List<Equipacion> r = jdbcTemplate.query("SELECT " + COLUMNAS + " FROM EQUIPACIONES E WHERE E.NOMBRE = ?",
				rowMapper, nombre);
		return r.isEmpty() ? null : r.get(0);
	}

	@Override
	public Equipacion crear(Equipacion e) {
		KeyHolder keyHolder = new GeneratedKeyHolder();

		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
					"INSERT INTO EQUIPACIONES (NOMBRE, CAMISETA, PANTALON, MEDIAS, COLOR_HEX, ORDEN, ACTIVA)"
							+ " VALUES (?, ?, ?, ?, ?, ?, ?)",
					new String[] { "ID" });
			ps.setString(1, e.getNombre());
			ps.setString(2, e.getCamiseta());
			ps.setString(3, e.getPantalon());
			ps.setString(4, e.getMedias());
			ps.setString(5, e.getColorHex());
			ps.setInt(6, e.getOrden());
			ps.setInt(7, e.isActiva() ? 1 : 0);
			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();

		if (key == null) {
			throw new IllegalStateException("No se pudo obtener el ID generado del equipaje");
		}

		e.setId(key.longValue());
		return e;
	}

	@Override
	public void actualizar(Equipacion e) {
		jdbcTemplate.update(
				"UPDATE EQUIPACIONES SET NOMBRE = ?, CAMISETA = ?, PANTALON = ?, MEDIAS = ?, COLOR_HEX = ?, ORDEN = ?,"
						+ " ACTIVA = ? WHERE ID = ?",
				e.getNombre(), e.getCamiseta(), e.getPantalon(), e.getMedias(), e.getColorHex(), e.getOrden(),
				e.isActiva() ? 1 : 0, e.getId());
	}

	@Override
	public int contarConvocatorias(Long id) {
		Integer n = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM CONVOCATORIAS WHERE EQUIPACION_ID = ? OR EQUIPACION_MOCHILA_ID = ?",
				Integer.class, id, id);
		return n == null ? 0 : n;
	}

	@Override
	public void eliminar(Long id) {
		jdbcTemplate.update("DELETE FROM EQUIPACIONES WHERE ID = ?", id);
	}
}
