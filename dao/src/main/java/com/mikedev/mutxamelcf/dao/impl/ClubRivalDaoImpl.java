package com.mikedev.mutxamelcf.dao.impl;

import java.io.ByteArrayInputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ClubRivalDao;
import com.mikedev.mutxamelcf.model.ClubRival;

@Repository
public class ClubRivalDaoImpl implements ClubRivalDao {

	/* El BLOB nunca se lee en las consultas de datos: solo si existe (ESCUDO IS NOT NULL). */
	private static final String CAMPOS = "ID, NOMBRE, CODIGO_FFCV, ESCUDO_VERSION, FECHA_ACTUALIZACION, FECHA_CREACION, "
			+ "CASE WHEN ESCUDO IS NOT NULL THEN 1 ELSE 0 END AS TIENE_ESCUDO";

	private final JdbcTemplate jdbcTemplate;

	public ClubRivalDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Long crear(String nombre, Integer codigoFfcv) {
		jdbcTemplate.update("INSERT INTO CLUBES_RIVALES (NOMBRE, CODIGO_FFCV, FECHA_CREACION) VALUES (?, ?, ?)", nombre,
				codigoFfcv, Timestamp.valueOf(LocalDateTime.now()));

		return jdbcTemplate.queryForObject("SELECT ID FROM CLUBES_RIVALES WHERE NOMBRE = ?", Long.class, nombre);
	}

	@Override
	public ClubRival obtenerPorId(Long id) {
		return primero("SELECT " + CAMPOS + " FROM CLUBES_RIVALES WHERE ID = ?", id);
	}

	@Override
	public ClubRival obtenerPorCodigo(int codigoFfcv) {
		return primero("SELECT " + CAMPOS + " FROM CLUBES_RIVALES WHERE CODIGO_FFCV = ?", codigoFfcv);
	}

	@Override
	public ClubRival obtenerPorAlias(String nombreNormalizado) {
		return primero("SELECT " + CAMPOS + " FROM CLUBES_RIVALES WHERE ID = "
				+ "(SELECT CLUB_RIVAL_ID FROM CLUBES_RIVALES_ALIAS WHERE NOMBRE_NORMALIZADO = ?)", nombreNormalizado);
	}

	@Override
	public ClubRival obtenerPorNombre(String nombre) {
		return primero("SELECT " + CAMPOS + " FROM CLUBES_RIVALES WHERE NOMBRE = ?", nombre);
	}

	@Override
	public List<ClubRival> listar() {
		return jdbcTemplate.query("SELECT " + CAMPOS + " FROM CLUBES_RIVALES ORDER BY NOMBRE, ID",
				ClubRivalDaoImpl::mapRow);
	}

	@Override
	public boolean anadirAlias(Long clubId, String nombreNormalizado) {
		Integer existe = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM CLUBES_RIVALES_ALIAS WHERE NOMBRE_NORMALIZADO = ?", Integer.class,
				nombreNormalizado);

		if (existe != null && existe > 0) {
			return false;
		}

		jdbcTemplate.update("INSERT INTO CLUBES_RIVALES_ALIAS (CLUB_RIVAL_ID, NOMBRE_NORMALIZADO) VALUES (?, ?)", clubId,
				nombreNormalizado);

		return true;
	}

	@Override
	public List<String> listarAlias(Long clubId) {
		return jdbcTemplate.queryForList(
				"SELECT NOMBRE_NORMALIZADO FROM CLUBES_RIVALES_ALIAS WHERE CLUB_RIVAL_ID = ? ORDER BY NOMBRE_NORMALIZADO",
				String.class, clubId);
	}

	@Override
	public Map<String, Long> mapaAlias() {
		Map<String, Long> mapa = new LinkedHashMap<>();

		jdbcTemplate.query("SELECT NOMBRE_NORMALIZADO, CLUB_RIVAL_ID FROM CLUBES_RIVALES_ALIAS", rs -> {
			mapa.put(rs.getString(1), rs.getLong(2));
		});

		return mapa;
	}

	@Override
	public Map<Long, List<String>> aliasPorClub() {
		Map<Long, List<String>> mapa = new LinkedHashMap<>();

		jdbcTemplate.query(
				"SELECT CLUB_RIVAL_ID, NOMBRE_NORMALIZADO FROM CLUBES_RIVALES_ALIAS ORDER BY NOMBRE_NORMALIZADO", rs -> {
					mapa.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
				});

		return mapa;
	}

	@Override
	public void ponerCodigo(Long clubId, Integer codigoFfcv) {
		jdbcTemplate.update("UPDATE CLUBES_RIVALES SET CODIGO_FFCV = ? WHERE ID = ?", codigoFfcv, clubId);
	}

	@Override
	public void renombrar(Long clubId, String nombre) {
		jdbcTemplate.update("UPDATE CLUBES_RIVALES SET NOMBRE = ? WHERE ID = ?", nombre, clubId);
	}

	@Override
	public void guardarEscudo(Long clubId, byte[] png) {
		jdbcTemplate.update(
				"UPDATE CLUBES_RIVALES SET ESCUDO = ?, ESCUDO_VERSION = ESCUDO_VERSION + 1, FECHA_ACTUALIZACION = ? "
						+ "WHERE ID = ?",
				ps -> {
					ps.setBinaryStream(1, new ByteArrayInputStream(png), png.length);
					ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
					ps.setLong(3, clubId);
				});
	}

	@Override
	public void quitarEscudo(Long clubId) {
		jdbcTemplate.update(
				"UPDATE CLUBES_RIVALES SET ESCUDO = NULL, ESCUDO_VERSION = ESCUDO_VERSION + 1, FECHA_ACTUALIZACION = ? "
						+ "WHERE ID = ?",
				Timestamp.valueOf(LocalDateTime.now()), clubId);
	}

	@Override
	public void copiarEscudo(Long origenId, Long destinoId) {
		jdbcTemplate.update(
				"UPDATE CLUBES_RIVALES SET ESCUDO = (SELECT O.ESCUDO FROM CLUBES_RIVALES O WHERE O.ID = ?), "
						+ "ESCUDO_VERSION = ESCUDO_VERSION + 1, FECHA_ACTUALIZACION = ? WHERE ID = ?",
				origenId, Timestamp.valueOf(LocalDateTime.now()), destinoId);
	}

	@Override
	public byte[] obtenerEscudo(Long clubId) {
		return jdbcTemplate.query("SELECT ESCUDO FROM CLUBES_RIVALES WHERE ID = ?",
				rs -> rs.next() ? rs.getBytes(1) : null, clubId);
	}

	@Override
	public void moverAlias(Long origenId, Long destinoId) {
		jdbcTemplate.update("UPDATE CLUBES_RIVALES_ALIAS SET CLUB_RIVAL_ID = ? WHERE CLUB_RIVAL_ID = ?", destinoId,
				origenId);
	}

	@Override
	public void moverPartidos(Long origenId, Long destinoId) {
		jdbcTemplate.update("UPDATE PARTIDOS SET CLUB_RIVAL_ID = ? WHERE CLUB_RIVAL_ID = ?", destinoId, origenId);
	}

	@Override
	public int contarPartidos(Long clubId) {
		Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PARTIDOS WHERE CLUB_RIVAL_ID = ?",
				Integer.class, clubId);

		return total == null ? 0 : total;
	}

	@Override
	public int contarClasificaciones(Long clubId) {
		Integer total = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM CLASIFICACIONES WHERE CODIGO_CLUB = (SELECT CODIGO_FFCV FROM CLUBES_RIVALES WHERE ID = ?)",
				Integer.class, clubId);

		return total == null ? 0 : total;
	}

	@Override
	public void borrar(Long clubId) {
		jdbcTemplate.update("DELETE FROM CLUBES_RIVALES_ALIAS WHERE CLUB_RIVAL_ID = ?", clubId);
		jdbcTemplate.update("DELETE FROM CLUBES_RIVALES WHERE ID = ?", clubId);
	}

	@Override
	public Map<Long, Integer> contarPartidosPorClub() {
		Map<Long, Integer> mapa = new LinkedHashMap<>();

		jdbcTemplate.query(
				"SELECT CLUB_RIVAL_ID, COUNT(*) FROM PARTIDOS WHERE CLUB_RIVAL_ID IS NOT NULL GROUP BY CLUB_RIVAL_ID",
				rs -> {
					mapa.put(rs.getLong(1), rs.getInt(2));
				});

		return mapa;
	}

	@Override
	public Map<Long, List<String>> equiposPorClubDePartidos() {
		Map<Long, Set<String>> conjuntos = new LinkedHashMap<>();

		jdbcTemplate.query("SELECT DISTINCT P.CLUB_RIVAL_ID, E.NOMBRE FROM PARTIDOS P JOIN EQUIPO E ON E.ID = P.EQUIPO_ID "
				+ "WHERE P.CLUB_RIVAL_ID IS NOT NULL ORDER BY E.NOMBRE", rs -> {
					conjuntos.computeIfAbsent(rs.getLong(1), k -> new LinkedHashSet<>()).add(rs.getString(2));
				});

		Map<Long, List<String>> mapa = new LinkedHashMap<>();
		conjuntos.forEach((k, v) -> mapa.put(k, new ArrayList<>(v)));

		return mapa;
	}

	private ClubRival primero(String sql, Object... args) {
		List<ClubRival> filas = jdbcTemplate.query(sql, ClubRivalDaoImpl::mapRow, args);

		return filas.isEmpty() ? null : filas.get(0);
	}

	private static ClubRival mapRow(ResultSet rs, int rowNum) throws SQLException {
		ClubRival club = new ClubRival();
		club.setId(rs.getLong("ID"));
		club.setNombre(rs.getString("NOMBRE"));

		int codigo = rs.getInt("CODIGO_FFCV");
		club.setCodigoFfcv(rs.wasNull() ? null : codigo);

		club.setEscudoVersion(rs.getLong("ESCUDO_VERSION"));
		club.setTieneEscudo(rs.getInt("TIENE_ESCUDO") == 1);

		Timestamp actualizacion = rs.getTimestamp("FECHA_ACTUALIZACION");
		club.setFechaActualizacion(actualizacion == null ? null : actualizacion.toLocalDateTime());

		Timestamp creacion = rs.getTimestamp("FECHA_CREACION");
		club.setFechaCreacion(creacion == null ? null : creacion.toLocalDateTime());

		return club;
	}
}
