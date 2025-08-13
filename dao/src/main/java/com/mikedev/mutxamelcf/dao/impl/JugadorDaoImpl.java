package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.model.Jugador;

@Repository
public class JugadorDaoImpl implements JugadorDao {

	private final JdbcTemplate jdbcTemplate;

	public JugadorDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean guardarJugador(Jugador jugador) {
		String sql = "INSERT INTO jugadores (dni, nombre, apellidos, fecha_nacimiento, poblacion, "
				+ "nacionalidad, categoria, deporte, dorsal, posicion, equipo, foto) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		return (jdbcTemplate.update(sql, jugador.getDni(), jugador.getNombre(), jugador.getApellidos(),
				jugador.getFechaNacimiento(), jugador.getPoblacion(), jugador.getNacionalidad(), jugador.getCategoria(),
				jugador.getDeporte(), jugador.getDorsal(), jugador.getPosicion(), jugador.getEquipo(),
				jugador.getFoto()) == 1);
	}

	@SuppressWarnings("deprecation")
	@Override
	public Jugador obtenerPorId(Long id) {
		String sql = "SELECT * FROM jugadores WHERE id = ?";

		return jdbcTemplate.queryForObject(sql, new Object[] { id }, new RowMapper<Jugador>() {
			@Override
			public Jugador mapRow(ResultSet rs, int rowNum) throws SQLException {
				Jugador jugador = new Jugador();
				jugador.setId(rs.getLong("id"));
				jugador.setNombre(rs.getString("nombre"));
				jugador.setApellidos(rs.getString("apellidos"));
				jugador.setCategoria(rs.getString("categoria"));
				jugador.setDeporte(rs.getString("deporte"));
				jugador.setEquipo(rs.getString("equipo"));
				jugador.setDorsal(rs.getInt("dorsal"));
				jugador.setPosicion(rs.getString("posicion"));
				jugador.setFoto(rs.getBytes("foto"));
				return jugador;
			}
		});
	}

	@SuppressWarnings("deprecation")
	@Override
	public List<Jugador> obtenerTodosPorCategoria(String categoria) {
		String sql = "SELECT * FROM jugadores WHERE categoria = ? ORDER BY deporte, equipo, apellidos";

		return jdbcTemplate.query(sql, new Object[] { categoria }, new RowMapper<Jugador>() {
			@Override
			public Jugador mapRow(ResultSet rs, int rowNum) throws SQLException {
				Jugador jugador = new Jugador();
				jugador.setId(rs.getLong("id"));
				jugador.setNombre(rs.getString("nombre"));
				jugador.setApellidos(rs.getString("apellidos"));
				jugador.setCategoria(rs.getString("categoria"));
				jugador.setDeporte(rs.getString("deporte"));
				jugador.setEquipo(rs.getString("equipo"));
				jugador.setDorsal(rs.getInt("dorsal"));
				jugador.setPosicion(rs.getString("posicion"));
				jugador.setFoto(rs.getBytes("foto"));
				return jugador;
			}
		});
	}

	@SuppressWarnings("deprecation")
	@Override
	public List<Jugador> obtenerTodosPorEquipo(String equipo) {
		String sql = "SELECT * FROM jugadores WHERE equipo = ? ORDER BY equipo, apellidos";

		return jdbcTemplate.query(sql, new Object[] { equipo }, new RowMapper<Jugador>() {
			@Override
			public Jugador mapRow(ResultSet rs, int rowNum) throws SQLException {
				Jugador jugador = new Jugador();
				jugador.setId(rs.getLong("id"));
				jugador.setNombre(rs.getString("nombre"));
				jugador.setApellidos(rs.getString("apellidos"));
				jugador.setCategoria(rs.getString("categoria"));
				jugador.setDeporte(rs.getString("deporte"));
				jugador.setEquipo(rs.getString("equipo"));
				jugador.setDorsal(rs.getInt("dorsal"));
				jugador.setPosicion(rs.getString("posicion"));
				jugador.setFoto(rs.getBytes("foto"));
				return jugador;
			}
		});
	}

	@Override
	public void eliminar(Long id) {
		String sql = "DELETE FROM jugadores WHERE id = ?";
		jdbcTemplate.update(sql, id);
	}

	@Override
	public List<Jugador> obtenerTodos() {
		String sql = "SELECT * FROM jugadores ORDER BY deporte, equipo, apellidos";

		return jdbcTemplate.query(sql, new RowMapper<Jugador>() {
			@Override
			public Jugador mapRow(ResultSet rs, int rowNum) throws SQLException {
				Jugador jugador = new Jugador();
				jugador.setId(rs.getLong("id"));
				jugador.setNombre(rs.getString("nombre"));
				jugador.setApellidos(rs.getString("apellidos"));
				jugador.setCategoria(rs.getString("categoria"));
				jugador.setDeporte(rs.getString("deporte"));
				jugador.setEquipo(rs.getString("equipo"));
				jugador.setDorsal(rs.getInt("dorsal"));
				jugador.setPosicion(rs.getString("posicion"));
				jugador.setFoto(rs.getBytes("foto"));
				return jugador;
			}
		});
	}
}
