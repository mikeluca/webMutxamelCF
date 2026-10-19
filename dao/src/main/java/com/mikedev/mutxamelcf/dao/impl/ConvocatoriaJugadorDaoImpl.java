package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

            convocatoriaJugador.setRespuesta(
                    rs.getString("RESPUESTA"));

            convocatoriaJugador.setMotivo(
                    rs.getString("MOTIVO"));

            Timestamp fechaRespuesta = rs.getTimestamp("FECHA_RESPUESTA");

            if (fechaRespuesta != null) {
                convocatoriaJugador.setFechaRespuesta(
                        fechaRespuesta.toLocalDateTime());
            }

            long usuarioRespuesta = rs.getLong("USUARIO_APP_RESPUESTA_ID");

            if (!rs.wasNull()) {
                convocatoriaJugador.setUsuarioAppRespuestaId(usuarioRespuesta);
            }

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
                    JUGADOR_ID,
                    RESPUESTA,
                    MOTIVO,
                    FECHA_RESPUESTA,
                    USUARIO_APP_RESPUESTA_ID
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
                    JUGADOR_ID,
                    RESPUESTA,
                    MOTIVO,
                    FECHA_RESPUESTA,
                    USUARIO_APP_RESPUESTA_ID
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
    public int guardarRespuesta(
            Long convocatoriaId,
            Long jugadorId,
            String respuesta,
            String motivo,
            LocalDateTime fechaRespuesta,
            Long usuarioAppId) {

        return jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(
                            """
                                    UPDATE CONVOCATORIA_JUGADOR
                                    SET RESPUESTA = ?,
                                        MOTIVO = ?,
                                        FECHA_RESPUESTA = ?,
                                        USUARIO_APP_RESPUESTA_ID = ?
                                    WHERE CONVOCATORIA_ID = ?
                                      AND JUGADOR_ID = ?
                                    """);

                    if (respuesta != null) {
                        ps.setString(1, respuesta);
                    } else {
                        ps.setNull(1, Types.VARCHAR);
                    }

                    if (motivo != null) {
                        ps.setString(2, motivo);
                    } else {
                        ps.setNull(2, Types.VARCHAR);
                    }

                    if (fechaRespuesta != null) {
                        ps.setTimestamp(3, Timestamp.valueOf(fechaRespuesta));
                    } else {
                        ps.setNull(3, Types.TIMESTAMP);
                    }

                    if (usuarioAppId != null) {
                        ps.setLong(4, usuarioAppId);
                    } else {
                        ps.setNull(4, Types.NUMERIC);
                    }

                    ps.setLong(5, convocatoriaId);
                    ps.setLong(6, jugadorId);

                    return ps;
                });
    }

    @Override
    public List<ConvocatoriaJugador> obtenerPorJugadorYRango(
            Long jugadorId,
            LocalDate desde,
            LocalDate hasta) {

        String sql = """
                SELECT
                    CJ.ID,
                    CJ.CONVOCATORIA_ID,
                    CJ.JUGADOR_ID,
                    CJ.RESPUESTA,
                    CJ.MOTIVO,
                    CJ.FECHA_RESPUESTA,
                    CJ.USUARIO_APP_RESPUESTA_ID
                FROM CONVOCATORIA_JUGADOR CJ
                JOIN CONVOCATORIAS C ON C.ID = CJ.CONVOCATORIA_ID
                JOIN PARTIDOS P ON P.ID = C.PARTIDO_ID
                WHERE CJ.JUGADOR_ID = ?
                  AND P.DIA BETWEEN ? AND ?
                ORDER BY P.DIA, CJ.ID
                """;

        return jdbcTemplate.query(
                sql,
                rowMapper,
                jugadorId,
                java.sql.Date.valueOf(desde),
                java.sql.Date.valueOf(hasta));
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