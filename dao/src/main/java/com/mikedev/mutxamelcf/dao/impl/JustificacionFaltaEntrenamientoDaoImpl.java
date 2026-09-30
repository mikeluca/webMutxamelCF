package com.mikedev.mutxamelcf.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.JustificacionFaltaEntrenamientoDao;
import com.mikedev.mutxamelcf.model.JustificacionFaltaEntrenamiento;

@Repository
public class JustificacionFaltaEntrenamientoDaoImpl implements JustificacionFaltaEntrenamientoDao {

    // Limite de elementos por clausula IN en Oracle (1000); se deja
    // margen igual que en el resto del proyecto (p.ej. CuotaJugadorDaoImpl).
    private static final int TAMANO_LOTE_IN = 900;

    private static final RowMapper<JustificacionFaltaEntrenamiento> ROW_MAPPER =
            JustificacionFaltaEntrenamientoDaoImpl::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JustificacionFaltaEntrenamientoDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public JustificacionFaltaEntrenamiento crear(JustificacionFaltaEntrenamiento justificacion) {

        String sql = """
                INSERT INTO JUSTIFICACIONES_FALTA_ENTRENAMIENTO (
                    SESION_ID,
                    JUGADOR_ID,
                    USUARIO_APP_ID,
                    MOTIVO
                )
                VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });

            ps.setLong(1, justificacion.getSesionId());
            ps.setLong(2, justificacion.getJugadorId());
            ps.setLong(3, justificacion.getUsuarioAppId());
            ps.setString(4, justificacion.getMotivo());

            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID generado de la justificación de falta");
        }

        justificacion.setId(key.longValue());

        return justificacion;
    }

    @Override
    public void actualizar(JustificacionFaltaEntrenamiento justificacion) {

        String sql = """
                UPDATE JUSTIFICACIONES_FALTA_ENTRENAMIENTO
                SET MOTIVO = ?,
                    USUARIO_APP_ID = ?,
                    FECHA_CREACION = SYSTIMESTAMP
                WHERE ID = ?
                """;

        jdbcTemplate.update(sql, justificacion.getMotivo(), justificacion.getUsuarioAppId(), justificacion.getId());
    }

    @Override
    public JustificacionFaltaEntrenamiento obtenerPorSesionYJugador(Long sesionId, Long jugadorId) {

        String sql = """
                SELECT ID, SESION_ID, JUGADOR_ID, USUARIO_APP_ID, MOTIVO, FECHA_CREACION
                FROM JUSTIFICACIONES_FALTA_ENTRENAMIENTO
                WHERE SESION_ID = ?
                  AND JUGADOR_ID = ?
                """;

        List<JustificacionFaltaEntrenamiento> resultado = jdbcTemplate.query(sql, ROW_MAPPER, sesionId, jugadorId);

        return resultado.isEmpty() ? null : resultado.get(0);
    }

    @Override
    public List<JustificacionFaltaEntrenamiento> obtenerPorSesion(Long sesionId) {

        String sql = """
                SELECT ID, SESION_ID, JUGADOR_ID, USUARIO_APP_ID, MOTIVO, FECHA_CREACION
                FROM JUSTIFICACIONES_FALTA_ENTRENAMIENTO
                WHERE SESION_ID = ?
                ORDER BY FECHA_CREACION ASC
                """;

        return jdbcTemplate.query(sql, ROW_MAPPER, sesionId);
    }

    @Override
    public void eliminarPorSesionIds(List<Long> sesionIds) {

        if (sesionIds == null || sesionIds.isEmpty()) {
            return;
        }

        List<Long> idsValidos = sesionIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (idsValidos.isEmpty()) {
            return;
        }

        for (int inicio = 0; inicio < idsValidos.size(); inicio += TAMANO_LOTE_IN) {

            List<Long> lote = idsValidos.subList(
                    inicio, Math.min(inicio + TAMANO_LOTE_IN, idsValidos.size()));

            String placeholders = String.join(",", Collections.nCopies(lote.size(), "?"));

            jdbcTemplate.update(
                    "DELETE FROM JUSTIFICACIONES_FALTA_ENTRENAMIENTO WHERE SESION_ID IN (" + placeholders + ")",
                    lote.toArray());
        }
    }

    private static JustificacionFaltaEntrenamiento mapRow(ResultSet rs, int rowNum) throws SQLException {

        JustificacionFaltaEntrenamiento justificacion = new JustificacionFaltaEntrenamiento();

        justificacion.setId(rs.getLong("ID"));
        justificacion.setSesionId(rs.getLong("SESION_ID"));
        justificacion.setJugadorId(rs.getLong("JUGADOR_ID"));
        justificacion.setUsuarioAppId(rs.getLong("USUARIO_APP_ID"));
        justificacion.setMotivo(rs.getString("MOTIVO"));

        Timestamp fechaCreacion = rs.getTimestamp("FECHA_CREACION");
        justificacion.setFechaCreacion(fechaCreacion != null ? fechaCreacion.toLocalDateTime() : null);

        return justificacion;
    }

}
