package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.sql.Statement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.model.EntrenamientoAsistencia;

@Repository
public class EntrenamientoAsistenciaDaoImpl
        implements EntrenamientoAsistenciaDao {

    private final JdbcTemplate jdbcTemplate;

    public EntrenamientoAsistenciaDaoImpl(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<EntrenamientoAsistencia> rowMapper = new RowMapper<EntrenamientoAsistencia>() {

        @Override
        public EntrenamientoAsistencia mapRow(
                ResultSet rs,
                int rowNum) throws SQLException {

            EntrenamientoAsistencia asistencia = new EntrenamientoAsistencia();

            asistencia.setId(
                    rs.getLong("ID"));

            asistencia.setEntrenamientoId(
                    rs.getLong("ENTRENAMIENTO_ID"));

            asistencia.setJugadorId(
                    rs.getLong("JUGADOR_ID"));

            asistencia.setEstado(
                    rs.getString("ESTADO"));

            return asistencia;
        }
    };

    @Override
    public EntrenamientoAsistencia guardar(
            EntrenamientoAsistencia asistencia) {

        String sql = """
                INSERT INTO ENTRENAMIENTO_ASISTENCIA (
                    ENTRENAMIENTO_ID,
                    JUGADOR_ID,
                    ESTADO
                )
                VALUES (?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    sql,
                    new String[] { "ID" });

            ps.setLong(1, asistencia.getEntrenamientoId());
            ps.setLong(2, asistencia.getJugadorId());
            ps.setString(3, asistencia.getEstado());

            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException(
                    "No se pudo obtener el ID generado de la asistencia");
        }

        asistencia.setId(key.longValue());

        return asistencia;
    }

    @Override
    public List<EntrenamientoAsistencia> obtenerPorEntrenamiento(
            Long entrenamientoId) {

        String sql = """
                SELECT
                    ID,
                    ENTRENAMIENTO_ID,
                    JUGADOR_ID,
                    ESTADO
                FROM ENTRENAMIENTO_ASISTENCIA
                WHERE ENTRENAMIENTO_ID = ?
                ORDER BY JUGADOR_ID
                """;

        return jdbcTemplate.query(
                sql,
                rowMapper,
                entrenamientoId);
    }

    @Override
    public EntrenamientoAsistencia obtenerPorEntrenamientoYJugador(
            Long entrenamientoId,
            Long jugadorId) {

        String sql = """
                SELECT
                    ID,
                    ENTRENAMIENTO_ID,
                    JUGADOR_ID,
                    ESTADO
                FROM ENTRENAMIENTO_ASISTENCIA
                WHERE ENTRENAMIENTO_ID = ?
                  AND JUGADOR_ID = ?
                """;

        List<EntrenamientoAsistencia> resultado = jdbcTemplate.query(
                sql,
                rowMapper,
                entrenamientoId,
                jugadorId);

        return resultado.isEmpty()
                ? null
                : resultado.get(0);
    }

    @Override
    public void eliminarPorEntrenamiento(
            Long entrenamientoId) {

        jdbcTemplate.update(
                """
                        DELETE FROM ENTRENAMIENTO_ASISTENCIA
                        WHERE ENTRENAMIENTO_ID = ?
                        """,
                entrenamientoId);
    }
}