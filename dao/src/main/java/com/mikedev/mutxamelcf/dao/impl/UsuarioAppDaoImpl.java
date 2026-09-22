package com.mikedev.mutxamelcf.dao.impl;

import java.sql.Timestamp;
import java.util.List;

import com.mikedev.mutxamelcf.model.UsuarioApp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;

@Repository
public class UsuarioAppDaoImpl implements UsuarioAppDao {

    private final JdbcTemplate jdbcTemplate;

    public UsuarioAppDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UsuarioApp obtenerPorEmail(String email) {

        String sql = """
                SELECT ID,
                       EMAIL,
                       PASSWORD_HASH,
                       ACTIVO,
                       FECHA_ALTA,
                       FECHA_ACTIVACION,
                       FECHA_ULTIMO_ACCESO,
                       TOKEN_ACTIVACION,
                       TOKEN_ACTIVACION_EXPIRA,
                       INTENTOS_ACTIVACION
                FROM USUARIOS_APP
                WHERE LOWER(EMAIL) = LOWER(?)
                """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setString(1, email),
                rs -> {

                    if (rs.next()) {
                        return mapearUsuario(rs);
                    }

                    return null;
                }
        );
    }

    @Override
    public UsuarioApp obtenerPorId(int id) {

        String sql = """
                SELECT ID,
                       EMAIL,
                       PASSWORD_HASH,
                       ACTIVO,
                       FECHA_ALTA,
                       FECHA_ACTIVACION,
                       FECHA_ULTIMO_ACCESO,
                       TOKEN_ACTIVACION,
                       TOKEN_ACTIVACION_EXPIRA,
                       INTENTOS_ACTIVACION
                FROM USUARIOS_APP
                WHERE ID = ?
                """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setInt(1, id),
                rs -> {

                    if (rs.next()) {
                        return mapearUsuario(rs);
                    }

                    return null;
                }
        );
    }

    @Override
    public UsuarioApp obtenerPorTokenActivacion(
            String tokenActivacion) {

        String sql = """
                SELECT ID,
                       EMAIL,
                       PASSWORD_HASH,
                       ACTIVO,
                       FECHA_ALTA,
                       FECHA_ACTIVACION,
                       FECHA_ULTIMO_ACCESO,
                       TOKEN_ACTIVACION,
                       TOKEN_ACTIVACION_EXPIRA,
                       INTENTOS_ACTIVACION
                FROM USUARIOS_APP
                WHERE TOKEN_ACTIVACION = ?
                """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setString(1, tokenActivacion),
                rs -> {

                    if (rs.next()) {
                        return mapearUsuario(rs);
                    }

                    return null;
                }
        );
    }

    @Override
    public List<UsuarioApp> listarTodos() {

        String sql = """
                SELECT ID,
                       EMAIL,
                       PASSWORD_HASH,
                       ACTIVO,
                       FECHA_ALTA,
                       FECHA_ACTIVACION,
                       FECHA_ULTIMO_ACCESO,
                       TOKEN_ACTIVACION,
                       TOKEN_ACTIVACION_EXPIRA,
                       INTENTOS_ACTIVACION
                FROM USUARIOS_APP
                ORDER BY FECHA_ALTA DESC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapearUsuario(rs));
    }

    @Override
    public int guardar(UsuarioApp usuario) {

        String sql = """
                INSERT INTO USUARIOS_APP (
                    EMAIL,
                    PASSWORD_HASH,
                    ACTIVO,
                    FECHA_ALTA,
                    FECHA_ACTIVACION,
                    FECHA_ULTIMO_ACCESO,
                    TOKEN_ACTIVACION
                )
                VALUES (?, ?, ?, SYSTIMESTAMP, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                usuario.getEmail(),
                usuario.getPasswordHash(),
                usuario.isActivo() ? 1 : 0,
                usuario.getFechaActivacion(),
                usuario.getFechaUltimoAcceso(),
                usuario.getTokenActivacion()
        );

        /*
         * Oracle utiliza IDENTITY para generar el ID.
         * Como EMAIL es UNIQUE, recuperamos el registro
         * recién insertado mediante el email.
         */
        UsuarioApp usuarioGuardado =
                obtenerPorEmail(usuario.getEmail());

        if (usuarioGuardado == null) {
            throw new IllegalStateException(
                    "No se ha podido recuperar el usuario recién creado"
            );
        }

        return usuarioGuardado.getId();
    }

    @Override
    public void actualizarPassword(
            int id,
            String passwordHash) {

        String sql = """
                UPDATE USUARIOS_APP
                SET PASSWORD_HASH = ?
                WHERE ID = ?
                """;

        jdbcTemplate.update(
                sql,
                passwordHash,
                id
        );
    }

    @Override
    public void activarUsuario(int id) {

        String sql = """
                UPDATE USUARIOS_APP
                SET ACTIVO = 1,
                    FECHA_ACTIVACION = SYSTIMESTAMP,
                    TOKEN_ACTIVACION = NULL,
                    TOKEN_ACTIVACION_EXPIRA = NULL
                WHERE ID = ?
                """;

        jdbcTemplate.update(sql, id);
    }

    @Override
    public void desactivarUsuario(int id) {

        String sql = """
                UPDATE USUARIOS_APP
                SET ACTIVO = 0
                WHERE ID = ?
                """;

        jdbcTemplate.update(sql, id);
    }

    @Override
    public void eliminar(int id) {

        String sql = "DELETE FROM USUARIOS_APP WHERE ID = ?";

        jdbcTemplate.update(sql, id);
    }

    @Override
    public void actualizarUltimoAcceso(int id) {

        String sql = """
                UPDATE USUARIOS_APP
                SET FECHA_ULTIMO_ACCESO = SYSTIMESTAMP
                WHERE ID = ?
                """;

        jdbcTemplate.update(sql, id);
    }

    @Override
    public void actualizarTokenActivacion(
            int id,
            String tokenHash,
            Timestamp expiracion) {

        String sql = """
                UPDATE USUARIOS_APP
                SET TOKEN_ACTIVACION = ?,
                    TOKEN_ACTIVACION_EXPIRA = ?,
                    INTENTOS_ACTIVACION = 0
                WHERE ID = ?
                """;

        jdbcTemplate.update(
                sql,
                tokenHash,
                expiracion,
                id
        );
    }

    @Override
    public void incrementarIntentosActivacion(int id) {

        String sql = """
                UPDATE USUARIOS_APP
                SET INTENTOS_ACTIVACION = INTENTOS_ACTIVACION + 1
                WHERE ID = ?
                """;

        jdbcTemplate.update(sql, id);
    }

    @Override
    public void invalidarTokenActivacion(int id) {

        String sql = """
                UPDATE USUARIOS_APP
                SET TOKEN_ACTIVACION = NULL,
                    TOKEN_ACTIVACION_EXPIRA = NULL
                WHERE ID = ?
                """;

        jdbcTemplate.update(sql, id);
    }

    private UsuarioApp mapearUsuario(
            java.sql.ResultSet rs)
            throws java.sql.SQLException {

        UsuarioApp usuario = new UsuarioApp();

        usuario.setId(rs.getInt("ID"));
        usuario.setEmail(rs.getString("EMAIL"));
        usuario.setPasswordHash(
                rs.getString("PASSWORD_HASH")
        );
        usuario.setActivo(
                rs.getInt("ACTIVO") == 1
        );
        usuario.setFechaAlta(
                rs.getTimestamp("FECHA_ALTA")
        );
        usuario.setFechaActivacion(
                rs.getTimestamp("FECHA_ACTIVACION")
        );
        usuario.setFechaUltimoAcceso(
                rs.getTimestamp("FECHA_ULTIMO_ACCESO")
        );
        usuario.setTokenActivacion(
                rs.getString("TOKEN_ACTIVACION")
        );
        usuario.setFechaExpiracionToken(
                rs.getTimestamp("TOKEN_ACTIVACION_EXPIRA")
        );
        usuario.setIntentosActivacion(
                rs.getInt("INTENTOS_ACTIVACION")
        );

        return usuario;
    }
}