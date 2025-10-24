package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.CuerpoTecnicoDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;

@Repository
public class CuerpoTecnicoDaoImpl implements CuerpoTecnicoDao {

	private final JdbcTemplate jdbcTemplate;

	public CuerpoTecnicoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean guardar(CuerpoTecnico cuerpoTecnico) {
		if (obtenerPorId(cuerpoTecnico.getId()) != null) {
			return actualizarCuerpoTecnico(cuerpoTecnico);
		} else {
			return insertarCuerpoTecnico(cuerpoTecnico);
		}
	}

	public boolean insertarCuerpoTecnico(CuerpoTecnico cuerpoTecnico) {
		String sql = "INSERT INTO cuerpo_tecnico (dni, nombre, apellidos, fecha_nacimiento, poblacion, "
				+ "nacionalidad, categoria, deporte, equipo, puesto, foto) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		return (jdbcTemplate.update(sql, cuerpoTecnico.getDni(), cuerpoTecnico.getNombre(),
				cuerpoTecnico.getApellidos(), cuerpoTecnico.getFechaNacimiento(), cuerpoTecnico.getPoblacion(),
				cuerpoTecnico.getNacionalidad(), cuerpoTecnico.getCategoria(), cuerpoTecnico.getDeporte(),
				cuerpoTecnico.getEquipo(), cuerpoTecnico.getPuesto(), cuerpoTecnico.getFoto()) == 1);
	}

	public boolean actualizarCuerpoTecnico(CuerpoTecnico cuerpoTecnico) {
		String sql = " UPDATE cuerpo_tecnico SET NOMBRE = ?, APELLIDOS = ?, PUESTO = ?, EQUIPO = ?, "
				+ "CATEGORIA = ?, DEPORTE = ?, FOTO = ? WHERE ID = ? ";

		return jdbcTemplate.update(sql, cuerpoTecnico.getNombre(), cuerpoTecnico.getApellidos(),
				cuerpoTecnico.getPuesto(), cuerpoTecnico.getEquipo(), cuerpoTecnico.getCategoria(),
				cuerpoTecnico.getDeporte(), cuerpoTecnico.getFoto(), cuerpoTecnico.getId()) == 1;
	}

	@SuppressWarnings("deprecation")
	@Override
	public CuerpoTecnico obtenerPorId(Long id) {
		String sql = "SELECT * FROM cuerpo_tecnico WHERE id = ?";

		try {
			return jdbcTemplate.queryForObject(sql, new Object[] { id }, new RowMapper<CuerpoTecnico>() {
				@Override
				public CuerpoTecnico mapRow(ResultSet rs, int rowNum) throws SQLException {
					CuerpoTecnico cuerpoTecnico = new CuerpoTecnico();
					cuerpoTecnico.setId(rs.getLong("id"));
					cuerpoTecnico.setNombre(rs.getString("nombre"));
					cuerpoTecnico.setApellidos(rs.getString("apellidos"));
					cuerpoTecnico.setCategoria(rs.getString("categoria"));
					cuerpoTecnico.setDeporte(rs.getString("deporte"));
					cuerpoTecnico.setEquipo(rs.getString("equipo"));
					cuerpoTecnico.setPuesto(rs.getString("puesto"));
					cuerpoTecnico.setFoto(rs.getBytes("foto"));
					return cuerpoTecnico;
				}
			});
		} catch (EmptyResultDataAccessException e) {
			return null; // No se encontró ningún jugador con ese id
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public List<CuerpoTecnico> obtenerTodosPorCategoria(String categoria) {
		String sql = "SELECT * FROM cuerpo_tecnico WHERE categoria = ? ORDER BY deporte, equipo, apellidos";

		return jdbcTemplate.query(sql, new Object[] { categoria }, new RowMapper<CuerpoTecnico>() {
			@Override
			public CuerpoTecnico mapRow(ResultSet rs, int rowNum) throws SQLException {
				CuerpoTecnico cuerpoTecnico = new CuerpoTecnico();
				cuerpoTecnico.setId(rs.getLong("id"));
				cuerpoTecnico.setNombre(rs.getString("nombre"));
				cuerpoTecnico.setApellidos(rs.getString("apellidos"));
				cuerpoTecnico.setCategoria(rs.getString("categoria"));
				cuerpoTecnico.setDeporte(rs.getString("deporte"));
				cuerpoTecnico.setEquipo(rs.getString("equipo"));
				cuerpoTecnico.setPuesto(rs.getString("puesto"));
				cuerpoTecnico.setFoto(rs.getBytes("foto"));
				return cuerpoTecnico;
			}
		});
	}

	@SuppressWarnings("deprecation")
	@Override
	public List<CuerpoTecnico> obtenerTodosPorEquipo(String equipo) {
		String sql = "SELECT * FROM cuerpo_tecnico WHERE equipo = ? ORDER BY equipo, apellidos";

		return jdbcTemplate.query(sql, new Object[] { equipo }, new RowMapper<CuerpoTecnico>() {
			@Override
			public CuerpoTecnico mapRow(ResultSet rs, int rowNum) throws SQLException {
				CuerpoTecnico cuerpoTecnico = new CuerpoTecnico();
				cuerpoTecnico.setId(rs.getLong("id"));
				cuerpoTecnico.setNombre(rs.getString("nombre"));
				cuerpoTecnico.setApellidos(rs.getString("apellidos"));
				cuerpoTecnico.setCategoria(rs.getString("categoria"));
				cuerpoTecnico.setDeporte(rs.getString("deporte"));
				cuerpoTecnico.setEquipo(rs.getString("equipo"));
				cuerpoTecnico.setPuesto(rs.getString("puesto"));
				cuerpoTecnico.setFoto(rs.getBytes("foto"));
				return cuerpoTecnico;
			}
		});
	}

	@Override
	public void eliminar(Long id) {
		String sql = "DELETE FROM cuerpo_tecnico WHERE id = ?";
		jdbcTemplate.update(sql, id);
	}

	@Override
	public List<CuerpoTecnico> obtenerTodos() {
		String sql = "SELECT * FROM cuerpo_tecnico ORDER BY deporte, equipo, apellidos";

		return jdbcTemplate.query(sql, new RowMapper<CuerpoTecnico>() {
			@Override
			public CuerpoTecnico mapRow(ResultSet rs, int rowNum) throws SQLException {
				CuerpoTecnico cuerpoTecnico = new CuerpoTecnico();
				cuerpoTecnico.setId(rs.getLong("id"));
				cuerpoTecnico.setNombre(rs.getString("nombre"));
				cuerpoTecnico.setApellidos(rs.getString("apellidos"));
				cuerpoTecnico.setCategoria(rs.getString("categoria"));
				cuerpoTecnico.setDeporte(rs.getString("deporte"));
				cuerpoTecnico.setEquipo(rs.getString("equipo"));
				cuerpoTecnico.setPuesto(rs.getString("puesto"));
				cuerpoTecnico.setFoto(rs.getBytes("foto"));
				return cuerpoTecnico;
			}
		});
	}
}
