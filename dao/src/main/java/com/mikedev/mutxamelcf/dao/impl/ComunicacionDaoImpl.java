package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.ComunicacionDao;
import com.mikedev.mutxamelcf.model.Comunicacion;

@Repository
public class ComunicacionDaoImpl implements ComunicacionDao {

    private final JdbcTemplate jdbcTemplate;

    public ComunicacionDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Comunicacion> COMUNICACION_ROW_MAPPER = new RowMapper<Comunicacion>() {

        @Override
        public Comunicacion mapRow(
                ResultSet rs,
                int rowNum) throws SQLException {

            Comunicacion comunicacion = new Comunicacion();

            comunicacion.setId(rs.getLong("ID"));
            comunicacion.setTitulo(rs.getString("TITULO"));
            comunicacion.setContenido(rs.getString("CONTENIDO"));
            comunicacion.setUsuarioAutorId(
                    rs.getLong("USUARIO_AUTOR_ID"));

            Timestamp fechaCreacion = rs.getTimestamp("FECHA_CREACION");

            if (fechaCreacion != null) {
                comunicacion.setFechaCreacion(
                        fechaCreacion.toLocalDateTime());
            }

            Timestamp fechaPublicacion = rs.getTimestamp("FECHA_PUBLICACION");

            if (fechaPublicacion != null) {
                comunicacion.setFechaPublicacion(
                        fechaPublicacion.toLocalDateTime());
            }

            comunicacion.setActiva(
                    rs.getInt("ACTIVA"));

            return comunicacion;
        }
    };

    @Override
    public Long guardar(Comunicacion comunicacion) {

        Long id = jdbcTemplate.queryForObject(
                "SELECT SEQ_COMUNICACIONES.NEXTVAL FROM DUAL",
                Long.class);

        String sql = """
                INSERT INTO COMUNICACIONES
                (
                    ID,
                    TITULO,
                    CONTENIDO,
                    USUARIO_AUTOR_ID,
                    FECHA_PUBLICACION,
                    ACTIVA
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                id,
                comunicacion.getTitulo(),
                comunicacion.getContenido(),
                comunicacion.getUsuarioAutorId(),
                comunicacion.getFechaPublicacion(),
                comunicacion.getActiva());

        comunicacion.setId(id);

        return id;
    }

    @Override
    public Comunicacion obtenerPorId(Long id) {

        String sql = """
                SELECT
                    ID,
                    TITULO,
                    CONTENIDO,
                    USUARIO_AUTOR_ID,
                    FECHA_CREACION,
                    FECHA_PUBLICACION,
                    ACTIVA
                FROM COMUNICACIONES
                WHERE ID = ?
                """;

        List<Comunicacion> resultados = jdbcTemplate.query(
                sql,
                COMUNICACION_ROW_MAPPER,
                id);

        if (resultados.isEmpty()) {
            return null;
        }

        return resultados.get(0);
    }

    @Override
    public List<Comunicacion> obtenerTodas() {

        String sql = """
                SELECT
                    ID,
                    TITULO,
                    CONTENIDO,
                    USUARIO_AUTOR_ID,
                    FECHA_CREACION,
                    FECHA_PUBLICACION,
                    ACTIVA
                FROM COMUNICACIONES
                WHERE ACTIVA = 1
                ORDER BY
                    FECHA_PUBLICACION DESC NULLS LAST,
                    FECHA_CREACION DESC
                """;

        return jdbcTemplate.query(
                sql,
                COMUNICACION_ROW_MAPPER);
    }

    @Override
    public List<Comunicacion> obtenerPorEquipo(Long equipoId) {

        String sql = """
                SELECT DISTINCT
                    c.ID,
                    c.TITULO,
                    c.CONTENIDO,
                    c.USUARIO_AUTOR_ID,
                    c.FECHA_CREACION,
                    c.FECHA_PUBLICACION,
                    c.ACTIVA
                FROM COMUNICACIONES c
                INNER JOIN COMUNICACION_EQUIPO ce
                    ON ce.COMUNICACION_ID = c.ID
                WHERE ce.EQUIPO_ID = ?
                  AND c.ACTIVA = 1
                ORDER BY
                    c.FECHA_PUBLICACION DESC NULLS LAST,
                    c.FECHA_CREACION DESC
                """;

        return jdbcTemplate.query(
                sql,
                COMUNICACION_ROW_MAPPER,
                equipoId);
    }

    @Override
    public List<Comunicacion> obtenerPorCategoria(String categoria) {

        String sql = """
                SELECT DISTINCT
                    c.ID,
                    c.TITULO,
                    c.CONTENIDO,
                    c.USUARIO_AUTOR_ID,
                    c.FECHA_CREACION,
                    c.FECHA_PUBLICACION,
                    c.ACTIVA
                FROM COMUNICACIONES c
                INNER JOIN COMUNICACION_CATEGORIA cc
                    ON cc.COMUNICACION_ID = c.ID
                WHERE UPPER(TRIM(cc.CATEGORIA)) =
                      UPPER(TRIM(?))
                  AND c.ACTIVA = 1
                ORDER BY
                    c.FECHA_PUBLICACION DESC NULLS LAST,
                    c.FECHA_CREACION DESC
                """;

        return jdbcTemplate.query(
                sql,
                COMUNICACION_ROW_MAPPER,
                categoria);
    }

    @Override
    public void guardarEquipo(
            Long comunicacionId,
            Long equipoId) {

        String sql = """
                INSERT INTO COMUNICACION_EQUIPO
                (
                    COMUNICACION_ID,
                    EQUIPO_ID
                )
                VALUES (?, ?)
                """;

        jdbcTemplate.update(
                sql,
                comunicacionId,
                equipoId);
    }

    @Override
    public void guardarCategoria(
            Long comunicacionId,
            String categoria) {

        String sql = """
                INSERT INTO COMUNICACION_CATEGORIA
                (
                    COMUNICACION_ID,
                    CATEGORIA
                )
                VALUES (?, ?)
                """;

        jdbcTemplate.update(
                sql,
                comunicacionId,
                categoria);
    }

    @Override
    public void eliminar(Long id) {

        jdbcTemplate.update(
                "DELETE FROM COMUNICACION_EQUIPO WHERE COMUNICACION_ID = ?",
                id);

        jdbcTemplate.update(
                "DELETE FROM COMUNICACION_CATEGORIA WHERE COMUNICACION_ID = ?",
                id);

        jdbcTemplate.update(
                "DELETE FROM COMUNICACIONES WHERE ID = ?",
                id);
    }

    @Override
    public boolean entrenadorPuedeGestionarEquipo(
            Long usuarioAppId,
            Long equipoId) {

        String sql = """
                SELECT COUNT(*)
                FROM USUARIOS_APP_CUERPO_TECNICO uct
                INNER JOIN CUERPO_TECNICO ct
                    ON ct.ID = uct.CUERPO_TECNICO_ID
                INNER JOIN EQUIPO e
                    ON UPPER(TRIM(e.NOMBRE)) =
                       UPPER(TRIM(ct.EQUIPO))
                WHERE uct.USUARIO_APP_ID = ?
                  AND e.ID = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                usuarioAppId,
                equipoId);

        return count != null && count > 0;
    }

    @Override
    public boolean existeEquipo(Long equipoId) {

        String sql = """
                SELECT COUNT(*)
                FROM EQUIPO
                WHERE ID = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                equipoId);

        return count != null && count > 0;
    }

    @Override
    public boolean existeCategoria(String categoria) {

        String sql = """
                SELECT COUNT(*)
                FROM EQUIPO
                WHERE UPPER(TRIM(CATEGORIA)) =
                      UPPER(TRIM(?))
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                categoria);

        return count != null && count > 0;
    }

    @Override
    public List<Long> obtenerEquiposDeEntrenador(
            Long usuarioAppId) {

        String sql = """
                SELECT DISTINCT e.ID
                FROM USUARIOS_APP_CUERPO_TECNICO uct
                INNER JOIN CUERPO_TECNICO ct
                    ON ct.ID = uct.CUERPO_TECNICO_ID
                INNER JOIN EQUIPO e
                    ON UPPER(TRIM(e.NOMBRE)) =
                       UPPER(TRIM(ct.EQUIPO))
                WHERE uct.USUARIO_APP_ID = ?
                ORDER BY e.ID
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("ID"),
                usuarioAppId);
    }

    @Override
    public List<Long> obtenerEquiposDeJugador(
            Long usuarioAppId) {

        String sql = """
                SELECT DISTINCT e.ID
                FROM USUARIOS_APP_JUGADORES uaj
                INNER JOIN JUGADORES j
                    ON j.ID = uaj.JUGADOR_ID
                INNER JOIN EQUIPO e
                    ON UPPER(TRIM(e.NOMBRE)) =
                       UPPER(TRIM(j.EQUIPO))
                WHERE uaj.USUARIO_APP_ID = ?
                ORDER BY e.ID
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("ID"),
                usuarioAppId);
    }

    @Override
    public List<Long> obtenerEquiposDeFamiliar(
            Long usuarioAppId) {

        String sql = """
                SELECT DISTINCT e.ID
                FROM USUARIOS_APP_FAMILIARES uaf
                INNER JOIN FAMILIARES f
                    ON f.ID = uaf.FAMILIAR_ID
                INNER JOIN FAMILIARES_JUGADOR fj
                    ON fj.FAMILIAR_ID = f.ID
                INNER JOIN JUGADORES j
                    ON j.ID = fj.JUGADOR_ID
                INNER JOIN EQUIPO e
                    ON UPPER(TRIM(e.NOMBRE)) =
                       UPPER(TRIM(j.EQUIPO))
                WHERE uaf.USUARIO_APP_ID = ?
                ORDER BY e.ID
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("ID"),
                usuarioAppId);
    }

    @Override
    public List<Comunicacion> obtenerParaUsuario(
            Long usuarioAppId) {

        /*
         * Este método se mantiene disponible en el DAO.
         * La selección definitiva de audiencia se realiza
         * actualmente desde el Service según el rol del usuario.
         */

        List<Long> equiposJugador = obtenerEquiposDeJugador(usuarioAppId);

        List<Long> equiposFamiliar = obtenerEquiposDeFamiliar(usuarioAppId);

        List<Long> equipos = new ArrayList<>();

        equipos.addAll(equiposJugador);
        equipos.addAll(equiposFamiliar);

        if (equipos.isEmpty()) {
            return Collections.emptyList();
        }

        return obtenerPorEquiposYCategorias(equipos);
    }

    @Override
    public List<Comunicacion> obtenerPorEquipos(
            List<Long> equipoIds) {

        if (equipoIds == null || equipoIds.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = String.join(
                ",",
                Collections.nCopies(
                        equipoIds.size(),
                        "?"));

        String sql = """
                SELECT DISTINCT
                    c.ID,
                    c.TITULO,
                    c.CONTENIDO,
                    c.USUARIO_AUTOR_ID,
                    c.FECHA_CREACION,
                    c.FECHA_PUBLICACION,
                    c.ACTIVA
                FROM COMUNICACIONES c
                LEFT JOIN COMUNICACION_EQUIPO ce
                    ON ce.COMUNICACION_ID = c.ID
                WHERE c.ACTIVA = 1
                  AND ce.EQUIPO_ID IN (%s)
                ORDER BY
                    c.FECHA_PUBLICACION DESC NULLS LAST,
                    c.FECHA_CREACION DESC
                """.formatted(placeholders);

        return jdbcTemplate.query(
                sql,
                COMUNICACION_ROW_MAPPER,
                equipoIds.toArray());
    }

    @Override
    public List<Comunicacion> obtenerPorEquiposYCategorias(
            List<Long> equipoIds) {

        if (equipoIds == null || equipoIds.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = String.join(
                ",",
                Collections.nCopies(
                        equipoIds.size(),
                        "?"));

        String sql = """
                SELECT DISTINCT
                    c.ID,
                    c.TITULO,
                    c.CONTENIDO,
                    c.USUARIO_AUTOR_ID,
                    c.FECHA_CREACION,
                    c.FECHA_PUBLICACION,
                    c.ACTIVA
                FROM COMUNICACIONES c
                LEFT JOIN COMUNICACION_EQUIPO ce
                    ON ce.COMUNICACION_ID = c.ID
                LEFT JOIN COMUNICACION_CATEGORIA cc
                    ON cc.COMUNICACION_ID = c.ID
                LEFT JOIN EQUIPO e
                    ON e.ID IN (%s)
                WHERE c.ACTIVA = 1
                  AND
                  (
                      ce.EQUIPO_ID IN (%s)
                      OR
                      UPPER(TRIM(cc.CATEGORIA)) =
                      UPPER(TRIM(e.CATEGORIA))
                  )
                ORDER BY
                    c.FECHA_PUBLICACION DESC NULLS LAST,
                    c.FECHA_CREACION DESC
                """.formatted(
                placeholders,
                placeholders);

        List<Object> parametros = new ArrayList<>();

        parametros.addAll(equipoIds);
        parametros.addAll(equipoIds);

        return jdbcTemplate.query(
                sql,
                COMUNICACION_ROW_MAPPER,
                parametros.toArray());
    }

    @Override
    public List<Long> obtenerUsuariosDelEquipo(Long equipoId) {

        return jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT USUARIO_APP_ID
                        FROM (

                            /* JUGADORES */
                            SELECT uaj.USUARIO_APP_ID
                            FROM USUARIOS_APP_JUGADORES uaj
                            INNER JOIN JUGADORES j
                                ON j.ID = uaj.JUGADOR_ID
                            INNER JOIN EQUIPO e
                                ON UPPER(TRIM(e.NOMBRE))
                                 = UPPER(TRIM(j.EQUIPO))
                            WHERE e.ID = ?

                            UNION

                            /* FAMILIARES */
                            SELECT uaf.USUARIO_APP_ID
                            FROM USUARIOS_APP_FAMILIARES uaf
                            INNER JOIN FAMILIARES f
                                ON f.ID = uaf.FAMILIAR_ID
                            INNER JOIN FAMILIARES_JUGADOR fj
                                ON fj.FAMILIAR_ID = f.ID
                            INNER JOIN JUGADORES j
                                ON j.ID = fj.JUGADOR_ID
                            INNER JOIN EQUIPO e
                                ON UPPER(TRIM(e.NOMBRE))
                                 = UPPER(TRIM(j.EQUIPO))
                            WHERE e.ID = ?

                            UNION

                            /* CUERPO TÉCNICO */
                            SELECT uct.USUARIO_APP_ID
                            FROM USUARIOS_APP_CUERPO_TECNICO uct
                            INNER JOIN CUERPO_TECNICO ct
                                ON ct.ID = uct.CUERPO_TECNICO_ID
                            INNER JOIN EQUIPO e
                                ON UPPER(TRIM(e.NOMBRE))
                                 = UPPER(TRIM(ct.EQUIPO))
                            WHERE e.ID = ?

                        )
                        """,
                Long.class,
                equipoId,
                equipoId,
                equipoId);
    }

    @Override
    public List<Long> obtenerUsuariosDeCategoria(
            String categoria) {

        return jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT USUARIO_APP_ID
                        FROM (

                            /* JUGADORES */
                            SELECT uaj.USUARIO_APP_ID
                            FROM USUARIOS_APP_JUGADORES uaj
                            INNER JOIN JUGADORES j
                                ON j.ID = uaj.JUGADOR_ID
                            WHERE UPPER(TRIM(j.CATEGORIA))
                                = UPPER(TRIM(?))

                            UNION

                            /* FAMILIARES */
                            SELECT uaf.USUARIO_APP_ID
                            FROM USUARIOS_APP_FAMILIARES uaf
                            INNER JOIN FAMILIARES f
                                ON f.ID = uaf.FAMILIAR_ID
                            INNER JOIN FAMILIARES_JUGADOR fj
                                ON fj.FAMILIAR_ID = f.ID
                            INNER JOIN JUGADORES j
                                ON j.ID = fj.JUGADOR_ID
                            WHERE UPPER(TRIM(j.CATEGORIA))
                                = UPPER(TRIM(?))

                            UNION

                            /* CUERPO TÉCNICO */
                            SELECT uct.USUARIO_APP_ID
                            FROM USUARIOS_APP_CUERPO_TECNICO uct
                            INNER JOIN CUERPO_TECNICO ct
                                ON ct.ID = uct.CUERPO_TECNICO_ID
                            WHERE UPPER(TRIM(ct.CATEGORIA))
                                = UPPER(TRIM(?))

                        )
                        """,
                Long.class,
                categoria,
                categoria,
                categoria);
    }

    @Override
    public void guardarUsuario(
            Long comunicacionId,
            Long usuarioAppId) {

        String sql = """
                INSERT INTO COMUNICACION_USUARIO
                (
                    COMUNICACION_ID,
                    USUARIO_APP_ID
                )
                VALUES (?, ?)
                """;

        jdbcTemplate.update(
                sql,
                comunicacionId,
                usuarioAppId);
    }

    @Override
    public List<Comunicacion> obtenerPorUsuarioDirecto(
            Long usuarioAppId) {

        String sql = """
                SELECT DISTINCT
                    c.ID,
                    c.TITULO,
                    c.CONTENIDO,
                    c.USUARIO_AUTOR_ID,
                    c.FECHA_CREACION,
                    c.FECHA_PUBLICACION,
                    c.ACTIVA
                FROM COMUNICACIONES c
                INNER JOIN COMUNICACION_USUARIO cu
                    ON cu.COMUNICACION_ID = c.ID
                WHERE cu.USUARIO_APP_ID = ?
                  AND c.ACTIVA = 1
                ORDER BY
                    c.FECHA_PUBLICACION DESC NULLS LAST,
                    c.FECHA_CREACION DESC
                """;

        return jdbcTemplate.query(
                sql,
                COMUNICACION_ROW_MAPPER,
                usuarioAppId);
    }

    @Override
    public boolean usuarioPuedeVerDirectamente(
            Long comunicacionId,
            Long usuarioAppId) {

        String sql = """
                SELECT COUNT(*)
                FROM COMUNICACION_USUARIO
                WHERE COMUNICACION_ID = ?
                  AND USUARIO_APP_ID = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                comunicacionId,
                usuarioAppId);

        return count != null && count > 0;
    }

}