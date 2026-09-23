package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.model.Jugador;

@Repository
public class JugadorDaoImpl implements JugadorDao {

	private static final Logger logger = LoggerFactory.getLogger(JugadorDaoImpl.class);

	private static final RowMapper<Jugador> JUGADOR_ROW_MAPPER = JugadorDaoImpl::mapRow;

	private final JdbcTemplate jdbcTemplate;

	public JugadorDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean guardarJugador(Jugador jugador) {
		logger.debug("Inicio guardarJugador: id={}, dni={}", jugador.getId(), jugador.getDni());
		boolean existe = obtenerPorId(jugador.getId()) != null;
		boolean resultado = existe ? actualizarJugador(jugador) : insertarJugador(jugador);
		logger.debug("Fin guardarJugador: existia={}, resultado={}", existe, resultado);
		return resultado;
	}

	public boolean insertarJugador(Jugador jugador) {
		logger.debug("Inicio insertarJugador: dni={}", jugador.getDni());
		String sql = "INSERT INTO jugadores (dni, nombre, apellidos, fecha_nacimiento, poblacion, "
				+ "nacionalidad, categoria, deporte, dorsal, posicion, equipo, foto) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		boolean insertado = jdbcTemplate.update(sql, jugador.getDni(), jugador.getNombre(), jugador.getApellidos(),
				jugador.getFechaNacimiento(), jugador.getPoblacion(), jugador.getNacionalidad(), jugador.getCategoria(),
				jugador.getDeporte(), jugador.getDorsal(), jugador.getPosicion(), jugador.getEquipo(),
				jugador.getFoto()) == 1;
		logger.info("Jugador insertado: dni={}, insertado={}", jugador.getDni(), insertado);
		logger.debug("Fin insertarJugador: insertado={}", insertado);
		return insertado;
	}

	public boolean actualizarJugador(Jugador jugador) {
		logger.debug("Inicio actualizarJugador: id={}", jugador.getId());
		String sql = " UPDATE JUGADORES SET NOMBRE = ?, APELLIDOS = ?, DORSAL = ?, POSICION = ?, EQUIPO = ?, "
				+ "CATEGORIA = ?, DEPORTE = ?, FOTO = ? WHERE ID = ? ";

		boolean actualizado = jdbcTemplate.update(sql, jugador.getNombre(), jugador.getApellidos(), jugador.getDorsal(),
				jugador.getPosicion(), jugador.getEquipo(), jugador.getCategoria(), jugador.getDeporte(),
				jugador.getFoto(), jugador.getId()) == 1;
		logger.debug("Fin actualizarJugador: id={}, actualizado={}", jugador.getId(), actualizado);
		return actualizado;
	}

	@Override
	public Jugador obtenerPorId(Long id) {
		logger.debug("Inicio obtenerPorId: id={}", id);
		String sql = "SELECT * FROM jugadores WHERE id = ?";

		try {
			Jugador jugador = jdbcTemplate.queryForObject(sql, JUGADOR_ROW_MAPPER, id);
			logger.debug("Fin obtenerPorId: id={}, encontrado=true", id);
			return jugador;
		} catch (EmptyResultDataAccessException e) {
			logger.warn("No se encontro jugador con id={}", id);
			return null;
		}
	}

	@Override
	public List<Jugador> obtenerTodosPorCategoria(String categoria) {
		logger.debug("Inicio obtenerTodosPorCategoria: categoria={}", categoria);
		String sql = "SELECT * FROM jugadores WHERE categoria = ? ORDER BY deporte, equipo, apellidos";

		List<Jugador> jugadores = jdbcTemplate.query(sql, JUGADOR_ROW_MAPPER, categoria);
		logger.debug("Fin obtenerTodosPorCategoria: categoria={}, total={}", categoria, jugadores.size());
		return jugadores;
	}

	@Override
	public List<Jugador> obtenerTodosPorEquipo(String equipo) {
		logger.debug("Inicio obtenerTodosPorEquipo: equipo={}", equipo);
		String sql = "SELECT * FROM jugadores WHERE equipo = ? ORDER BY equipo, dorsal";

		List<Jugador> jugadores = jdbcTemplate.query(sql, JUGADOR_ROW_MAPPER, equipo);
		logger.debug("Fin obtenerTodosPorEquipo: equipo={}, total={}", equipo, jugadores.size());
		return jugadores;
	}

	@Override
	public void eliminar(Long id) {
		logger.debug("Inicio eliminar: id={}", id);
		String sql = "DELETE FROM jugadores WHERE id = ?";
		int filasAfectadas = jdbcTemplate.update(sql, id);
		logger.info("Jugador eliminado: id={}, filasAfectadas={}", id, filasAfectadas);
		logger.debug("Fin eliminar: id={}", id);
	}

	@Override
	public List<Jugador> obtenerTodos() {
		logger.debug("Inicio obtenerTodos");
		String sql = "SELECT * FROM jugadores ORDER BY deporte, equipo, apellidos";

		List<Jugador> jugadores = jdbcTemplate.query(sql, JUGADOR_ROW_MAPPER);
		logger.debug("Fin obtenerTodos: total={}", jugadores.size());
		return jugadores;
	}

	private static Jugador mapRow(ResultSet rs, int rowNum) throws SQLException {
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
}
