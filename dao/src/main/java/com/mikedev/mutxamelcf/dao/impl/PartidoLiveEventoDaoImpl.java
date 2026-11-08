package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.model.EventoDirectoResumen;
import com.mikedev.mutxamelcf.model.JugadorBasico;
import com.mikedev.mutxamelcf.model.PartidoLiveEvento;

@Repository
public class PartidoLiveEventoDaoImpl implements PartidoLiveEventoDao {

	private static final RowMapper<PartidoLiveEvento> ROW_MAPPER = PartidoLiveEventoDaoImpl::mapRow;

	private static final String CAMPOS = """
			ID, PARTIDO_ID, TIPO, JUGADOR_ID, AUTOR_TEXTO, USUARIO_APP_ID, FECHA, ANULADO, FECHA_ANULACION,
			RESULTADO_ANTERIOR, GOLES_FAVOR_ANTERIOR, GOLES_CONTRA_ANTERIOR, AVISO_ANTERIOR, ESTADISTICAS_GUARDADAS, MINUTO
			""";

	private final JdbcTemplate jdbcTemplate;

	public PartidoLiveEventoDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public PartidoLiveEvento crear(PartidoLiveEvento evento) {

		KeyHolder keyHolder = new GeneratedKeyHolder();

		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO PARTIDO_LIVE_EVENTOS (
					    PARTIDO_ID, TIPO, JUGADOR_ID, AUTOR_TEXTO, USUARIO_APP_ID, FECHA,
					    RESULTADO_ANTERIOR, GOLES_FAVOR_ANTERIOR, GOLES_CONTRA_ANTERIOR, AVISO_ANTERIOR,
					    ESTADISTICAS_GUARDADAS, MINUTO)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", new String[] { "ID" });

			ps.setLong(1, evento.getPartidoId());
			ps.setString(2, evento.getTipo());
			ponerLong(ps, 3, evento.getJugadorId());
			ponerString(ps, 4, evento.getAutorTexto());
			ps.setLong(5, evento.getUsuarioAppId());
			ps.setTimestamp(6, Timestamp.valueOf(evento.getFecha()));
			ponerString(ps, 7, evento.getResultadoAnterior());
			ponerInteger(ps, 8, evento.getGolesFavorAnterior());
			ponerInteger(ps, 9, evento.getGolesContraAnterior());
			ponerInteger(ps, 10, evento.getAvisoAnterior() == null ? null : (evento.getAvisoAnterior() ? 1 : 0));
			ps.setInt(11, evento.isEstadisticasGuardadas() ? 1 : 0);
			ponerInteger(ps, 12, evento.getMinuto());

			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();

		if (key == null) {
			throw new IllegalStateException("No se pudo obtener el ID generado del evento");
		}

		evento.setId(key.longValue());

		return evento;
	}

	@Override
	public PartidoLiveEvento obtenerPorId(Long id) {

		List<PartidoLiveEvento> resultado = jdbcTemplate.query(
				"SELECT " + CAMPOS + " FROM PARTIDO_LIVE_EVENTOS WHERE ID = ?", ROW_MAPPER, id);

		return resultado.isEmpty() ? null : resultado.get(0);
	}

	@Override
	public List<PartidoLiveEvento> obtenerNoAnuladosPorPartido(Long partidoId) {

		return jdbcTemplate.query(
				"SELECT " + CAMPOS + " FROM PARTIDO_LIVE_EVENTOS WHERE PARTIDO_ID = ? AND ANULADO = 0 "
						+ "ORDER BY FECHA, ID",
				ROW_MAPPER, partidoId);
	}

	@Override
	public int anular(Long id, LocalDateTime fechaAnulacion) {

		return jdbcTemplate.update(
				"UPDATE PARTIDO_LIVE_EVENTOS SET ANULADO = 1, FECHA_ANULACION = ? WHERE ID = ? AND ANULADO = 0",
				Timestamp.valueOf(fechaAnulacion), id);
	}

	@Override
	public int actualizarMinuto(Long id, Integer minuto) {

		return jdbcTemplate.update(
				"UPDATE PARTIDO_LIVE_EVENTOS SET MINUTO = ? WHERE ID = ? AND ANULADO = 0", minuto, id);
	}

	@Override
	public List<JugadorBasico> obtenerJugadoresBasicos(Collection<Long> jugadorIds) {

		if (jugadorIds == null || jugadorIds.isEmpty()) {
			return List.of();
		}

		String marcadores = jugadorIds.stream().map(id -> "?").collect(Collectors.joining(", "));

		return jdbcTemplate.query(
				"SELECT ID, DORSAL, NOMBRE, APELLIDOS, NOMBRE_RETRANSMISION FROM JUGADORES WHERE ID IN ("
						+ marcadores + ")",
				(rs, n) -> mapJugadorBasico(rs, "ID", "DORSAL", "NOMBRE", "APELLIDOS", "NOMBRE_RETRANSMISION"),
				jugadorIds.toArray());
	}

	@Override
	public List<EventoDirectoResumen> obtenerResumenNoAnulados(Long partidoId) {

		// Una sola consulta (con el autor ya unido): la portada de la app la pide a menudo durante el partido.
		return jdbcTemplate.query("""
				SELECT E.TIPO, E.AUTOR_TEXTO, E.JUGADOR_ID, J.DORSAL AS J_DORSAL, J.NOMBRE AS J_NOMBRE,
				       J.APELLIDOS AS J_APELLIDOS, J.NOMBRE_RETRANSMISION AS J_NOMBRE_RETRANSMISION
				FROM PARTIDO_LIVE_EVENTOS E
				LEFT JOIN JUGADORES J ON J.ID = E.JUGADOR_ID
				WHERE E.PARTIDO_ID = ? AND E.ANULADO = 0
				ORDER BY E.FECHA, E.ID
				""", (rs, n) -> {
					rs.getLong("JUGADOR_ID");
					JugadorBasico jugador = rs.wasNull() ? null
							: mapJugadorBasico(rs, "JUGADOR_ID", "J_DORSAL", "J_NOMBRE", "J_APELLIDOS",
									"J_NOMBRE_RETRANSMISION");
					return new EventoDirectoResumen(rs.getString("TIPO"), rs.getString("AUTOR_TEXTO"), jugador);
				}, partidoId);
	}

	private static JugadorBasico mapJugadorBasico(ResultSet rs, String id, String dorsal, String nombre,
			String apellidos, String nombreRetransmision) throws SQLException {

		long jugadorId = rs.getLong(id);
		int numero = rs.getInt(dorsal);
		boolean sinDorsal = rs.wasNull();

		return new JugadorBasico(jugadorId, sinDorsal ? null : numero, rs.getString(nombre), rs.getString(apellidos),
				rs.getString(nombreRetransmision));
	}

	@Override
	public boolean existePorJugador(Long jugadorId) {

		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM PARTIDO_LIVE_EVENTOS WHERE JUGADOR_ID = ?", Integer.class, jugadorId);

		return count != null && count > 0;
	}

	private static PartidoLiveEvento mapRow(ResultSet rs, int rowNum) throws SQLException {

		PartidoLiveEvento evento = new PartidoLiveEvento();

		evento.setId(rs.getLong("ID"));
		evento.setPartidoId(rs.getLong("PARTIDO_ID"));
		evento.setTipo(rs.getString("TIPO"));

		long jugadorId = rs.getLong("JUGADOR_ID");
		evento.setJugadorId(rs.wasNull() ? null : jugadorId);

		evento.setAutorTexto(rs.getString("AUTOR_TEXTO"));
		evento.setUsuarioAppId(rs.getLong("USUARIO_APP_ID"));
		evento.setFecha(rs.getTimestamp("FECHA").toLocalDateTime());
		evento.setAnulado(rs.getInt("ANULADO") == 1);

		Timestamp anulacion = rs.getTimestamp("FECHA_ANULACION");
		evento.setFechaAnulacion(anulacion == null ? null : anulacion.toLocalDateTime());

		evento.setResultadoAnterior(rs.getString("RESULTADO_ANTERIOR"));

		int golesFavor = rs.getInt("GOLES_FAVOR_ANTERIOR");
		evento.setGolesFavorAnterior(rs.wasNull() ? null : golesFavor);

		int golesContra = rs.getInt("GOLES_CONTRA_ANTERIOR");
		evento.setGolesContraAnterior(rs.wasNull() ? null : golesContra);

		int aviso = rs.getInt("AVISO_ANTERIOR");
		evento.setAvisoAnterior(rs.wasNull() ? null : aviso == 1);

		evento.setEstadisticasGuardadas(rs.getInt("ESTADISTICAS_GUARDADAS") == 1);

		int minuto = rs.getInt("MINUTO");
		evento.setMinuto(rs.wasNull() ? null : minuto);

		return evento;
	}

	private static void ponerLong(PreparedStatement ps, int indice, Long valor) throws SQLException {
		if (valor != null) {
			ps.setLong(indice, valor);
		} else {
			ps.setNull(indice, Types.NUMERIC);
		}
	}

	private static void ponerInteger(PreparedStatement ps, int indice, Integer valor) throws SQLException {
		if (valor != null) {
			ps.setInt(indice, valor);
		} else {
			ps.setNull(indice, Types.NUMERIC);
		}
	}

	private static void ponerString(PreparedStatement ps, int indice, String valor) throws SQLException {
		if (valor != null) {
			ps.setString(indice, valor);
		} else {
			ps.setNull(indice, Types.VARCHAR);
		}
	}

}
