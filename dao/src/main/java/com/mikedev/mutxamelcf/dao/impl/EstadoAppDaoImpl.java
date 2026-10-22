package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.EstadoAppDao;

@Repository
public class EstadoAppDaoImpl implements EstadoAppDao {

	/** Cuenta activa: ACTIVO = 1 y no eliminada por su titular. */
	private static final String ACTIVA = "U.ACTIVO = 1 AND U.FECHA_ELIMINACION IS NULL";

	private static final String SIN_DISPOSITIVO_ACTIVO = """
			NOT EXISTS (SELECT 1 FROM USUARIOS_APP_DISPOSITIVOS D
			            WHERE D.USUARIO_APP_ID = U.ID AND D.ACTIVO = 1)
			""";

	private static final String CAMPOS = "U.ID, U.EMAIL, U.FECHA_ULTIMO_ACCESO";

	private static final RowMapper<Usuario> USUARIO = (rs, n) -> {
		Timestamp acceso = rs.getTimestamp("FECHA_ULTIMO_ACCESO");
		return new Usuario(rs.getLong("ID"), rs.getString("EMAIL"), acceso == null ? null : acceso.toLocalDateTime());
	};

	private final JdbcTemplate jdbcTemplate;

	public EstadoAppDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Totales contarCuentas() {

		return jdbcTemplate.queryForObject("""
				SELECT
				    COALESCE(SUM(CASE WHEN U.ACTIVO = 1 THEN 1 ELSE 0 END), 0) AS ACTIVAS,
				    COALESCE(SUM(CASE WHEN U.ACTIVO = 0 AND U.FECHA_ACTIVACION IS NULL THEN 1 ELSE 0 END), 0) AS PENDIENTES,
				    COALESCE(SUM(CASE WHEN U.ACTIVO = 0 AND U.FECHA_ACTIVACION IS NOT NULL THEN 1 ELSE 0 END), 0) AS DESACTIVADAS
				FROM USUARIOS_APP U
				WHERE U.FECHA_ELIMINACION IS NULL
				""",
				(rs, n) -> new Totales(rs.getInt("ACTIVAS"), rs.getInt("PENDIENTES"), rs.getInt("DESACTIVADAS")));
	}

	@Override
	public int contarActivasSinDispositivo() {
		return contar("SELECT COUNT(*) FROM USUARIOS_APP U WHERE " + ACTIVA + " AND " + SIN_DISPOSITIVO_ACTIVO);
	}

	@Override
	public int contarActivasSinNotificaciones() {
		return contar("SELECT COUNT(*) FROM USUARIOS_APP U WHERE " + ACTIVA + " AND " + SIN_NOTIFICACIONES);
	}

	@Override
	public int contarInactivasDesde(LocalDateTime desde) {
		return contar("SELECT COUNT(*) FROM USUARIOS_APP U WHERE " + ACTIVA + " AND " + INACTIVA, Timestamp.valueOf(desde));
	}

	private static final String SIN_NOTIFICACIONES = """
			EXISTS (SELECT 1 FROM PREFERENCIAS_NOTIFICACION P
			        WHERE P.USUARIO_APP_ID = U.ID AND P.NOTIFICACIONES_ACTIVADAS = 0)
			""";

	private static final String INACTIVA = "COALESCE(U.FECHA_ULTIMO_ACCESO, U.FECHA_ACTIVACION) < ?";

	@Override
	public List<DispositivosPorVersion> contarDispositivosPorVersion() {

		return jdbcTemplate.query("""
				SELECT D.PLATAFORMA, D.VERSION_APP, COUNT(*) AS DISPOSITIVOS
				FROM USUARIOS_APP_DISPOSITIVOS D
				INNER JOIN USUARIOS_APP U ON U.ID = D.USUARIO_APP_ID
				WHERE D.ACTIVO = 1 AND U.ACTIVO = 1 AND U.FECHA_ELIMINACION IS NULL
				GROUP BY D.PLATAFORMA, D.VERSION_APP
				""",
				(rs, n) -> new DispositivosPorVersion(
						rs.getString("PLATAFORMA"), rs.getString("VERSION_APP"), rs.getInt("DISPOSITIVOS")));
	}

	@Override
	public List<Usuario> listarActivasSinDispositivo(int maximo) {
		return listar("WHERE " + ACTIVA + " AND " + SIN_DISPOSITIVO_ACTIVO, maximo);
	}

	@Override
	public List<Usuario> listarActivasSinNotificaciones(int maximo) {
		return listar("WHERE " + ACTIVA + " AND " + SIN_NOTIFICACIONES, maximo);
	}

	@Override
	public List<Usuario> listarInactivasDesde(LocalDateTime desde, int maximo) {
		return listar("WHERE " + ACTIVA + " AND " + INACTIVA, maximo, Timestamp.valueOf(desde));
	}

	@Override
	public List<Usuario> listarActivasConVersiones(String plataforma, Collection<String> versiones, int maximo) {

		if (versiones == null || versiones.isEmpty()) {
			return List.of();
		}

		String marcadores = versiones.stream().map(v -> "?").collect(Collectors.joining(", "));

		List<Object> parametros = new ArrayList<>();
		parametros.add(plataforma);
		parametros.addAll(versiones);

		return listar("""
				WHERE %s AND EXISTS (
				    SELECT 1 FROM USUARIOS_APP_DISPOSITIVOS D
				    WHERE D.USUARIO_APP_ID = U.ID AND D.ACTIVO = 1
				      AND D.PLATAFORMA = ? AND D.VERSION_APP IN (%s))
				""".formatted(ACTIVA, marcadores), maximo, parametros.toArray());
	}

	@Override
	public Map<Long, List<String>> versionesDeDispositivosActivos(Collection<Long> usuarioIds) {

		Map<Long, List<String>> resultado = new HashMap<>();

		if (usuarioIds == null || usuarioIds.isEmpty()) {
			return resultado;
		}

		String marcadores = usuarioIds.stream().map(id -> "?").collect(Collectors.joining(", "));

		jdbcTemplate.query("""
				SELECT D.USUARIO_APP_ID, D.VERSION_APP
				FROM USUARIOS_APP_DISPOSITIVOS D
				WHERE D.ACTIVO = 1 AND D.USUARIO_APP_ID IN (%s)
				GROUP BY D.USUARIO_APP_ID, D.VERSION_APP
				""".formatted(marcadores), rs -> {
			resultado.computeIfAbsent(rs.getLong("USUARIO_APP_ID"), id -> new ArrayList<>())
					.add(rs.getString("VERSION_APP"));
		}, usuarioIds.toArray());

		return resultado;
	}

	private int contar(String sql, Object... parametros) {

		Integer total = jdbcTemplate.queryForObject(sql, Integer.class, parametros);

		return total == null ? 0 : total;
	}

	/** Las cuentas más recientes primero, con un máximo. */
	private List<Usuario> listar(String condicion, int maximo, Object... parametros) {

		return jdbcTemplate.query(
				"SELECT " + CAMPOS + " FROM USUARIOS_APP U " + condicion
						+ " ORDER BY U.FECHA_ULTIMO_ACCESO DESC NULLS LAST, U.ID FETCH FIRST " + Math.max(1, maximo)
						+ " ROWS ONLY",
				USUARIO, parametros);
	}
}
