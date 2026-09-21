package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.RolApp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.mikedev.mutxamelcf.dao.RolAppDao;

import java.util.List;

@Repository
public class RolAppDaoImpl implements RolAppDao {

    private final JdbcTemplate jdbcTemplate;

    public RolAppDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RolApp obtenerPorCodigo(String codigo) {

        String sql = """
                SELECT ID,
                       CODIGO,
                       NOMBRE
                FROM ROLES_APP
                WHERE CODIGO = ?
                """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setString(1, codigo),
                rs -> {

                    if (rs.next()) {
                        return mapearRol(rs);
                    }

                    return null;
                });
    }

    @Override
    public RolApp obtenerPorId(int id) {

        String sql = """
                SELECT ID,
                       CODIGO,
                       NOMBRE
                FROM ROLES_APP
                WHERE ID = ?
                """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setInt(1, id),
                rs -> {

                    if (rs.next()) {
                        return mapearRol(rs);
                    }

                    return null;
                });
    }

    @Override
    public List<RolApp> obtenerTodos() {

        String sql = """
                SELECT ID,
                       CODIGO,
                       NOMBRE
                FROM ROLES_APP
                ORDER BY ID
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> mapearRol(rs));
    }

    @Override
    public List<RolApp> obtenerPorUsuario(
            int usuarioAppId) {

        String sql = """
                SELECT R.ID,
                       R.CODIGO,
                       R.NOMBRE
                FROM ROLES_APP R
                INNER JOIN USUARIOS_APP_ROLES UAR
                    ON UAR.ROL_ID = R.ID
                WHERE UAR.USUARIO_APP_ID = ?
                ORDER BY R.ID
                """;

        return jdbcTemplate.query(
                sql,
                ps -> ps.setInt(1, usuarioAppId),
                (rs, rowNum) -> mapearRol(rs));
    }

    @Override
    public void asignarRol(
            int usuarioAppId,
            int rolId) {

        String sql = """
                INSERT INTO USUARIOS_APP_ROLES (
                    USUARIO_APP_ID,
                    ROL_ID
                )
                VALUES (?, ?)
                """;

        jdbcTemplate.update(
                sql,
                usuarioAppId,
                rolId);
    }

    @Override
    public void eliminarRol(
            int usuarioAppId,
            int rolId) {

        String sql = """
                DELETE FROM USUARIOS_APP_ROLES
                WHERE USUARIO_APP_ID = ?
                  AND ROL_ID = ?
                """;

        jdbcTemplate.update(
                sql,
                usuarioAppId,
                rolId);
    }

    @Override
    public void eliminarTodosLosRoles(int usuarioAppId) {

        String sql = """
                DELETE FROM USUARIOS_APP_ROLES
                WHERE USUARIO_APP_ID = ?
                """;

        jdbcTemplate.update(sql, usuarioAppId);
    }

    private RolApp mapearRol(
            java.sql.ResultSet rs)
            throws java.sql.SQLException {

        RolApp rol = new RolApp();

        rol.setId(rs.getInt("ID"));
        rol.setCodigo(rs.getString("CODIGO"));
        rol.setNombre(rs.getString("NOMBRE"));

        return rol;
    }
}