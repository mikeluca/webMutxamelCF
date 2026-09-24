package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.model.Equipo;

@Repository
public class EquipoDaoImpl implements EquipoDao {

	private static final Logger logger = LoggerFactory.getLogger(EquipoDaoImpl.class);

	private static final RowMapper<Equipo> EQUIPO_ROW_MAPPER = EquipoDaoImpl::mapRow;

	private final JdbcTemplate jdbcTemplate;

	public EquipoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean guardar(Equipo equipo) {
		logger.debug("Inicio guardar: id={}, categoria={}, grupo={}", equipo.getId(), equipo.getCategoria(),
				equipo.getGrupo());
		if (existeEquipo(equipo.getCategoria(), equipo.getGrupo(), equipo.getId())) {
			logger.warn("Equipo ya existente, no se guarda: categoria={}, grupo={}", equipo.getCategoria(), equipo.getGrupo());
			return false;
		}

		boolean resultado = equipo.getId() != null ? actualizarEquipo(equipo) : insertarEquipo(equipo);
		logger.debug("Fin guardar: resultado={}", resultado);
		return resultado;
	}

	private boolean insertarEquipo(Equipo equipo) {
		logger.debug("Inicio insertarEquipo: categoria={}, grupo={}", equipo.getCategoria(), equipo.getGrupo());
		String sql = "INSERT INTO equipo (categoria, grupo, orden, nombre, deporte) VALUES (?, ?, ?, ?, ?)";
		boolean insertado = jdbcTemplate.update(sql, equipo.getCategoria(), equipo.getGrupo(), equipo.getOrden(),
				equipo.getNombre(), equipo.getDeporte()) == 1;
		logger.debug("Fin insertarEquipo: insertado={}", insertado);
		return insertado;
	}

	private boolean actualizarEquipo(Equipo equipo) {
		logger.debug("Inicio actualizarEquipo: id={}", equipo.getId());
		String sql = "UPDATE equipo SET categoria = ?, grupo = ?, orden = ?, nombre = ?, deporte = ? WHERE id = ?";
		boolean actualizado = jdbcTemplate.update(sql, equipo.getCategoria(), equipo.getGrupo(), equipo.getOrden(),
				equipo.getNombre(), equipo.getDeporte(), equipo.getId()) == 1;
		logger.debug("Fin actualizarEquipo: id={}, actualizado={}", equipo.getId(), actualizado);
		return actualizado;
	}

	private boolean existeEquipo(String categoria, String grupo, Long idExcluido) {
		logger.debug("Inicio existeEquipo: categoria={}, grupo={}, idExcluido={}", categoria, grupo, idExcluido);
		String sql = "SELECT COUNT(*) FROM equipo WHERE categoria = ? AND grupo = ?"
				+ (idExcluido != null ? " AND id != ?" : "");
		Integer count = idExcluido != null
				? jdbcTemplate.queryForObject(sql, Integer.class, categoria, grupo, idExcluido)
				: jdbcTemplate.queryForObject(sql, Integer.class, categoria, grupo);
		boolean existe = count != null && count > 0;
		logger.debug("Fin existeEquipo: existe={}", existe);
		return existe;
	}

	@Override
	public Equipo obtenerEquipoPorId(Long id) {
		logger.debug("Inicio obtenerEquipoPorId: id={}", id);
		String sql = "SELECT * FROM equipo WHERE id = ?";
		Equipo equipo = jdbcTemplate.queryForObject(sql, EQUIPO_ROW_MAPPER, id);
		logger.debug("Fin obtenerEquipoPorId: id={}", id);
		return equipo;
	}

	@Override
	public Equipo obtenerEquipoPorNombre(String nombre) {
		logger.debug("Inicio obtenerEquipoPorNombre: nombre={}", nombre);
		String sql = "SELECT * FROM equipo WHERE UPPER(TRIM(nombre)) = UPPER(TRIM(?))";
		List<Equipo> equipos = jdbcTemplate.query(sql, EQUIPO_ROW_MAPPER, nombre);
		Equipo equipo = equipos.isEmpty() ? null : equipos.get(0);
		logger.debug("Fin obtenerEquipoPorNombre: nombre={}, encontrado={}", nombre, equipo != null);
		return equipo;
	}

	@Override
	public void eliminarEquipo(Long id) {
		logger.debug("Inicio eliminarEquipo: id={}", id);
		String sql = "DELETE FROM equipo WHERE id = ?";
		int filasAfectadas = jdbcTemplate.update(sql, id);
		logger.info("Equipo eliminado: id={}, filasAfectadas={}", id, filasAfectadas);
		logger.debug("Fin eliminarEquipo: id={}", id);
	}

	@Override
	public List<String> obtenerCategorias() {
		logger.debug("Inicio obtenerCategorias");
		String sql = "SELECT categoria FROM (SELECT DISTINCT categoria, orden FROM equipo) ORDER BY orden";
		List<String> categorias = jdbcTemplate.queryForList(sql, String.class);
		logger.debug("Fin obtenerCategorias: total={}", categorias.size());
		return categorias;
	}

	@Override
	public List<Equipo> obtenerTodos() {
		logger.debug("Inicio obtenerTodos");
		String sql = "SELECT * FROM equipo ORDER BY deporte, orden, nombre";
		List<Equipo> equipos = jdbcTemplate.query(sql, EQUIPO_ROW_MAPPER);
		logger.debug("Fin obtenerTodos: total={}", equipos.size());
		return equipos;
	}

	@Override
	public List<Equipo> obtenerTodosPorCategoria(String categoria) {
		logger.debug("Inicio obtenerTodosPorCategoria: categoria={}", categoria);
		String sql = "SELECT * FROM equipo WHERE categoria = ? ORDER BY deporte, orden, nombre";
		List<Equipo> equipos = jdbcTemplate.query(sql, EQUIPO_ROW_MAPPER, categoria);
		logger.debug("Fin obtenerTodosPorCategoria: categoria={}, total={}", categoria, equipos.size());
		return equipos;
	}

	@Override
	public Map<String, List<Equipo>> obtenerEquiposAgrupadosPorCategoria(String deporte) {
		logger.debug("Inicio obtenerEquiposAgrupadosPorCategoria: deporte={}", deporte);
		String sql = "SELECT id, nombre, categoria, grupo, orden, deporte FROM equipo WHERE deporte = ? and orden <> 'I' ORDER BY orden, nombre";
		List<Equipo> equipos = jdbcTemplate.query(sql, EQUIPO_ROW_MAPPER, deporte);

		Map<String, List<Equipo>> agrupados = equipos.stream().collect(Collectors.groupingBy(Equipo::getOrden));
		logger.debug("Fin obtenerEquiposAgrupadosPorCategoria: deporte={}, grupos={}", deporte, agrupados.size());
		return agrupados;
	}

	private static Equipo mapRow(ResultSet rs, int rowNum) throws SQLException {
		Equipo equipo = new Equipo();
		equipo.setId(rs.getLong("id"));
		equipo.setCategoria(rs.getString("categoria"));
		equipo.setGrupo(rs.getString("grupo"));
		equipo.setOrden(rs.getString("orden"));
		equipo.setNombre(rs.getString("nombre"));
		equipo.setDeporte(rs.getString("deporte"));
		return equipo;
	}

}
