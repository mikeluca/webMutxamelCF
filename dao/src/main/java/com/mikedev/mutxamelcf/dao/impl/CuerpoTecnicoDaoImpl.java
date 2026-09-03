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

import com.mikedev.mutxamelcf.dao.CuerpoTecnicoDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;

@Repository
public class CuerpoTecnicoDaoImpl implements CuerpoTecnicoDao {

	private static final Logger logger = LoggerFactory.getLogger(CuerpoTecnicoDaoImpl.class);

	private static final RowMapper<CuerpoTecnico> CUERPO_TECNICO_ROW_MAPPER = CuerpoTecnicoDaoImpl::mapRow;

	private final JdbcTemplate jdbcTemplate;

	public CuerpoTecnicoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public boolean guardar(CuerpoTecnico cuerpoTecnico) {
		logger.debug("Inicio guardar: id={}, dni={}", cuerpoTecnico.getId(), cuerpoTecnico.getDni());
		boolean existe = obtenerPorId(cuerpoTecnico.getId()) != null;
		boolean resultado = existe ? actualizarCuerpoTecnico(cuerpoTecnico) : insertarCuerpoTecnico(cuerpoTecnico);
		logger.debug("Fin guardar: existia={}, resultado={}", existe, resultado);
		return resultado;
	}

	public boolean insertarCuerpoTecnico(CuerpoTecnico cuerpoTecnico) {
		logger.debug("Inicio insertarCuerpoTecnico: dni={}", cuerpoTecnico.getDni());
		String sql = "INSERT INTO cuerpo_tecnico (dni, nombre, apellidos, fecha_nacimiento, poblacion, "
				+ "nacionalidad, categoria, deporte, equipo, puesto, foto) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		boolean insertado = jdbcTemplate.update(sql, cuerpoTecnico.getDni(), cuerpoTecnico.getNombre(),
				cuerpoTecnico.getApellidos(), cuerpoTecnico.getFechaNacimiento(), cuerpoTecnico.getPoblacion(),
				cuerpoTecnico.getNacionalidad(), cuerpoTecnico.getCategoria(), cuerpoTecnico.getDeporte(),
				cuerpoTecnico.getEquipo(), cuerpoTecnico.getPuesto(), cuerpoTecnico.getFoto()) == 1;
		logger.info("Cuerpo tecnico insertado: dni={}, insertado={}", cuerpoTecnico.getDni(), insertado);
		logger.debug("Fin insertarCuerpoTecnico: insertado={}", insertado);
		return insertado;
	}

	public boolean actualizarCuerpoTecnico(CuerpoTecnico cuerpoTecnico) {
		logger.debug("Inicio actualizarCuerpoTecnico: id={}", cuerpoTecnico.getId());
		String sql = " UPDATE cuerpo_tecnico SET NOMBRE = ?, APELLIDOS = ?, PUESTO = ?, EQUIPO = ?, "
				+ "CATEGORIA = ?, DEPORTE = ?, FOTO = ? WHERE ID = ? ";

		boolean actualizado = jdbcTemplate.update(sql, cuerpoTecnico.getNombre(), cuerpoTecnico.getApellidos(),
				cuerpoTecnico.getPuesto(), cuerpoTecnico.getEquipo(), cuerpoTecnico.getCategoria(),
				cuerpoTecnico.getDeporte(), cuerpoTecnico.getFoto(), cuerpoTecnico.getId()) == 1;
		logger.debug("Fin actualizarCuerpoTecnico: id={}, actualizado={}", cuerpoTecnico.getId(), actualizado);
		return actualizado;
	}

	@Override
	public CuerpoTecnico obtenerPorId(Long id) {
		logger.debug("Inicio obtenerPorId: id={}", id);
		String sql = "SELECT * FROM cuerpo_tecnico WHERE id = ?";

		try {
			CuerpoTecnico cuerpoTecnico = jdbcTemplate.queryForObject(sql, CUERPO_TECNICO_ROW_MAPPER, id);
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
		String sql = "SELECT * FROM cuerpo_tecnico WHERE categoria = ? ORDER BY deporte, equipo, apellidos";

		List<CuerpoTecnico> lista = jdbcTemplate.query(sql, CUERPO_TECNICO_ROW_MAPPER, categoria);
		logger.debug("Fin obtenerTodosPorCategoria: categoria={}, total={}", categoria, lista.size());
		return lista;
	}

	@Override
	public List<CuerpoTecnico> obtenerTodosPorEquipo(String equipo) {
		logger.debug("Inicio obtenerTodosPorEquipo: equipo={}", equipo);
		String sql = "SELECT * FROM cuerpo_tecnico WHERE equipo = ? ORDER BY equipo, apellidos";

		List<CuerpoTecnico> lista = jdbcTemplate.query(sql, CUERPO_TECNICO_ROW_MAPPER, equipo);
		logger.debug("Fin obtenerTodosPorEquipo: equipo={}, total={}", equipo, lista.size());
		return lista;
	}

	@Override
	public void eliminar(Long id) {
		logger.debug("Inicio eliminar: id={}", id);
		String sql = "DELETE FROM cuerpo_tecnico WHERE id = ?";
		int filasAfectadas = jdbcTemplate.update(sql, id);
		logger.info("Miembro del cuerpo tecnico eliminado: id={}, filasAfectadas={}", id, filasAfectadas);
		logger.debug("Fin eliminar: id={}", id);
	}

	@Override
	public List<CuerpoTecnico> obtenerTodos() {
		logger.debug("Inicio obtenerTodos");
		String sql = "SELECT * FROM cuerpo_tecnico ORDER BY deporte, equipo, apellidos";

		List<CuerpoTecnico> lista = jdbcTemplate.query(sql, CUERPO_TECNICO_ROW_MAPPER);
		logger.debug("Fin obtenerTodos: total={}", lista.size());
		return lista;
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
