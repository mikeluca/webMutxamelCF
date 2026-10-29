package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.model.Jugador;

@Repository
public class JugadorDaoImpl implements JugadorDao {

	private static final Logger logger = LoggerFactory.getLogger(JugadorDaoImpl.class);

	/**
	 * El equipo se lee SIEMPRE de EQUIPO (por JUGADORES.EQUIPO_ID): el texto de JUGADORES.EQUIPO es solo una copia
	 * que puede quedar desfasada si se renombra el equipo.
	 */
	private static final String SELECT_JUGADOR = """
			SELECT J.ID, J.NOMBRE, J.APELLIDOS, J.CATEGORIA, J.DEPORTE, J.DORSAL, J.POSICION, J.FOTO,
			       J.EQUIPO_ID, J.ANIO_NACIMIENTO, J.FECHA_BAJA, E.NOMBRE AS EQUIPO_ACTUAL
			FROM JUGADORES J
			LEFT JOIN EQUIPO E ON E.ID = J.EQUIPO_ID
			""";

	private static final RowMapper<Jugador> JUGADOR_ROW_MAPPER = JugadorDaoImpl::mapRow;

	/**
	 * Subconsulta: el jugador (J) tiene una fila PRUEBA, es decir, es una prueba dada de alta desde "Nueva temporada".
	 * Mientras la temporada no se aplica su ficha tiene FECHA_BAJA solo para que los listados de jugadores activos
	 * (todos filtran por FECHA_BAJA IS NULL) no la vean.
	 */
	private static final String PRUEBA_PENDIENTE = "SELECT 1 FROM JUGADOR_TEMPORADA JTP"
			+ " WHERE JTP.JUGADOR_ID = J.ID AND JTP.ESTADO = 'PRUEBA'";

	private final JdbcTemplate jdbcTemplate;

	public JugadorDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean guardarJugador(Jugador jugador) {
		logger.debug("Inicio guardarJugador: id={}", jugador.getId());
		boolean existe = jugador.getId() != null && obtenerPorId(jugador.getId()) != null;
		boolean resultado = existe ? actualizarJugador(jugador) : insertarJugador(jugador);
		logger.debug("Fin guardarJugador: existia={}, resultado={}", existe, resultado);
		return resultado;
	}

	public boolean insertarJugador(Jugador jugador) {
		logger.debug("Inicio insertarJugador");
		String sql = "INSERT INTO jugadores (nombre, apellidos, categoria, deporte, dorsal, posicion, equipo, equipo_id,"
				+ " anio_nacimiento, foto) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		KeyHolder claves = new GeneratedKeyHolder();
		int filas = jdbcTemplate.update(conexion -> {
			var ps = conexion.prepareStatement(sql, new String[] { "ID" });
			ps.setString(1, jugador.getNombre());
			ps.setString(2, jugador.getApellidos());
			ps.setString(3, jugador.getCategoria());
			ps.setString(4, jugador.getDeporte());
			ps.setObject(5, jugador.getDorsal());
			ps.setString(6, jugador.getPosicion());
			ps.setString(7, jugador.getEquipo());
			ps.setObject(8, jugador.getEquipoId());
			ps.setObject(9, jugador.getAnioNacimiento());
			ps.setBytes(10, jugador.getFoto());
			return ps;
		}, claves);

		if (filas == 1 && claves.getKey() != null) {
			jugador.setId(claves.getKey().longValue());
		}
		logger.info("Jugador insertado: id={}, insertado={}", jugador.getId(), filas == 1);
		logger.debug("Fin insertarJugador: insertado={}", filas == 1);
		return filas == 1;
	}

	public boolean actualizarJugador(Jugador jugador) {
		logger.debug("Inicio actualizarJugador: id={}", jugador.getId());
		// La baja (FECHA_BAJA) no se toca aquí: va por darDeBaja / reactivar. Las copias de equipo, categoría y deporte
		// no se borran si llegan nulas (un jugador de baja conserva las del último equipo).
		String sql = " UPDATE JUGADORES SET NOMBRE = ?, APELLIDOS = ?, DORSAL = ?, POSICION = ?, "
				+ "EQUIPO = COALESCE(?, EQUIPO), EQUIPO_ID = ?, CATEGORIA = COALESCE(?, CATEGORIA), "
				+ "DEPORTE = COALESCE(?, DEPORTE), ANIO_NACIMIENTO = ?, FOTO = ? WHERE ID = ? ";

		boolean actualizado = jdbcTemplate.update(sql, jugador.getNombre(), jugador.getApellidos(), jugador.getDorsal(),
				jugador.getPosicion(), jugador.getEquipo(), jugador.getEquipoId(), jugador.getCategoria(),
				jugador.getDeporte(), jugador.getAnioNacimiento(), jugador.getFoto(), jugador.getId()) == 1;
		logger.debug("Fin actualizarJugador: id={}, actualizado={}", jugador.getId(), actualizado);
		return actualizado;
	}

	@Override
	public Jugador obtenerPorId(Long id) {
		logger.debug("Inicio obtenerPorId: id={}", id);
		String sql = SELECT_JUGADOR + " WHERE J.ID = ?";

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
		String sql = SELECT_JUGADOR + " WHERE E.CATEGORIA = ? AND J.FECHA_BAJA IS NULL"
				+ " ORDER BY J.DEPORTE, E.NOMBRE, J.APELLIDOS";

		List<Jugador> jugadores = jdbcTemplate.query(sql, JUGADOR_ROW_MAPPER, categoria);
		logger.debug("Fin obtenerTodosPorCategoria: categoria={}, total={}", categoria, jugadores.size());
		return jugadores;
	}

	@Override
	public List<Jugador> obtenerTodosPorEquipoId(Long equipoId) {
		logger.debug("Inicio obtenerTodosPorEquipoId: equipoId={}", equipoId);
		String sql = SELECT_JUGADOR + " WHERE J.EQUIPO_ID = ? AND J.FECHA_BAJA IS NULL ORDER BY J.DORSAL";

		List<Jugador> jugadores = jdbcTemplate.query(sql, JUGADOR_ROW_MAPPER, equipoId);
		logger.debug("Fin obtenerTodosPorEquipoId: equipoId={}, total={}", equipoId, jugadores.size());
		return jugadores;
	}

	@Override
	public List<Jugador> obtenerTodosPorNombreEquipo(String nombreEquipo) {
		logger.debug("Inicio obtenerTodosPorNombreEquipo: equipo={}", nombreEquipo);
		// El nombre solo resuelve QUÉ equipo es (EQUIPO.NOMBRE); los jugadores se enlazan por EQUIPO_ID.
		String sql = SELECT_JUGADOR + " WHERE E.NOMBRE = ? AND J.FECHA_BAJA IS NULL ORDER BY J.DORSAL";

		List<Jugador> jugadores = jdbcTemplate.query(sql, JUGADOR_ROW_MAPPER, nombreEquipo);
		logger.debug("Fin obtenerTodosPorNombreEquipo: equipo={}, total={}", nombreEquipo, jugadores.size());
		return jugadores;
	}

	@Override
	public void eliminar(Long id) {
		logger.debug("Inicio eliminar: id={}", id);
		// Su fila de la temporada activa se creó al darlo de alta; se va con el jugador.
		int filasTemporada = jdbcTemplate.update("DELETE FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ?", id);
		String sql = "DELETE FROM jugadores WHERE id = ?";
		int filasAfectadas = jdbcTemplate.update(sql, id);
		logger.info("Jugador eliminado: id={}, filasAfectadas={}, filasTemporada={}", id, filasAfectadas, filasTemporada);
		logger.debug("Fin eliminar: id={}", id);
	}

	@Override
	public List<Jugador> obtenerTodos() {
		logger.debug("Inicio obtenerTodos");
		// Sin las pruebas todavía ocultas (ficha con FECHA_BAJA y una fila PRUEBA pendiente de aplicar)
		String sql = SELECT_JUGADOR + " WHERE (J.FECHA_BAJA IS NULL OR NOT EXISTS (" + PRUEBA_PENDIENTE + "))"
				+ " ORDER BY J.DEPORTE, E.NOMBRE, J.APELLIDOS";

		List<Jugador> jugadores = jdbcTemplate.query(sql, JUGADOR_ROW_MAPPER);
		logger.debug("Fin obtenerTodos: total={}", jugadores.size());
		return jugadores;
	}

	@Override
	public List<Jugador> obtenerActivos() {
		logger.debug("Inicio obtenerActivos");
		String sql = SELECT_JUGADOR + " WHERE J.FECHA_BAJA IS NULL ORDER BY J.DEPORTE, E.NOMBRE, J.APELLIDOS";

		List<Jugador> jugadores = jdbcTemplate.query(sql, JUGADOR_ROW_MAPPER);
		logger.debug("Fin obtenerActivos: total={}", jugadores.size());
		return jugadores;
	}

	@Override
	public List<Jugador> obtenerBajas() {
		logger.debug("Inicio obtenerBajas");
		// Una prueba oculta no es una baja
		String sql = SELECT_JUGADOR + " WHERE J.FECHA_BAJA IS NOT NULL AND NOT EXISTS (" + PRUEBA_PENDIENTE + ")"
				+ " ORDER BY J.FECHA_BAJA DESC, J.APELLIDOS";

		List<Jugador> jugadores = jdbcTemplate.query(sql, JUGADOR_ROW_MAPPER);
		logger.debug("Fin obtenerBajas: total={}", jugadores.size());
		return jugadores;
	}

	@Override
	public boolean darDeBaja(Long id, LocalDate fecha) {
		logger.debug("Inicio darDeBaja: id={}, fecha={}", id, fecha);
		boolean baja = jdbcTemplate.update(
				"UPDATE JUGADORES SET FECHA_BAJA = ?, EQUIPO_ID = NULL WHERE ID = ?", Date.valueOf(fecha), id) == 1;
		logger.info("Jugador dado de baja: id={}, baja={}", id, baja);
		return baja;
	}

	@Override
	public boolean reactivar(Long id, Long equipoId, String equipoNombre, String categoria, String deporte) {
		logger.debug("Inicio reactivar: id={}, equipoId={}", id, equipoId);
		boolean reactivado = jdbcTemplate.update(
				"UPDATE JUGADORES SET FECHA_BAJA = NULL, EQUIPO_ID = ?, EQUIPO = ?, CATEGORIA = ?, DEPORTE = ? WHERE ID = ?",
				equipoId, equipoNombre, categoria, deporte, id) == 1;
		logger.info("Jugador reactivado: id={}, reactivado={}", id, reactivado);
		return reactivado;
	}

	@Override
	public boolean activarSinEquipo(Long id, String categoria) {
		return jdbcTemplate.update(
				"UPDATE JUGADORES SET FECHA_BAJA = NULL, EQUIPO_ID = NULL, CATEGORIA = COALESCE(?, CATEGORIA) WHERE ID = ?",
				categoria, id) == 1;
	}

	@Override
	public boolean asignarPlantilla(Long id, Long equipoId, String equipoNombre, String categoria, String deporte,
			Integer dorsal) {
		return jdbcTemplate.update(
				"UPDATE JUGADORES SET EQUIPO_ID = ?, EQUIPO = COALESCE(?, EQUIPO), CATEGORIA = COALESCE(?, CATEGORIA),"
						+ " DEPORTE = COALESCE(?, DEPORTE), DORSAL = ? WHERE ID = ?",
				equipoId, equipoNombre, categoria, deporte, dorsal, id) == 1;
	}

	@Override
	public boolean actualizarAnioNacimiento(Long id, Integer anioNacimiento) {
		return jdbcTemplate.update("UPDATE JUGADORES SET ANIO_NACIMIENTO = ? WHERE ID = ?", anioNacimiento, id) == 1;
	}

	private static Jugador mapRow(ResultSet rs, int rowNum) throws SQLException {
		Jugador jugador = new Jugador();
		jugador.setId(rs.getLong("id"));
		jugador.setNombre(rs.getString("nombre"));
		jugador.setApellidos(rs.getString("apellidos"));
		jugador.setCategoria(rs.getString("categoria"));
		jugador.setDeporte(rs.getString("deporte"));
		jugador.setEquipo(rs.getString("equipo_actual"));
		long equipoId = rs.getLong("equipo_id");
		jugador.setEquipoId(rs.wasNull() ? null : equipoId);
		int dorsal = rs.getInt("dorsal");
		jugador.setDorsal(rs.wasNull() ? null : dorsal);
		jugador.setPosicion(rs.getString("posicion"));
		jugador.setFoto(rs.getBytes("foto"));
		int anio = rs.getInt("anio_nacimiento");
		jugador.setAnioNacimiento(rs.wasNull() ? null : anio);
		Date baja = rs.getDate("fecha_baja");
		jugador.setFechaBaja(baja == null ? null : baja.toLocalDate());
		return jugador;
	}
}
