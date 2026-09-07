package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.NotificacionAppDao;
import com.mikedev.mutxamelcf.model.NotificacionApp;

@Repository
public class NotificacionAppDaoImpl implements NotificacionAppDao {

    private final JdbcTemplate jdbcTemplate;

    public NotificacionAppDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<NotificacionApp> rowMapper = new RowMapper<NotificacionApp>() {

        @Override
        public NotificacionApp mapRow(ResultSet rs, int rowNum)
                throws SQLException {

            NotificacionApp notificacion = new NotificacionApp();

            notificacion.setId(rs.getLong("ID"));
            notificacion.setUsuarioAppId(
                    rs.getLong("USUARIO_APP_ID"));
            notificacion.setTipo(
                    rs.getString("TIPO"));
            notificacion.setTitulo(
                    rs.getString("TITULO"));
            notificacion.setMensaje(
                    rs.getString("MENSAJE"));

            long referenciaId = rs.getLong("REFERENCIA_ID");

            if (rs.wasNull()) {
                notificacion.setReferenciaId(null);
            } else {
                notificacion.setReferenciaId(referenciaId);
            }

            Timestamp fecha = rs.getTimestamp("FECHA");

            if (fecha != null) {
                notificacion.setFecha(fecha.toLocalDateTime());
            }

            notificacion.setLeida(
                    rs.getInt("LEIDA"));

            return notificacion;
        }
    };

    @Override
    public Long guardar(NotificacionApp notificacion) {

        Long id = jdbcTemplate.queryForObject(
                "SELECT SEQ_NOTIFICACIONES_APP.NEXTVAL FROM DUAL",
                Long.class);

        jdbcTemplate.update(
                """
                        INSERT INTO NOTIFICACIONES_APP
                        (
                            ID,
                            USUARIO_APP_ID,
                            TIPO,
                            TITULO,
                            MENSAJE,
                            REFERENCIA_ID,
                            FECHA,
                            LEIDA
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                notificacion.getUsuarioAppId(),
                notificacion.getTipo(),
                notificacion.getTitulo(),
                notificacion.getMensaje(),
                notificacion.getReferenciaId(),
                notificacion.getFecha() != null
                        ? Timestamp.valueOf(notificacion.getFecha())
                        : null,
                notificacion.getLeida());

        notificacion.setId(id);

        return id;
    }

    @Override
    public NotificacionApp obtenerPorId(Long id) {

        List<NotificacionApp> resultado = jdbcTemplate.query(
                """
                        SELECT
                            ID,
                            USUARIO_APP_ID,
                            TIPO,
                            TITULO,
                            MENSAJE,
                            REFERENCIA_ID,
                            FECHA,
                            LEIDA
                        FROM NOTIFICACIONES_APP
                        WHERE ID = ?
                        """,
                rowMapper,
                id);

        return resultado.isEmpty()
                ? null
                : resultado.get(0);
    }

    @Override
    public List<NotificacionApp> obtenerPorUsuario(Long usuarioId) {

        return jdbcTemplate.query(
                """
                        SELECT
                            ID,
                            USUARIO_APP_ID,
                            TIPO,
                            TITULO,
                            MENSAJE,
                            REFERENCIA_ID,
                            FECHA,
                            LEIDA
                        FROM NOTIFICACIONES_APP
                        WHERE USUARIO_APP_ID = ?
                        ORDER BY FECHA DESC, ID DESC
                        """,
                rowMapper,
                usuarioId);
    }

    @Override
    public List<NotificacionApp> obtenerNoLeidas(Long usuarioId) {

        return jdbcTemplate.query(
                """
                        SELECT
                            ID,
                            USUARIO_APP_ID,
                            TIPO,
                            TITULO,
                            MENSAJE,
                            REFERENCIA_ID,
                            FECHA,
                            LEIDA
                        FROM NOTIFICACIONES_APP
                        WHERE USUARIO_APP_ID = ?
                          AND LEIDA = 0
                        ORDER BY FECHA DESC, ID DESC
                        """,
                rowMapper,
                usuarioId);
    }

    @Override
    public void marcarComoLeida(Long id, Long usuarioId) {

        jdbcTemplate.update(
                """
                        UPDATE NOTIFICACIONES_APP
                        SET LEIDA = 1
                        WHERE ID = ?
                          AND USUARIO_APP_ID = ?
                        """,
                id,
                usuarioId);
    }

    @Override
    public void marcarTodasComoLeidas(Long usuarioId) {

        jdbcTemplate.update(
                """
                        UPDATE NOTIFICACIONES_APP
                        SET LEIDA = 1
                        WHERE USUARIO_APP_ID = ?
                          AND LEIDA = 0
                        """,
                usuarioId);
    }

    @Override
    public int contarNoLeidas(Long usuarioId) {

        Integer resultado = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM NOTIFICACIONES_APP
                        WHERE USUARIO_APP_ID = ?
                          AND LEIDA = 0
                        """,
                Integer.class,
                usuarioId);

        return resultado != null ? resultado : 0;
    }
}