package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugador;

@Repository
public class PartidoEstadisticaJugadorDaoImpl
        implements PartidoEstadisticaJugadorDao {

    private final JdbcTemplate jdbcTemplate;

    public PartidoEstadisticaJugadorDaoImpl(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PartidoEstadisticaJugador> rowMapper = new RowMapper<PartidoEstadisticaJugador>() {

        @Override
        public PartidoEstadisticaJugador mapRow(
                ResultSet rs,
                int rowNum) throws SQLException {

            PartidoEstadisticaJugador estadistica = new PartidoEstadisticaJugador();

            estadistica.setId(
                    rs.getLong("ID"));

            estadistica.setPartidoId(
                    rs.getLong("PARTIDO_ID"));

            estadistica.setJugadorId(
                    rs.getLong("JUGADOR_ID"));

            estadistica.setGoles(
                    rs.getInt("GOLES"));

            estadistica.setAsistencias(
                    rs.getInt("ASISTENCIAS"));

            estadistica.setTarjetasAmarillas(
                    rs.getInt("TARJETAS_AMARILLAS"));

            estadistica.setTarjetaRoja(
                    rs.getInt("TARJETA_ROJA") == 1);

            return estadistica;
        }
    };

    @Override
    public PartidoEstadisticaJugador guardar(
            PartidoEstadisticaJugador estadistica) {

        String sql = """
                INSERT INTO PARTIDO_ESTADISTICAS_JUGADOR (
                    PARTIDO_ID,
                    JUGADOR_ID,
                    GOLES,
                    ASISTENCIAS,
                    TARJETAS_AMARILLAS,
                    TARJETA_ROJA
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    sql,
                    new String[] { "ID" });

            ps.setLong(1, estadistica.getPartidoId());
            ps.setLong(2, estadistica.getJugadorId());
            ps.setInt(3, estadistica.getGoles() != null ? estadistica.getGoles() : 0);
            ps.setInt(4, estadistica.getAsistencias() != null ? estadistica.getAsistencias() : 0);
            ps.setInt(5, estadistica.getTarjetasAmarillas() != null ? estadistica.getTarjetasAmarillas() : 0);
            ps.setInt(6, estadistica.isTarjetaRoja() ? 1 : 0);

            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "No se pudo obtener el ID generado de la estadística");
        }

        estadistica.setId(key.longValue());

        return estadistica;
    }

    @Override
    public List<PartidoEstadisticaJugador> obtenerPorPartido(
            Long partidoId) {

        String sql = """
                SELECT
                    ID,
                    PARTIDO_ID,
                    JUGADOR_ID,
                    GOLES,
                    ASISTENCIAS,
                    TARJETAS_AMARILLAS,
                    TARJETA_ROJA
                FROM PARTIDO_ESTADISTICAS_JUGADOR
                WHERE PARTIDO_ID = ?
                ORDER BY JUGADOR_ID
                """;

        return jdbcTemplate.query(
                sql,
                rowMapper,
                partidoId);
    }

    @Override
    public void eliminarPorPartido(
            Long partidoId) {

        jdbcTemplate.update(
                """
                        DELETE FROM PARTIDO_ESTADISTICAS_JUGADOR
                        WHERE PARTIDO_ID = ?
                        """,
                partidoId);
    }
}
