package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.CuentaSinJugadoresDao;
import com.mikedev.mutxamelcf.model.CuentaSinJugadores;

@Repository
public class CuentaSinJugadoresDaoImpl implements CuentaSinJugadoresDao {

	private static final String COLUMNAS = """
			U.ID, U.EMAIL, U.NOMBRE, U.ACTIVO, U.FECHA_AVISO_SIN_JUGADORES, U.DESACTIVADA_SIN_JUGADORES""";

	private static final RowMapper<CuentaSinJugadores> MAPPER = (rs, n) -> new CuentaSinJugadores(rs.getLong("ID"),
			rs.getString("EMAIL"), rs.getString("NOMBRE"), rs.getInt("ACTIVO") == 1,
			rs.getTimestamp("FECHA_AVISO_SIN_JUGADORES"), rs.getInt("DESACTIVADA_SIN_JUGADORES") == 1);

	private final JdbcTemplate jdbcTemplate;

	public CuentaSinJugadoresDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<CuentaSinJugadores> listarSinJugadoresActivos(LocalDate hoy) {
		// Un jugador cuenta como activo si no está de baja o si es una prueba de una temporada que no ha terminado
		// (las pruebas del borrador nacen con FECHA_BAJA para ocultarlas, pero su familia no debe perder la cuenta)
		String jugadorActivo = """
				(J.FECHA_BAJA IS NULL OR EXISTS (
				    SELECT 1 FROM JUGADOR_TEMPORADA JT
				    INNER JOIN TEMPORADAS T ON T.ID = JT.TEMPORADA_ID
				    WHERE JT.JUGADOR_ID = J.ID AND JT.ESTADO = 'PRUEBA' AND T.FECHA_FIN >= ?))""";

		String sql = """
				SELECT %s
				FROM USUARIOS_APP U
				WHERE U.FECHA_ELIMINACION IS NULL
				  AND EXISTS (
				      SELECT 1 FROM USUARIOS_APP_ROLES UR
				      INNER JOIN ROLES_APP R ON R.ID = UR.ROL_ID
				      WHERE UR.USUARIO_APP_ID = U.ID AND R.CODIGO IN ('FAMILIAR', 'JUGADOR'))
				  AND NOT EXISTS (
				      SELECT 1 FROM USUARIOS_APP_ROLES UR
				      INNER JOIN ROLES_APP R ON R.ID = UR.ROL_ID
				      WHERE UR.USUARIO_APP_ID = U.ID
				        AND R.CODIGO IN ('ENTRENADOR', 'COORDINADOR', 'ADMIN_APP', 'RETRANSMISION'))
				  AND NOT EXISTS (
				      SELECT 1 FROM USUARIOS_APP_JUGADORES UAJ
				      INNER JOIN JUGADORES J ON J.ID = UAJ.JUGADOR_ID
				      WHERE UAJ.USUARIO_APP_ID = U.ID AND %s)
				  AND NOT EXISTS (
				      SELECT 1 FROM USUARIOS_APP_FAMILIARES UAF
				      INNER JOIN FAMILIARES_JUGADOR FJ ON FJ.FAMILIAR_ID = UAF.FAMILIAR_ID
				      INNER JOIN JUGADORES J ON J.ID = FJ.JUGADOR_ID
				      WHERE UAF.USUARIO_APP_ID = U.ID AND %s)
				ORDER BY U.ID
				""".formatted(COLUMNAS, jugadorActivo, jugadorActivo);

		Date fecha = Date.valueOf(hoy);

		return jdbcTemplate.query(sql, MAPPER, fecha, fecha);
	}

	@Override
	public List<CuentaSinJugadores> listarMarcadas() {
		return jdbcTemplate.query("SELECT " + COLUMNAS + """

				FROM USUARIOS_APP U
				WHERE U.FECHA_ELIMINACION IS NULL
				  AND (U.FECHA_AVISO_SIN_JUGADORES IS NOT NULL OR U.DESACTIVADA_SIN_JUGADORES = 1)
				ORDER BY U.ID
				""", MAPPER);
	}

	@Override
	public int marcarAviso(long usuarioId, Timestamp fecha) {
		return jdbcTemplate.update("""
				UPDATE USUARIOS_APP SET FECHA_AVISO_SIN_JUGADORES = ?
				WHERE ID = ? AND ACTIVO = 1 AND FECHA_ELIMINACION IS NULL AND FECHA_AVISO_SIN_JUGADORES IS NULL
				""", fecha, usuarioId);
	}

	@Override
	public int desactivar(long usuarioId) {
		return jdbcTemplate.update("""
				UPDATE USUARIOS_APP SET ACTIVO = 0, DESACTIVADA_SIN_JUGADORES = 1
				WHERE ID = ? AND ACTIVO = 1 AND FECHA_ELIMINACION IS NULL AND FECHA_AVISO_SIN_JUGADORES IS NOT NULL
				""", usuarioId);
	}

	@Override
	public int cancelarAviso(long usuarioId) {
		// Una cuenta que desactivó el job (ACTIVO = 0 y marca a 1) no se toca aquí: se reactiva con reactivar()
		return jdbcTemplate.update("""
				UPDATE USUARIOS_APP SET FECHA_AVISO_SIN_JUGADORES = NULL, DESACTIVADA_SIN_JUGADORES = 0
				WHERE ID = ? AND (FECHA_AVISO_SIN_JUGADORES IS NOT NULL OR DESACTIVADA_SIN_JUGADORES = 1)
				  AND (ACTIVO = 1 OR DESACTIVADA_SIN_JUGADORES = 0)
				""", usuarioId);
	}

	@Override
	public int reactivar(long usuarioId) {
		return jdbcTemplate.update("""
				UPDATE USUARIOS_APP SET ACTIVO = 1, FECHA_AVISO_SIN_JUGADORES = NULL, DESACTIVADA_SIN_JUGADORES = 0
				WHERE ID = ? AND ACTIVO = 0 AND DESACTIVADA_SIN_JUGADORES = 1 AND FECHA_ELIMINACION IS NULL
				""", usuarioId);
	}
}
