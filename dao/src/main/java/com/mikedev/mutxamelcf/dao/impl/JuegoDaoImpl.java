package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.JuegoDao;

@Repository
public class JuegoDaoImpl implements JuegoDao {

	/** PAREJAS puntúa por tiempo (menos es mejor); los demás juegos, por puntos (más es mejor). */
	private static boolean esDeTiempo(String juego) {
		return JUEGO_DE_TIEMPO.equals(juego);
	}

	/** El orden de la mejor marca (sin desempates): menos tiempo (PAREJAS) o más puntos. */
	private static String ordenMarca(String juego, String prefijo) {
		return esDeTiempo(juego) ? prefijo + "DURACION_MS ASC" : prefijo + "PUNTOS DESC";
	}

	/**
	 * La mejor marca de cada cuenta visible en el ranking (con alias, no oculta, cuenta activa y no eliminada): una
	 * fila por cuenta, la de más puntos (PAREJAS: la de menos tiempo) y, si empata, la más antigua. Añade a
	 * {@code args} el juego y las fechas.
	 */
	private static String mejores(String juego, LocalDateTime desde, LocalDateTime hasta, List<Object> args) {
		args.add(juego);

		StringBuilder sql = new StringBuilder("""
				SELECT USUARIO_APP_ID, ALIAS, PUNTOS, DURACION_MS, FECHA FROM (
				    SELECT P.USUARIO_APP_ID, F.ALIAS, P.PUNTOS, P.DURACION_MS, P.FECHA,
				           ROW_NUMBER() OVER (PARTITION BY P.USUARIO_APP_ID
				                              ORDER BY\s""" + ordenMarca(juego, "P.") + """
				, P.FECHA ASC, P.ID ASC) AS RN
				    FROM JUEGO_PUNTUACIONES P
				    JOIN JUEGO_PERFILES F ON F.USUARIO_APP_ID = P.USUARIO_APP_ID
				    JOIN USUARIOS_APP U ON U.ID = P.USUARIO_APP_ID
				    WHERE P.JUEGO = ?
				""");

		if (desde != null) {
			sql.append(" AND P.FECHA >= ?");
			args.add(ts(desde));
		}

		if (hasta != null) {
			sql.append(" AND P.FECHA < ?");
			args.add(ts(hasta));
		}

		return sql.append("""
				 AND F.OCULTO = 0
				      AND F.ALIAS IS NOT NULL
				      AND U.ACTIVO = 1
				      AND U.FECHA_ELIMINACION IS NULL
				) MARCAS WHERE RN = 1
				""").toString();
	}

	private static final RowMapper<Perfil> PERFIL = (rs, n) -> new Perfil(rs.getLong("USUARIO_APP_ID"),
			rs.getString("ALIAS"), rs.getString("EQUIPACION"), rs.getInt("OCULTO") == 1);

	private static final String PERFIL_ADMIN_SELECT = """
			SELECT F.USUARIO_APP_ID, F.ALIAS, F.EQUIPACION, F.OCULTO, F.FECHA_ALTA,
			       U.NOMBRE, U.APELLIDOS, U.EMAIL,
			       (SELECT COUNT(*) FROM JUEGO_PUNTUACIONES P WHERE P.USUARIO_APP_ID = F.USUARIO_APP_ID) AS PARTIDAS,
			       (SELECT COALESCE(MAX(P.PUNTOS), 0) FROM JUEGO_PUNTUACIONES P
			         WHERE P.USUARIO_APP_ID = F.USUARIO_APP_ID AND P.JUEGO = 'ATRAPA') AS M_ATRAPA,
			       (SELECT COALESCE(MAX(P.PUNTOS), 0) FROM JUEGO_PUNTUACIONES P
			         WHERE P.USUARIO_APP_ID = F.USUARIO_APP_ID AND P.JUEGO = 'CABEZAZOS') AS M_CABEZAZOS,
			       (SELECT COALESCE(MIN(P.DURACION_MS), 0) FROM JUEGO_PUNTUACIONES P
			         WHERE P.USUARIO_APP_ID = F.USUARIO_APP_ID AND P.JUEGO = 'PAREJAS') AS M_PAREJAS
			FROM JUEGO_PERFILES F
			JOIN USUARIOS_APP U ON U.ID = F.USUARIO_APP_ID
			""";

	private static final RowMapper<PerfilAdmin> PERFIL_ADMIN = (rs, n) -> {
		String nombre = ((rs.getString("NOMBRE") == null ? "" : rs.getString("NOMBRE")) + " "
				+ (rs.getString("APELLIDOS") == null ? "" : rs.getString("APELLIDOS"))).trim();

		return new PerfilAdmin(rs.getLong("USUARIO_APP_ID"), rs.getString("ALIAS"), rs.getString("EQUIPACION"),
				rs.getInt("OCULTO") == 1, nombre, rs.getString("EMAIL"), fecha(rs.getTimestamp("FECHA_ALTA")),
				rs.getInt("PARTIDAS"), rs.getInt("M_ATRAPA"), rs.getInt("M_CABEZAZOS"),
				rs.getInt("M_PAREJAS"));
	};

	private final JdbcTemplate jdbcTemplate;

	public JuegoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static LocalDateTime fecha(Timestamp t) {
		return t == null ? null : t.toLocalDateTime();
	}

	private static Timestamp ts(LocalDateTime f) {
		return f == null ? null : Timestamp.valueOf(f);
	}

	@Override
	public Optional<Perfil> obtenerPerfil(long usuarioId) {
		return jdbcTemplate.query("""
				SELECT USUARIO_APP_ID, ALIAS, EQUIPACION, OCULTO FROM JUEGO_PERFILES WHERE USUARIO_APP_ID = ?
				""", PERFIL, usuarioId).stream().findFirst();
	}

	@Override
	public void crearPerfil(long usuarioId, String alias, String aliasNorm, String equipacion, LocalDateTime ahora) {
		jdbcTemplate.update("""
				INSERT INTO JUEGO_PERFILES (USUARIO_APP_ID, ALIAS, ALIAS_NORMALIZADO, EQUIPACION, OCULTO, FECHA_ALTA,
				                            FECHA_MODIFICACION)
				VALUES (?, ?, ?, ?, 0, ?, ?)
				""", usuarioId, alias, aliasNorm, equipacion, ts(ahora), ts(ahora));
	}

	@Override
	public void actualizarPerfil(long usuarioId, String alias, String aliasNorm, String equipacion,
			LocalDateTime ahora) {
		jdbcTemplate.update("""
				UPDATE JUEGO_PERFILES
				SET ALIAS = COALESCE(?, ALIAS), ALIAS_NORMALIZADO = COALESCE(?, ALIAS_NORMALIZADO),
				    EQUIPACION = COALESCE(?, EQUIPACION), FECHA_MODIFICACION = ?
				WHERE USUARIO_APP_ID = ?
				""", alias, aliasNorm, equipacion, ts(ahora), usuarioId);
	}

	@Override
	public void actualizarOculto(long usuarioId, boolean oculto, LocalDateTime ahora) {
		jdbcTemplate.update("UPDATE JUEGO_PERFILES SET OCULTO = ?, FECHA_MODIFICACION = ? WHERE USUARIO_APP_ID = ?",
				oculto ? 1 : 0, ts(ahora), usuarioId);
	}

	@Override
	public boolean existeAlias(String aliasNorm, long exceptoUsuarioId) {
		Integer filas = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM JUEGO_PERFILES WHERE ALIAS_NORMALIZADO = ? AND USUARIO_APP_ID <> ?", Integer.class,
				aliasNorm, exceptoUsuarioId);

		return filas != null && filas > 0;
	}

	@Override
	public void insertarPuntuacion(long usuarioId, String juego, int puntos, int duracionMs, LocalDateTime fecha) {
		jdbcTemplate.update("""
				INSERT INTO JUEGO_PUNTUACIONES (USUARIO_APP_ID, JUEGO, PUNTOS, DURACION_MS, FECHA)
				VALUES (?, ?, ?, ?, ?)
				""", usuarioId, juego, puntos, duracionMs, ts(fecha));
	}

	@Override
	public Optional<Marca> mejorMarca(long usuarioId, String juego, LocalDateTime desde, LocalDateTime hasta) {
		List<Object> args = new ArrayList<>(List.of(usuarioId, juego));
		StringBuilder sql = new StringBuilder(
				"SELECT PUNTOS, DURACION_MS, FECHA FROM JUEGO_PUNTUACIONES WHERE USUARIO_APP_ID = ? AND JUEGO = ?");

		if (desde != null) {
			sql.append(" AND FECHA >= ?");
			args.add(ts(desde));
		}

		if (hasta != null) {
			sql.append(" AND FECHA < ?");
			args.add(ts(hasta));
		}

		sql.append(" ORDER BY " + ordenMarca(juego, "") + ", FECHA ASC, ID ASC FETCH FIRST 1 ROWS ONLY");

		return jdbcTemplate.query(sql.toString(),
				(rs, n) -> new Marca(rs.getInt("PUNTOS"), rs.getInt("DURACION_MS"), fecha(rs.getTimestamp("FECHA"))),
				args.toArray())
				.stream().findFirst();
	}

	@Override
	public List<FilaRanking> ranking(String juego, LocalDateTime desde, LocalDateTime hasta, int limite) {
		List<Object> args = new ArrayList<>();
		String mejores = mejores(juego, desde, hasta, args);

		return jdbcTemplate.query(
				"SELECT * FROM (" + mejores + ") MEJORES ORDER BY " + ordenMarca(juego, "")
						+ ", FECHA ASC, USUARIO_APP_ID ASC FETCH FIRST " + Math.max(limite, 0) + " ROWS ONLY",
				(rs, n) -> new FilaRanking(rs.getLong("USUARIO_APP_ID"), rs.getString("ALIAS"), rs.getInt("PUNTOS"),
						rs.getInt("DURACION_MS"), fecha(rs.getTimestamp("FECHA"))),
				args.toArray());
	}

	@Override
	public int cuentasPorDelante(String juego, LocalDateTime desde, LocalDateTime hasta, long usuarioId,
			Marca marca) {
		List<Object> args = new ArrayList<>();
		String mejores = mejores(juego, desde, hasta, args);
		Timestamp f = ts(marca.fecha());

		// Por delante: más puntos (PAREJAS: menos tiempo) o igual marca y antes (y, si es el mismo instante, menor id).
		int medida = esDeTiempo(juego) ? marca.duracionMs() : marca.puntos();
		String mejorQue = esDeTiempo(juego) ? "DURACION_MS < ? OR (DURACION_MS = ?" : "PUNTOS > ? OR (PUNTOS = ?";

		args.addAll(List.of(usuarioId, medida, medida, f, f, usuarioId));

		Integer cuentas = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + mejores + ") MEJORES "
				+ "WHERE USUARIO_APP_ID <> ? AND (" + mejorQue + " AND (FECHA < ? "
				+ "OR (FECHA = ? AND USUARIO_APP_ID < ?))))", Integer.class, args.toArray());

		return cuentas == null ? 0 : cuentas;
	}

	@Override
	public int contarPuntuacionesDesde(long usuarioId, LocalDateTime desde) {
		Integer filas = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM JUEGO_PUNTUACIONES WHERE USUARIO_APP_ID = ? AND FECHA >= ?", Integer.class,
				usuarioId, ts(desde));

		return filas == null ? 0 : filas;
	}

	@Override
	public Optional<LocalDateTime> ultimaPuntuacion(long usuarioId) {
		return jdbcTemplate.query("SELECT MAX(FECHA) AS FECHA FROM JUEGO_PUNTUACIONES WHERE USUARIO_APP_ID = ?",
				(rs, n) -> fecha(rs.getTimestamp("FECHA")), usuarioId).stream().filter(f -> f != null).findFirst();
	}

	@Override
	public List<PerfilAdmin> listarPerfilesAdmin(String aliasNormContiene, int limite) {
		String filtro = aliasNormContiene == null ? "" : aliasNormContiene.replace("%", "").replace("_", "");

		return jdbcTemplate.query(PERFIL_ADMIN_SELECT + "WHERE (? = '' OR F.ALIAS_NORMALIZADO LIKE ?) "
				+ "ORDER BY F.FECHA_ALTA DESC, F.USUARIO_APP_ID DESC FETCH FIRST " + Math.max(limite, 0)
				+ " ROWS ONLY", PERFIL_ADMIN, filtro, "%" + filtro + "%");
	}

	@Override
	public Optional<PerfilAdmin> obtenerPerfilAdmin(long usuarioId) {
		return jdbcTemplate.query(PERFIL_ADMIN_SELECT + "WHERE F.USUARIO_APP_ID = ?", PERFIL_ADMIN, usuarioId)
				.stream().findFirst();
	}

	@Override
	public List<Puntuacion> ultimasPuntuaciones(long usuarioId, int limite) {
		return jdbcTemplate.query("""
				SELECT ID, JUEGO, PUNTOS, DURACION_MS, FECHA FROM JUEGO_PUNTUACIONES
				WHERE USUARIO_APP_ID = ?
				ORDER BY FECHA DESC, ID DESC
				FETCH FIRST\s""" + Math.max(limite, 0) + " ROWS ONLY",
				(rs, n) -> new Puntuacion(rs.getLong("ID"), rs.getString("JUEGO"), rs.getInt("PUNTOS"),
						rs.getInt("DURACION_MS"), fecha(rs.getTimestamp("FECHA"))),
				usuarioId);
	}

	@Override
	public int borrarPuntuacion(long puntuacionId) {
		return jdbcTemplate.update("DELETE FROM JUEGO_PUNTUACIONES WHERE ID = ?", puntuacionId);
	}

	@Override
	public int borrarPuntuacionesDeUsuario(long usuarioId) {
		return jdbcTemplate.update("DELETE FROM JUEGO_PUNTUACIONES WHERE USUARIO_APP_ID = ?", usuarioId);
	}

	@Override
	public void eliminarTodoDeUsuario(long usuarioId) {
		borrarPuntuacionesDeUsuario(usuarioId);
		jdbcTemplate.update("DELETE FROM JUEGO_PERFILES WHERE USUARIO_APP_ID = ?", usuarioId);
	}

	@Override
	public List<ClubConEscudo> clubesConEscudo() {
		return jdbcTemplate.query("SELECT ID, NOMBRE, CODIGO_FFCV, ESCUDO_VERSION FROM CLUBES_RIVALES "
				+ "WHERE ESCUDO IS NOT NULL ORDER BY ID",
				(rs, n) -> new ClubConEscudo(rs.getLong("ID"), rs.getString("NOMBRE"),
						rs.getObject("CODIGO_FFCV") == null ? null : rs.getInt("CODIGO_FFCV"),
						rs.getLong("ESCUDO_VERSION")));
	}

	@Override
	public Set<Long> clubesDeLasClasificacionesActuales() {
		// Solo la temporada activa; sin temporada activa no hay "actuales". Nunca nuestras filas.
		return new LinkedHashSet<>(jdbcTemplate.queryForList("""
				SELECT DISTINCT CR.ID
				FROM CLASIFICACIONES C
				JOIN EQUIPO E ON E.ID = C.EQUIPO_ID
				JOIN CLUBES_RIVALES CR ON CR.CODIGO_FFCV = C.CODIGO_CLUB
				WHERE E.NOMBRE IN ('Primer Equipo', 'Filial')
				  AND C.TEMPORADA_ID IN (SELECT T.ID FROM TEMPORADAS T WHERE T.ACTIVA = 1)
				  AND C.ES_NUESTRO = 0
				""", Long.class));
	}
}
