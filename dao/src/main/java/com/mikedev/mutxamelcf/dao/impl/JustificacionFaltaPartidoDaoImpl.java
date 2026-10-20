package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;

@Repository
public class JustificacionFaltaPartidoDaoImpl implements JustificacionFaltaPartidoDao {

    private static final String COLUMNAS = "ID, PARTIDO_ID, JUGADOR_ID, USUARIO_APP_ID, MOTIVO, FECHA_CREACION";

    private static final RowMapper<JustificacionFaltaPartido> ROW_MAPPER = (rs, n) -> {

        JustificacionFaltaPartido justificacion = new JustificacionFaltaPartido();

        justificacion.setId(rs.getLong("ID"));
        justificacion.setPartidoId(rs.getLong("PARTIDO_ID"));
        justificacion.setJugadorId(rs.getLong("JUGADOR_ID"));
        justificacion.setUsuarioAppId(rs.getLong("USUARIO_APP_ID"));
        justificacion.setMotivo(rs.getString("MOTIVO"));

        Timestamp fecha = rs.getTimestamp("FECHA_CREACION");

        if (fecha != null) {
            justificacion.setFechaCreacion(fecha.toLocalDateTime());
        }

        return justificacion;
    };

    private final JdbcTemplate jdbcTemplate;

    public JustificacionFaltaPartidoDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public JustificacionFaltaPartido crear(JustificacionFaltaPartido justificacion) {

        String sql = """
                INSERT INTO JUSTIFICACIONES_FALTA_PARTIDO (
                    PARTIDO_ID,
                    JUGADOR_ID,
                    USUARIO_APP_ID,
                    MOTIVO,
                    FECHA_CREACION
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });

            ps.setLong(1, justificacion.getPartidoId());
            ps.setLong(2, justificacion.getJugadorId());
            ps.setLong(3, justificacion.getUsuarioAppId());
            ps.setString(4, justificacion.getMotivo());
            ps.setTimestamp(5, Timestamp.valueOf(justificacion.getFechaCreacion()));

            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID generado de la justificación de ausencia");
        }

        justificacion.setId(key.longValue());

        return justificacion;
    }

    @Override
    public void actualizar(JustificacionFaltaPartido justificacion) {

        jdbcTemplate.update("""
                UPDATE JUSTIFICACIONES_FALTA_PARTIDO
                SET MOTIVO = ?,
                    USUARIO_APP_ID = ?,
                    FECHA_CREACION = ?
                WHERE ID = ?
                """,
                justificacion.getMotivo(),
                justificacion.getUsuarioAppId(),
                Timestamp.valueOf(justificacion.getFechaCreacion()),
                justificacion.getId());
    }

    @Override
    public JustificacionFaltaPartido obtenerPorPartidoYJugador(Long partidoId, Long jugadorId) {

        List<JustificacionFaltaPartido> resultado = jdbcTemplate.query(
                "SELECT " + COLUMNAS + " FROM JUSTIFICACIONES_FALTA_PARTIDO WHERE PARTIDO_ID = ? AND JUGADOR_ID = ?",
                ROW_MAPPER, partidoId, jugadorId);

        return resultado.isEmpty() ? null : resultado.get(0);
    }

    @Override
    public List<JustificacionFaltaPartido> obtenerPorPartido(Long partidoId) {

        return jdbcTemplate.query(
                "SELECT " + COLUMNAS
                        + " FROM JUSTIFICACIONES_FALTA_PARTIDO WHERE PARTIDO_ID = ? ORDER BY FECHA_CREACION, ID",
                ROW_MAPPER, partidoId);
    }

    @Override
    public List<JustificacionFaltaPartido> obtenerPorJugadorYRango(
            Long jugadorId, java.time.LocalDate desde, java.time.LocalDate hasta) {

        return jdbcTemplate.query(
                "SELECT J.ID, J.PARTIDO_ID, J.JUGADOR_ID, J.USUARIO_APP_ID, J.MOTIVO, J.FECHA_CREACION"
                        + " FROM JUSTIFICACIONES_FALTA_PARTIDO J JOIN PARTIDOS P ON P.ID = J.PARTIDO_ID"
                        + " WHERE J.JUGADOR_ID = ? AND P.DIA BETWEEN ? AND ? ORDER BY P.DIA, J.ID",
                ROW_MAPPER, jugadorId, java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta));
    }

    @Override
    public int eliminar(Long partidoId, Long jugadorId) {

        return jdbcTemplate.update(
                "DELETE FROM JUSTIFICACIONES_FALTA_PARTIDO WHERE PARTIDO_ID = ? AND JUGADOR_ID = ?",
                partidoId, jugadorId);
    }
}
