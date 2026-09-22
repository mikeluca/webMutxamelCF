package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;

@Repository
public class ConvocatoriaJugadorDaoImpl
        implements ConvocatoriaJugadorDao {

    private final JdbcTemplate jdbcTemplate;

    public ConvocatoriaJugadorDaoImpl(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ConvocatoriaJugador> rowMapper = new RowMapper<ConvocatoriaJugador>() {

        @Override
        public ConvocatoriaJugador mapRow(
                ResultSet rs,
                int rowNum) throws SQLException {

            ConvocatoriaJugador convocatoriaJugador = new ConvocatoriaJugador();

            convocatoriaJugador.setId(
                    rs.getLong("ID"));

            convocatoriaJugador.setConvocatoriaId(
                    rs.getLong("CONVOCATORIA_ID"));

            convocatoriaJugador.setJugadorId(
                    rs.getLong("JUGADOR_ID"));

            return convocatoriaJugador;
        }
    };

    @Override
    public ConvocatoriaJugador guardar(
            ConvocatoriaJugador convocatoriaJugador) {

        String sql = """
                INSERT INTO CONVOCATORIA_JUGADOR (
                    CONVOCATORIA_ID,
                    JUGADOR_ID
                )
                VALUES (?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    sql,
                    new String[] { "ID" });

            ps.setLong(
                    1,
                    convocatoriaJugador.getConvocatoriaId());
            ps.setLong(
                    2,
                    convocatoriaJugador.getJugadorId());

            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "No se pudo obtener el ID generado de la convocatoria-jugador");
        }

        convocatoriaJugador.setId(key.longValue());

        return convocatoriaJugador;
    }

    @Override
    public List<ConvocatoriaJugador> obtenerPorConvocatoria(
            Long convocatoriaId) {

        String sql = """
                SELECT
                    ID,
                    CONVOCATORIA_ID,
                    JUGADOR_ID
                FROM CONVOCATORIA_JUGADOR
                WHERE CONVOCATORIA_ID = ?
                ORDER BY JUGADOR_ID
                """;

        return jdbcTemplate.query(
                sql,
                rowMapper,
                convocatoriaId);
    }

    @Override
    public ConvocatoriaJugador obtenerPorConvocatoriaYJugador(
            Long convocatoriaId,
            Long jugadorId) {

        String sql = """
                SELECT
                    ID,
                    CONVOCATORIA_ID,
                    JUGADOR_ID
                FROM CONVOCATORIA_JUGADOR
                WHERE CONVOCATORIA_ID = ?
                  AND JUGADOR_ID = ?
                """;

        List<ConvocatoriaJugador> resultado = jdbcTemplate.query(
                sql,
                rowMapper,
                convocatoriaId,
                jugadorId);

        return resultado.isEmpty()
                ? null
                : resultado.get(0);
    }

    @Override
    public void eliminarPorConvocatoria(
            Long convocatoriaId) {

        jdbcTemplate.update(
                """
                        DELETE FROM CONVOCATORIA_JUGADOR
                        WHERE CONVOCATORIA_ID = ?
                        """,
                convocatoriaId);
    }

    @Override
    public boolean existePorJugador(
            Long jugadorId) {

        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM CONVOCATORIA_JUGADOR
                        WHERE JUGADOR_ID = ?
                        """,
                Integer.class,
                jugadorId);

        return count != null && count > 0;
    }
}