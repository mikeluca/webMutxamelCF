package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.model.DispositivoApp;

@Repository
public class DispositivoAppDaoImpl implements DispositivoAppDao {

        private final JdbcTemplate jdbcTemplate;

        public DispositivoAppDaoImpl(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public void registrar(DispositivoApp dispositivo) {

                Long id = jdbcTemplate.queryForObject(
                                "SELECT SEQ_USUARIOS_APP_DISP.NEXTVAL FROM DUAL",
                                Long.class);

                LocalDateTime ahora = LocalDateTime.now();

                jdbcTemplate.update(
                                """
                                                INSERT INTO USUARIOS_APP_DISPOSITIVOS
                                                (
                                                    ID,
                                                    USUARIO_APP_ID,
                                                    TOKEN_FCM,
                                                    PLATAFORMA,
                                                    ACTIVO,
                                                    FECHA_REGISTRO,
                                                    FECHA_ULTIMO_ACCESO
                                                )
                                                VALUES (?, ?, ?, ?, ?, ?, ?)
                                                """,
                                id,
                                dispositivo.getUsuarioAppId(),
                                dispositivo.getTokenFcm(),
                                dispositivo.getPlataforma(),
                                1,
                                Timestamp.valueOf(
                                                dispositivo.getFechaRegistro() != null
                                                                ? dispositivo.getFechaRegistro()
                                                                : ahora),
                                Timestamp.valueOf(
                                                dispositivo.getFechaUltimoAcceso() != null
                                                                ? dispositivo.getFechaUltimoAcceso()
                                                                : ahora));
        }

        @Override
        public void actualizarAcceso(
                        Long usuarioAppId,
                        String tokenFcm) {

                jdbcTemplate.update(
                                """
                                                UPDATE USUARIOS_APP_DISPOSITIVOS
                                                SET ACTIVO = 1,
                                                    FECHA_ULTIMO_ACCESO = ?
                                                WHERE USUARIO_APP_ID = ?
                                                  AND TOKEN_FCM = ?
                                                """,
                                Timestamp.valueOf(LocalDateTime.now()),
                                usuarioAppId,
                                tokenFcm);
        }

        @Override
        public void desactivar(
                        Long usuarioAppId,
                        String tokenFcm) {

                jdbcTemplate.update(
                                """
                                                UPDATE USUARIOS_APP_DISPOSITIVOS
                                                SET ACTIVO = 0
                                                WHERE USUARIO_APP_ID = ?
                                                  AND TOKEN_FCM = ?
                                                """,
                                usuarioAppId,
                                tokenFcm);
        }

        @Override
        public DispositivoApp obtenerPorUsuarioYToken(
                        Long usuarioAppId,
                        String tokenFcm) {

                List<DispositivoApp> dispositivos = jdbcTemplate.query(
                                """
                                                SELECT
                                                    ID,
                                                    USUARIO_APP_ID,
                                                    TOKEN_FCM,
                                                    PLATAFORMA,
                                                    ACTIVO,
                                                    FECHA_REGISTRO,
                                                    FECHA_ULTIMO_ACCESO
                                                FROM USUARIOS_APP_DISPOSITIVOS
                                                WHERE USUARIO_APP_ID = ?
                                                  AND TOKEN_FCM = ?
                                                """,
                                (rs, rowNum) -> mapearDispositivo(rs),
                                usuarioAppId,
                                tokenFcm);

                return dispositivos.isEmpty()
                                ? null
                                : dispositivos.get(0);
        }

        @Override
        public List<DispositivoApp> obtenerActivosPorUsuario(
                        Long usuarioAppId) {

                return jdbcTemplate.query(
                                """
                                                SELECT
                                                    ID,
                                                    USUARIO_APP_ID,
                                                    TOKEN_FCM,
                                                    PLATAFORMA,
                                                    ACTIVO,
                                                    FECHA_REGISTRO,
                                                    FECHA_ULTIMO_ACCESO
                                                FROM USUARIOS_APP_DISPOSITIVOS
                                                WHERE USUARIO_APP_ID = ?
                                                  AND ACTIVO = 1
                                                ORDER BY FECHA_ULTIMO_ACCESO DESC
                                                """,
                                (rs, rowNum) -> mapearDispositivo(rs),
                                usuarioAppId);
        }

        @Override
        public void desactivarTokenDeOtrosUsuarios(
                        Long usuarioAppId,
                        String tokenFcm) {

                jdbcTemplate.update(
                                """
                                                UPDATE USUARIOS_APP_DISPOSITIVOS
                                                SET ACTIVO = 0
                                                WHERE TOKEN_FCM = ?
                                                  AND USUARIO_APP_ID <> ?
                                                """,
                                tokenFcm,
                                usuarioAppId);
        }

        private DispositivoApp mapearDispositivo(
                        java.sql.ResultSet rs) throws java.sql.SQLException {

                DispositivoApp dispositivo = new DispositivoApp();

                dispositivo.setId(
                                rs.getLong("ID"));

                dispositivo.setUsuarioAppId(
                                rs.getLong("USUARIO_APP_ID"));

                dispositivo.setTokenFcm(
                                rs.getString("TOKEN_FCM"));

                dispositivo.setPlataforma(
                                rs.getString("PLATAFORMA"));

                dispositivo.setActivo(
                                rs.getInt("ACTIVO"));

                Timestamp fechaRegistro = rs.getTimestamp("FECHA_REGISTRO");

                if (fechaRegistro != null) {
                        dispositivo.setFechaRegistro(
                                        fechaRegistro.toLocalDateTime());
                }

                Timestamp fechaUltimoAcceso = rs.getTimestamp("FECHA_ULTIMO_ACCESO");

                if (fechaUltimoAcceso != null) {
                        dispositivo.setFechaUltimoAcceso(
                                        fechaUltimoAcceso.toLocalDateTime());
                }

                return dispositivo;
        }
}