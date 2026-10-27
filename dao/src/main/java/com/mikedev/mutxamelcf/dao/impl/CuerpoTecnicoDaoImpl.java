package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.CuerpoTecnicoDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoEquipo;

@Repository
public class CuerpoTecnicoDaoImpl implements CuerpoTecnicoDao {

	private static final Logger logger = LoggerFactory.getLogger(CuerpoTecnicoDaoImpl.class);

	private static final RowMapper<CuerpoTecnico> CUERPO_TECNICO_ROW_MAPPER = CuerpoTecnicoDaoImpl::mapRow;

	/**
	 * Una fila por (ficha, equipo): el equipo sale de EQUIPO y el puesto es el de ese equipo
	 * (COALESCE(CTE.PUESTO, CT.PUESTO)). Nada depende de las copias CUERPO_TECNICO.EQUIPO/CATEGORIA/DEPORTE.
	 */
	private static final String SELECT_POR_EQUIPO = """
			SELECT CT.ID, CT.NOMBRE, CT.APELLIDOS, CT.FOTO, E.NOMBRE AS EQUIPO, E.CATEGORIA AS CATEGORIA,
			       E.DEPORTE AS DEPORTE, COALESCE(CTE.PUESTO, CT.PUESTO) AS PUESTO
			FROM CUERPO_TECNICO CT
			INNER JOIN CUERPO_TECNICO_EQUIPO CTE ON CTE.CUERPO_TECNICO_ID = CT.ID
			INNER JOIN EQUIPO E ON E.ID = CTE.EQUIPO_ID
			""";

	private final JdbcTemplate jdbcTemplate;

	public CuerpoTecnicoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean guardar(CuerpoTecnico cuerpoTecnico) {
		logger.debug("Inicio guardar: id={}", cuerpoTecnico.getId());
		boolean existe = cuerpoTecnico.getId() != null && obtenerPorId(cuerpoTecnico.getId()) != null;
		boolean resultado = existe ? actualizarCuerpoTecnico(cuerpoTecnico) : insertarCuerpoTecnico(cuerpoTecnico);
		logger.debug("Fin guardar: existia={}, resultado={}", existe, resultado);
		return resultado;
	}

	public boolean insertarCuerpoTecnico(CuerpoTecnico c) {
		logger.debug("Inicio insertarCuerpoTecnico: nombre={}", c.getNombre());
		String sql = "INSERT INTO cuerpo_tecnico (nombre, apellidos, categoria, deporte, equipo, puesto, foto) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?)";

		KeyHolder claves = new GeneratedKeyHolder();
		int filas = jdbcTemplate.update(conexion -> {
			var ps = conexion.prepareStatement(sql, new String[] { "ID" });
			ps.setString(1, c.getNombre());
			ps.setString(2, c.getApellidos());
			ps.setString(3, c.getCategoria());
			ps.setString(4, c.getDeporte());
			ps.setString(5, c.getEquipo());
			ps.setString(6, c.getPuesto());
			ps.setBytes(7, c.getFoto());
			return ps;
		}, claves);

		if (filas == 1 && claves.getKey() != null) {
			c.setId(claves.getKey().longValue());
		}
		logger.info("Cuerpo tecnico insertado: id={}, insertado={}", c.getId(), filas == 1);
		return filas == 1;
	}

	public boolean actualizarCuerpoTecnico(CuerpoTecnico c) {
		logger.debug("Inicio actualizarCuerpoTecnico: id={}", c.getId());
		String sql = " UPDATE cuerpo_tecnico SET NOMBRE = ?, APELLIDOS = ?, PUESTO = ?, EQUIPO = ?, "
				+ "CATEGORIA = ?, DEPORTE = ?, FOTO = ? WHERE ID = ? ";

		boolean actualizado = jdbcTemplate.update(sql, c.getNombre(), c.getApellidos(), c.getPuesto(), c.getEquipo(),
				c.getCategoria(), c.getDeporte(), c.getFoto(), c.getId()) == 1;
		logger.debug("Fin actualizarCuerpoTecnico: id={}, actualizado={}", c.getId(), actualizado);
		return actualizado;
	}

	@Override
	public CuerpoTecnico obtenerPorId(Long id) {
		logger.debug("Inicio obtenerPorId: id={}", id);
		String sql = "SELECT * FROM cuerpo_tecnico WHERE id = ?";

		try {
			CuerpoTecnico cuerpoTecnico = jdbcTemplate.queryForObject(sql, CUERPO_TECNICO_ROW_MAPPER, id);
			if (cuerpoTecnico != null) {
				cuerpoTecnico.setEquipos(obtenerEquipos(id));
			}
			logger.debug("Fin obtenerPorId: id={}, encontrado=true", id);
			return cuerpoTecnico;
		} catch (EmptyResultDataAccessException e) {
			logger.warn("No se encontro miembro del cuerpo tecnico con id={}", id);
			return null;
		}
	}

	@Override
	public List<CuerpoTecnico> obtenerTodosPorCategoria(String categoria) {
		logger.debug("Inicio obtenerTodosPorCategoria: categoria={}", categoria);
		String sql = SELECT_POR_EQUIPO + " WHERE E.CATEGORIA = ? ORDER BY E.DEPORTE, E.NOMBRE, CT.APELLIDOS";

		List<CuerpoTecnico> lista = jdbcTemplate.query(sql, CUERPO_TECNICO_ROW_MAPPER, categoria);
		logger.debug("Fin obtenerTodosPorCategoria: categoria={}, total={}", categoria, lista.size());
		return lista;
	}

	@Override
	public List<CuerpoTecnico> obtenerTodosPorEquipo(String nombreEquipo) {
		logger.debug("Inicio obtenerTodosPorEquipo: equipo={}", nombreEquipo);
		// El nombre solo identifica QUÉ equipo es (EQUIPO.NOMBRE); la relación va por CUERPO_TECNICO_EQUIPO.
		String sql = SELECT_POR_EQUIPO + " WHERE E.NOMBRE = ? ORDER BY CT.APELLIDOS, CT.NOMBRE";

		List<CuerpoTecnico> lista = jdbcTemplate.query(sql, CUERPO_TECNICO_ROW_MAPPER, nombreEquipo);
		logger.debug("Fin obtenerTodosPorEquipo: equipo={}, total={}", nombreEquipo, lista.size());
		return lista;
	}

	@Override
	public List<CuerpoTecnico> obtenerTodosPorEquipoId(Long equipoId) {
		String sql = SELECT_POR_EQUIPO + " WHERE E.ID = ? ORDER BY CT.APELLIDOS, CT.NOMBRE";
		return jdbcTemplate.query(sql, CUERPO_TECNICO_ROW_MAPPER, equipoId);
	}

	@Override
	public void eliminar(Long id) {
		logger.debug("Inicio eliminar: id={}", id);
		int filasEquipos = jdbcTemplate.update("DELETE FROM cuerpo_tecnico_equipo WHERE cuerpo_tecnico_id = ?", id);
		int filasAfectadas = jdbcTemplate.update("DELETE FROM cuerpo_tecnico WHERE id = ?", id);
		logger.info("Miembro del cuerpo tecnico eliminado: id={}, filasAfectadas={}, filasEquipos={}", id,
				filasAfectadas, filasEquipos);
	}

	@Override
	public List<CuerpoTecnico> obtenerTodos() {
		logger.debug("Inicio obtenerTodos");
		List<CuerpoTecnico> lista = jdbcTemplate.query("SELECT * FROM cuerpo_tecnico ORDER BY apellidos, nombre, id",
				CUERPO_TECNICO_ROW_MAPPER);

		// Los equipos de todas las fichas de una vez
		Map<Long, List<CuerpoTecnicoEquipo>> equipos = new HashMap<>();
		jdbcTemplate.query("""
				SELECT CTE.CUERPO_TECNICO_ID AS FICHA, E.ID AS EQUIPO_ID, E.NOMBRE, E.CATEGORIA, E.DEPORTE, CTE.PUESTO,
				       COALESCE(CTE.PUESTO, CT.PUESTO) AS PUESTO_MOSTRADO
				FROM CUERPO_TECNICO_EQUIPO CTE
				INNER JOIN CUERPO_TECNICO CT ON CT.ID = CTE.CUERPO_TECNICO_ID
				INNER JOIN EQUIPO E ON E.ID = CTE.EQUIPO_ID
				ORDER BY E.ORDEN, E.NOMBRE""", rs -> {
			equipos.computeIfAbsent(rs.getLong("FICHA"), k -> new ArrayList<>()).add(mapEquipo(rs));
		});
		lista.forEach(c -> c.setEquipos(equipos.getOrDefault(c.getId(), new ArrayList<>())));

		logger.debug("Fin obtenerTodos: total={}", lista.size());
		return lista;
	}

	@Override
	public List<CuerpoTecnicoEquipo> obtenerEquipos(Long cuerpoTecnicoId) {
		return jdbcTemplate.query("""
				SELECT E.ID AS EQUIPO_ID, E.NOMBRE, E.CATEGORIA, E.DEPORTE, CTE.PUESTO,
				       COALESCE(CTE.PUESTO, CT.PUESTO) AS PUESTO_MOSTRADO
				FROM CUERPO_TECNICO_EQUIPO CTE
				INNER JOIN CUERPO_TECNICO CT ON CT.ID = CTE.CUERPO_TECNICO_ID
				INNER JOIN EQUIPO E ON E.ID = CTE.EQUIPO_ID
				WHERE CTE.CUERPO_TECNICO_ID = ?
				ORDER BY E.ORDEN, E.NOMBRE""", (rs, n) -> mapEquipo(rs), cuerpoTecnicoId);
	}

	@Override
	public void reemplazarEquipos(Long cuerpoTecnicoId, List<CuerpoTecnicoEquipo> equipos) {
		// Equipo repetido: cuenta una vez (el primero)
		Map<Long, CuerpoTecnicoEquipo> deseados = new LinkedHashMap<>();
		for (CuerpoTecnicoEquipo e : equipos) {
			deseados.putIfAbsent(e.getEquipoId(), e);
		}

		Map<Long, String> actuales = new HashMap<>();
		jdbcTemplate.query("SELECT EQUIPO_ID, PUESTO FROM CUERPO_TECNICO_EQUIPO WHERE CUERPO_TECNICO_ID = ?", rs -> {
			actuales.put(rs.getLong("EQUIPO_ID"), rs.getString("PUESTO"));
		}, cuerpoTecnicoId);

		for (Long equipoId : actuales.keySet()) {
			if (!deseados.containsKey(equipoId)) {
				jdbcTemplate.update("DELETE FROM CUERPO_TECNICO_EQUIPO WHERE CUERPO_TECNICO_ID = ? AND EQUIPO_ID = ?",
						cuerpoTecnicoId, equipoId);
			}
		}

		for (CuerpoTecnicoEquipo e : deseados.values()) {
			if (actuales.containsKey(e.getEquipoId())) {
				if (!java.util.Objects.equals(actuales.get(e.getEquipoId()), e.getPuesto())) {
					jdbcTemplate.update(
							"UPDATE CUERPO_TECNICO_EQUIPO SET PUESTO = ? WHERE CUERPO_TECNICO_ID = ? AND EQUIPO_ID = ?",
							e.getPuesto(), cuerpoTecnicoId, e.getEquipoId());
				}
			} else {
				jdbcTemplate.update("INSERT INTO CUERPO_TECNICO_EQUIPO (CUERPO_TECNICO_ID, EQUIPO_ID, PUESTO, FECHA_CREACION)"
						+ " VALUES (?, ?, ?, ?)", cuerpoTecnicoId, e.getEquipoId(), e.getPuesto(),
						Timestamp.valueOf(LocalDateTime.now()));
			}
		}
	}

	@Override
	public boolean existenParaEquipo(Long equipoId) {
		Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CUERPO_TECNICO_EQUIPO WHERE EQUIPO_ID = ?",
				Integer.class, equipoId);
		return total != null && total > 0;
	}

	@Override
	public void actualizarCopiaInformativa(Long cuerpoTecnicoId, String equipo, String categoria, String deporte) {
		jdbcTemplate.update("UPDATE CUERPO_TECNICO SET EQUIPO = ?, CATEGORIA = ?, DEPORTE = ? WHERE ID = ?", equipo,
				categoria, deporte, cuerpoTecnicoId);
	}

	@Override
	public int moverVinculosApp(Long desdeId, Long haciaId) {
		int movidos = jdbcTemplate.update("""
				UPDATE USUARIOS_APP_CUERPO_TECNICO SET CUERPO_TECNICO_ID = ?
				WHERE CUERPO_TECNICO_ID = ?
				  AND USUARIO_APP_ID NOT IN (SELECT USUARIO_APP_ID FROM USUARIOS_APP_CUERPO_TECNICO
				                              WHERE CUERPO_TECNICO_ID = ?)""", haciaId, desdeId, haciaId);
		// Los que ya estaban vinculados a la ficha de destino se descartan
		jdbcTemplate.update("DELETE FROM USUARIOS_APP_CUERPO_TECNICO WHERE CUERPO_TECNICO_ID = ?", desdeId);
		return movidos;
	}

	@Override
	public int contarVinculosApp(Long cuerpoTecnicoId) {
		Integer total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM USUARIOS_APP_CUERPO_TECNICO WHERE CUERPO_TECNICO_ID = ?", Integer.class,
				cuerpoTecnicoId);
		return total == null ? 0 : total;
	}

	private static CuerpoTecnicoEquipo mapEquipo(ResultSet rs) throws SQLException {
		CuerpoTecnicoEquipo e = new CuerpoTecnicoEquipo();
		e.setEquipoId(rs.getLong("EQUIPO_ID"));
		e.setEquipoNombre(rs.getString("NOMBRE"));
		e.setCategoria(rs.getString("CATEGORIA"));
		e.setDeporte(rs.getString("DEPORTE"));
		e.setPuesto(rs.getString("PUESTO"));
		e.setPuestoMostrado(rs.getString("PUESTO_MOSTRADO"));
		return e;
	}

	private static CuerpoTecnico mapRow(ResultSet rs, int rowNum) throws SQLException {
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
}
