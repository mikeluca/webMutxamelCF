package com.mikedev.mutxamelcf.dao.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.PartidoAlineacionDao;
import com.mikedev.mutxamelcf.model.AlineacionJugador;
import com.mikedev.mutxamelcf.model.JugadorBasico;
import com.mikedev.mutxamelcf.model.JugadorFicha;

@Repository
public class PartidoAlineacionDaoImpl implements PartidoAlineacionDao {

	private final JdbcTemplate jdbcTemplate;

	public PartidoAlineacionDaoImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<AlineacionJugador> obtener(Long partidoId) {

		return jdbcTemplate.query(
				"SELECT JUGADOR_ID, TITULAR, ORDEN FROM PARTIDO_ALINEACION WHERE PARTIDO_ID = ?"
						+ " ORDER BY TITULAR DESC, ORDEN, ID",
				(rs, n) -> new AlineacionJugador(rs.getLong("JUGADOR_ID"), rs.getInt("TITULAR") == 1,
						rs.getInt("ORDEN")),
				partidoId);
	}

	@Override
	public String obtenerFormacion(Long partidoId) {

		List<String> formaciones = jdbcTemplate.query("SELECT FORMACION FROM PARTIDOS WHERE ID = ?",
				(rs, n) -> rs.getString("FORMACION"), partidoId);

		return formaciones.isEmpty() ? null : formaciones.get(0);
	}

	@Override
	public void reemplazar(Long partidoId, String formacion, List<Long> titulares, List<Long> suplentes) {

		jdbcTemplate.update("DELETE FROM PARTIDO_ALINEACION WHERE PARTIDO_ID = ?", partidoId);

		List<Object[]> filas = new ArrayList<>();

		for (int i = 0; i < titulares.size(); i++) {
			filas.add(new Object[] { partidoId, titulares.get(i), 1, i + 1 });
		}

		for (int i = 0; i < suplentes.size(); i++) {
			filas.add(new Object[] { partidoId, suplentes.get(i), 0, i + 1 });
		}

		jdbcTemplate.batchUpdate(
				"INSERT INTO PARTIDO_ALINEACION (PARTIDO_ID, JUGADOR_ID, TITULAR, ORDEN) VALUES (?, ?, ?, ?)", filas);

		jdbcTemplate.update("UPDATE PARTIDOS SET FORMACION = ? WHERE ID = ?", formacion, partidoId);
	}

	@Override
	public List<JugadorFicha> obtenerJugadoresFicha(Collection<Long> jugadorIds, int anioMayorEdad,
			Long primerEquipoId) {

		if (jugadorIds == null || jugadorIds.isEmpty()) {
			return List.of();
		}

		String marcadores = jugadorIds.stream().map(id -> "?").collect(Collectors.joining(", "));

		List<Object> parametros = new ArrayList<>();
		parametros.add(anioMayorEdad);
		parametros.add(primerEquipoId);
		parametros.addAll(jugadorIds);

		// La foto solo se lee de quien puede salir con ella: a un menor no se le trae el BLOB de la base de datos.
		return jdbcTemplate.query("""
				SELECT ID, DORSAL, NOMBRE, APELLIDOS, NOMBRE_RETRANSMISION,
				       CASE WHEN (ANIO_NACIMIENTO IS NOT NULL AND ANIO_NACIMIENTO <= ?)
				              OR (ANIO_NACIMIENTO IS NULL AND EQUIPO_ID = ?)
				            THEN FOTO END AS FOTO_PUBLICA
				FROM JUGADORES
				WHERE ID IN (""" + marcadores + ")", (rs, n) -> {

			int dorsal = rs.getInt("DORSAL");
			Integer numero = rs.wasNull() ? null : dorsal;

			return new JugadorFicha(
					new JugadorBasico(rs.getLong("ID"), numero, rs.getString("NOMBRE"), rs.getString("APELLIDOS"),
							rs.getString("NOMBRE_RETRANSMISION")),
					rs.getBytes("FOTO_PUBLICA"));
		}, parametros.toArray());
	}
}
