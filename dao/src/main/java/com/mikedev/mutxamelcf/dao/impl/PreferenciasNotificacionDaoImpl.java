package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.PreferenciasNotificacionDao;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacion;

@Repository
public class PreferenciasNotificacionDaoImpl
        implements PreferenciasNotificacionDao {

    private final JdbcTemplate jdbcTemplate;

    public PreferenciasNotificacionDaoImpl(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<PreferenciasNotificacion> rowMapper = new RowMapper<PreferenciasNotificacion>() {

        @Override
        public PreferenciasNotificacion mapRow(
                ResultSet rs,
                int rowNum) throws SQLException {

            PreferenciasNotificacion preferencias = new PreferenciasNotificacion();

            preferencias.setId(
                    rs.getLong("ID"));

            preferencias.setUsuarioAppId(
                    rs.getLong("USUARIO_APP_ID"));

            preferencias.setNotificacionesActivadas(
                    rs.getInt("NOTIFICACIONES_ACTIVADAS"));

            preferencias.setNoticiasActivadas(
                    rs.getInt("NOTICIAS_ACTIVADAS"));

            preferencias.setComunicacionesActivadas(
                    rs.getInt("COMUNICACIONES_ACTIVADAS"));

            preferencias.setMensajesActivados(
                    rs.getInt("MENSAJES_ACTIVADOS"));

            preferencias.setResultadosActivados(
                    rs.getInt("RESULTADOS_ACTIVADOS"));

            return preferencias;
        }
    };

    @Override
    public PreferenciasNotificacion obtenerPorUsuario(
            Long usuarioId) {

        List<PreferenciasNotificacion> resultado = jdbcTemplate.query(
                """
                        SELECT
                            ID,
                            USUARIO_APP_ID,
                            NOTIFICACIONES_ACTIVADAS,
                            NOTICIAS_ACTIVADAS,
                            COMUNICACIONES_ACTIVADAS,
                            MENSAJES_ACTIVADOS,
                            RESULTADOS_ACTIVADOS
                        FROM PREFERENCIAS_NOTIFICACION
                        WHERE USUARIO_APP_ID = ?
                        """,
                rowMapper,
                usuarioId);

        return resultado.isEmpty()
                ? null
                : resultado.get(0);
    }

    @Override
    public void guardar(
            PreferenciasNotificacion preferencias) {

        Long id = jdbcTemplate.queryForObject(
                "SELECT SEQ_PREFERENCIAS_NOTIF.NEXTVAL FROM DUAL",
                Long.class);

        jdbcTemplate.update(
                """
                        INSERT INTO PREFERENCIAS_NOTIFICACION
                        (
                            ID,
                            USUARIO_APP_ID,
                            NOTIFICACIONES_ACTIVADAS,
                            NOTICIAS_ACTIVADAS,
                            COMUNICACIONES_ACTIVADAS,
                            MENSAJES_ACTIVADOS,
                            RESULTADOS_ACTIVADOS
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                preferencias.getUsuarioAppId(),
                preferencias.getNotificacionesActivadas(),
                preferencias.getNoticiasActivadas(),
                preferencias.getComunicacionesActivadas(),
                preferencias.getMensajesActivados(),
                preferencias.getResultadosActivados());

        preferencias.setId(id);
    }

    @Override
    public void actualizar(
            PreferenciasNotificacion preferencias) {

        jdbcTemplate.update(
                """
                        UPDATE PREFERENCIAS_NOTIFICACION
                        SET
                            NOTIFICACIONES_ACTIVADAS = ?,
                            NOTICIAS_ACTIVADAS = ?,
                            COMUNICACIONES_ACTIVADAS = ?,
                            MENSAJES_ACTIVADOS = ?,
                            RESULTADOS_ACTIVADOS = ?
                        WHERE USUARIO_APP_ID = ?
                        """,
                preferencias.getNotificacionesActivadas(),
                preferencias.getNoticiasActivadas(),
                preferencias.getComunicacionesActivadas(),
                preferencias.getMensajesActivados(),
                preferencias.getResultadosActivados(),
                preferencias.getUsuarioAppId());
    }
}