package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
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

	// La temporada va como parámetro (la resuelve TemporadaActivaId): una subconsulta en el ON del LEFT JOIN puede
	// dar ORA-01799. La modalidad es la de la temporada, con la de EQUIPO como respaldo.
	private static final String BASE = """
			SELECT e.id, e.categoria, e.grupo, e.orden, e.nombre, e.deporte,
			       COALESCE(et.modalidad, e.modalidad) AS modalidad,
			       et.activo AS activo_temporada, et.tipo AS tipo_temporada
			FROM equipo e
			LEFT JOIN equipo_temporada et ON et.equipo_id = e.id AND et.temporada_id = ?
			""";

	private static final String SOLO_ACTIVOS = "(? < 0 OR et.activo = 1)";
	private static final String SOLO_PUBLICOS = "(? < 0 OR (et.activo = 1 AND et.tipo = 'NORMAL'))";

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
		if (existeNombre(equipo.getNombre(), equipo.getId())) {
			logger.warn("Nombre de equipo ya usado por otro equipo, no se guarda: nombre={}", equipo.getNombre());
			return false;
		}

		boolean resultado = equipo.getId() != null ? actualizarEquipo(equipo) : insertarEquipo(equipo);
		if (resultado) {
			guardarEnLaTemporada(equipo);
		}
		logger.debug("Fin guardar: resultado={}", resultado);
		return resultado;
	}

	private boolean insertarEquipo(Equipo equipo) {
		logger.debug("Inicio insertarEquipo: categoria={}, grupo={}", equipo.getCategoria(), equipo.getGrupo());
		String sql = "INSERT INTO equipo (categoria, grupo, orden, nombre, deporte, modalidad) VALUES (?, ?, ?, ?, ?, ?)";
		boolean insertado = jdbcTemplate.update(sql, equipo.getCategoria(), equipo.getGrupo(), equipo.getOrden(),
				equipo.getNombre(), equipo.getDeporte(), equipo.getModalidad()) == 1;
		logger.debug("Fin insertarEquipo: insertado={}", insertado);
		return insertado;
	}

	private boolean actualizarEquipo(Equipo equipo) {
		logger.debug("Inicio actualizarEquipo: id={}", equipo.getId());
		String sql = "UPDATE equipo SET categoria = ?, grupo = ?, orden = ?, nombre = ?, deporte = ?, modalidad = ? WHERE id = ?";
		boolean actualizado = jdbcTemplate.update(sql, equipo.getCategoria(), equipo.getGrupo(), equipo.getOrden(),
				equipo.getNombre(), equipo.getDeporte(), equipo.getModalidad(), equipo.getId()) == 1;
		if (actualizado) {
			// Las copias de JUGADORES (EQUIPO, CATEGORIA, DEPORTE) siguen al equipo; el enlace real es EQUIPO_ID.
			jdbcTemplate.update("UPDATE jugadores SET equipo = ?, categoria = ?, deporte = ? WHERE equipo_id = ?",
					equipo.getNombre(), equipo.getCategoria(), equipo.getDeporte(), equipo.getId());
		}
		logger.debug("Fin actualizarEquipo: id={}, actualizado={}", equipo.getId(), actualizado);
		return actualizado;
	}

	// Su fila de EQUIPO_TEMPORADA en la temporada activa: ACTIVO (nulo conserva el actual; un equipo nuevo entra activo)
	// y MODALIDAD (EQUIPO.MODALIDAD se guarda igual, sincronizada). El TIPO no se toca.
	private void guardarEnLaTemporada(Equipo equipo) {
		long temporadaId = TemporadaActivaId.obtenerOMenosUno(jdbcTemplate);

		if (temporadaId < 0) {
			return;
		}

		Long equipoId = equipo.getId();

		if (equipoId == null) {
			List<Long> ids = jdbcTemplate.queryForList(
					"SELECT id FROM equipo WHERE UPPER(TRIM(nombre)) = UPPER(TRIM(?))", Long.class, equipo.getNombre());
			equipoId = ids.isEmpty() ? null : ids.get(0);
		}

		if (equipoId == null) {
			return;
		}

		List<Integer> actuales = jdbcTemplate.queryForList(
				"SELECT activo FROM equipo_temporada WHERE equipo_id = ? AND temporada_id = ?", Integer.class, equipoId,
				temporadaId);
		int activo = equipo.getActivoTemporada() != null ? (equipo.getActivoTemporada() ? 1 : 0)
				: (actuales.isEmpty() ? 1 : actuales.get(0));

		if (actuales.isEmpty()) {
			jdbcTemplate.update("INSERT INTO equipo_temporada (equipo_id, temporada_id, activo, modalidad, tipo)"
					+ " VALUES (?, ?, ?, ?, 'NORMAL')", equipoId, temporadaId, activo, equipo.getModalidad());
		} else {
			jdbcTemplate.update("UPDATE equipo_temporada SET activo = ?, modalidad = ? WHERE equipo_id = ? AND temporada_id = ?",
					activo, equipo.getModalidad(), equipoId, temporadaId);
		}
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

	private boolean existeNombre(String nombre, Long idExcluido) {
		logger.debug("Inicio existeNombre: nombre={}, idExcluido={}", nombre, idExcluido);
		String sql = "SELECT COUNT(*) FROM equipo WHERE UPPER(TRIM(nombre)) = UPPER(TRIM(?))"
				+ (idExcluido != null ? " AND id != ?" : "");
		Integer count = idExcluido != null
				? jdbcTemplate.queryForObject(sql, Integer.class, nombre, idExcluido)
				: jdbcTemplate.queryForObject(sql, Integer.class, nombre);
		boolean existe = count != null && count > 0;
		logger.debug("Fin existeNombre: existe={}", existe);
		return existe;
	}

	@Override
	public Equipo obtenerEquipoPorId(Long id) {
		logger.debug("Inicio obtenerEquipoPorId: id={}", id);
		Equipo equipo = jdbcTemplate.queryForObject(BASE + " WHERE e.id = ?", EQUIPO_ROW_MAPPER,
				TemporadaActivaId.obtenerOMenosUno(jdbcTemplate), id);
		logger.debug("Fin obtenerEquipoPorId: id={}", id);
		return equipo;
	}

	@Override
	public Equipo obtenerEquipoPorNombre(String nombre) {
		logger.debug("Inicio obtenerEquipoPorNombre: nombre={}", nombre);
		List<Equipo> equipos = jdbcTemplate.query(BASE + " WHERE UPPER(TRIM(e.nombre)) = UPPER(TRIM(?))", EQUIPO_ROW_MAPPER,
				TemporadaActivaId.obtenerOMenosUno(jdbcTemplate), nombre);
		Equipo equipo = equipos.isEmpty() ? null : equipos.get(0);
		logger.debug("Fin obtenerEquipoPorNombre: nombre={}, encontrado={}", nombre, equipo != null);
		return equipo;
	}

	@Override
	public void eliminarEquipo(Long id) {
		logger.debug("Inicio eliminarEquipo: id={}", id);
		// Las filas de EQUIPO_TEMPORADA (FK) van antes: el servicio ya comprobó que no tiene datos.
		jdbcTemplate.update("DELETE FROM equipo_temporada WHERE equipo_id = ?", id);
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
		List<Equipo> equipos = consultar(null, null, "e.deporte, e.orden, e.nombre");
		logger.debug("Fin obtenerTodos: total={}", equipos.size());
		return equipos;
	}

	@Override
	public List<Equipo> obtenerActivos() {
		logger.debug("Inicio obtenerActivos");
		List<Equipo> equipos = consultar(SOLO_ACTIVOS, null, "e.deporte, e.orden, e.nombre");
		logger.debug("Fin obtenerActivos: total={}", equipos.size());
		return equipos;
	}

	@Override
	public List<Equipo> obtenerActivosPublicos() {
		logger.debug("Inicio obtenerActivosPublicos");
		List<Equipo> equipos = consultar(SOLO_PUBLICOS, null, "e.deporte, e.orden, e.nombre");
		logger.debug("Fin obtenerActivosPublicos: total={}", equipos.size());
		return equipos;
	}

	@Override
	public List<Equipo> obtenerActivosPorCategoria(String categoria) {
		logger.debug("Inicio obtenerActivosPorCategoria: categoria={}", categoria);
		List<Equipo> equipos = consultar(SOLO_ACTIVOS, "e.categoria = ?", "e.deporte, e.orden, e.nombre", categoria);
		logger.debug("Fin obtenerActivosPorCategoria: categoria={}, total={}", categoria, equipos.size());
		return equipos;
	}

	@Override
	public List<Equipo> obtenerActivosPublicosPorDeporte(String deporte) {
		logger.debug("Inicio obtenerActivosPublicosPorDeporte: deporte={}", deporte);
		List<Equipo> equipos = consultar(SOLO_PUBLICOS, "e.deporte = ?", "e.orden, e.nombre", deporte);
		logger.debug("Fin obtenerActivosPublicosPorDeporte: deporte={}, total={}", deporte, equipos.size());
		return equipos;
	}

	@Override
	public Map<String, List<Equipo>> obtenerEquiposAgrupadosPorCategoria(String deporte) {
		logger.debug("Inicio obtenerEquiposAgrupadosPorCategoria: deporte={}", deporte);
		List<Equipo> equipos = consultar(SOLO_PUBLICOS, "e.deporte = ? and e.orden <> 'I'", "e.orden, e.nombre", deporte);

		Map<String, List<Equipo>> agrupados = equipos.stream().collect(Collectors.groupingBy(Equipo::getOrden));
		logger.debug("Fin obtenerEquiposAgrupadosPorCategoria: deporte={}, grupos={}", deporte, agrupados.size());
		return agrupados;
	}

	/**
	 * {@code filtroTemporada} (SOLO_ACTIVOS o SOLO_PUBLICOS) lleva un parámetro: la temporada; con -1 (sin temporada
	 * activa) no filtra. {@code condicion} y sus {@code parametros} van después.
	 */
	private List<Equipo> consultar(String filtroTemporada, String condicion, String orden, Object... parametros) {
		long temporadaId = TemporadaActivaId.obtenerOMenosUno(jdbcTemplate);
		List<Object> args = new ArrayList<>();
		args.add(temporadaId);
		List<String> condiciones = new ArrayList<>();

		if (filtroTemporada != null) {
			condiciones.add(filtroTemporada);
			args.add(temporadaId);
		}

		if (condicion != null) {
			condiciones.add(condicion);
			args.addAll(Arrays.asList(parametros));
		}

		String where = condiciones.isEmpty() ? "" : " WHERE " + String.join(" AND ", condiciones);

		return jdbcTemplate.query(BASE + where + " ORDER BY " + orden, EQUIPO_ROW_MAPPER, args.toArray());
	}

	private static Equipo mapRow(ResultSet rs, int rowNum) throws SQLException {
		Equipo equipo = new Equipo();
		equipo.setId(rs.getLong("id"));
		equipo.setCategoria(rs.getString("categoria"));
		equipo.setGrupo(rs.getString("grupo"));
		equipo.setOrden(rs.getString("orden"));
		equipo.setNombre(rs.getString("nombre"));
		equipo.setDeporte(rs.getString("deporte"));
		equipo.setModalidad(rs.getString("modalidad"));
		int activo = rs.getInt("activo_temporada");
		equipo.setActivoTemporada(rs.wasNull() ? null : activo == 1);
		equipo.setTipoTemporada(rs.getString("tipo_temporada"));
		return equipo;
	}

}
