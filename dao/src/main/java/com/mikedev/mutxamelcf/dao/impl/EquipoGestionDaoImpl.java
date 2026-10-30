package com.mikedev.mutxamelcf.dao.impl;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.Equipo;

@Repository
public class EquipoGestionDaoImpl implements EquipoGestionDao {

    private final JdbcTemplate jdbcTemplate;

    public EquipoGestionDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
    public boolean puedeGestionarEquipo(
            Long usuarioAppId,
            Long equipoId) {

        String sql = """
                SELECT COUNT(*)
                FROM EQUIPO E
                WHERE E.ID = ?
                  AND (
                        /*
                         * ADMIN_APP o COORDINADOR:
                         * pueden gestionar cualquier equipo.
                         */
                        EXISTS (
                            SELECT 1
                            FROM USUARIOS_APP_ROLES UAR
                            INNER JOIN ROLES_APP R
                                ON R.ID = UAR.ROL_ID
                            WHERE UAR.USUARIO_APP_ID = ?
                              AND R.CODIGO IN ('ADMIN_APP', 'COORDINADOR')
                        )

                        OR

                        /*
                         * ENTRENADOR:
                         * únicamente sus equipos asignados.
                         */
                        EXISTS (
                            SELECT 1
                            FROM USUARIOS_APP_CUERPO_TECNICO UACT
                            INNER JOIN CUERPO_TECNICO_EQUIPO CTE
                                ON CTE.CUERPO_TECNICO_ID = UACT.CUERPO_TECNICO_ID
                            WHERE UACT.USUARIO_APP_ID = ?
                              AND CTE.EQUIPO_ID = E.ID
                        )
                  )
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                equipoId,
                usuarioAppId,
                usuarioAppId);

        return count != null && count > 0;
    }

    @Override
    public boolean gestionaAlgunEquipo(Long usuarioAppId) {

        String sql = """
                SELECT COUNT(*)
                FROM DUAL
                WHERE EXISTS (
                        SELECT 1
                        FROM USUARIOS_APP_ROLES UAR
                        INNER JOIN ROLES_APP R
                            ON R.ID = UAR.ROL_ID
                        WHERE UAR.USUARIO_APP_ID = ?
                          AND R.CODIGO IN ('ADMIN_APP', 'COORDINADOR')
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM USUARIOS_APP_CUERPO_TECNICO UACT
                        INNER JOIN CUERPO_TECNICO_EQUIPO CTE
                            ON CTE.CUERPO_TECNICO_ID = UACT.CUERPO_TECNICO_ID
                        WHERE UACT.USUARIO_APP_ID = ?
                    )
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, usuarioAppId, usuarioAppId);

        return count != null && count > 0;
    }

    @Override
    public List<Long> obtenerJugadoresPorEquipo(Long equipoId) {

        String sql = """
                SELECT J.ID
                FROM JUGADORES J
                WHERE J.EQUIPO_ID = ?
                  AND J.FECHA_BAJA IS NULL
                ORDER BY J.DORSAL ASC
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("ID"),
                equipoId);
    }

    @Override
    public String obtenerNombreEquipo(Long equipoId) {

        String sql = """
                SELECT NOMBRE
                FROM EQUIPO
                WHERE ID = ?
                """;

        return jdbcTemplate.queryForObject(
                sql,
                String.class,
                equipoId);
    }

    @Override
    public List<Long> obtenerCoordinadores() {
        return obtenerUsuariosPorRol("COORDINADOR");
    }

    @Override
    public boolean esCoordinadorOAdmin(Long usuarioAppId) {

        Integer total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM USUARIOS_APP_ROLES UAR
                INNER JOIN ROLES_APP R
                    ON R.ID = UAR.ROL_ID
                WHERE UAR.USUARIO_APP_ID = ?
                  AND R.CODIGO IN ('ADMIN_APP', 'COORDINADOR')
                """, Integer.class, usuarioAppId);

        return total != null && total > 0;
    }

    @Override
    public List<Long> obtenerAdministradoresApp() {
        return obtenerUsuariosPorRol("ADMIN_APP");
    }

    private List<Long> obtenerUsuariosPorRol(String codigoRol) {

        String sql = """
                SELECT DISTINCT U.ID
                FROM USUARIOS_APP U
                INNER JOIN USUARIOS_APP_ROLES UAR
                    ON UAR.USUARIO_APP_ID = U.ID
                INNER JOIN ROLES_APP R
                    ON R.ID = UAR.ROL_ID
                WHERE R.CODIGO = ?
                  AND U.ACTIVO = 1
                ORDER BY U.ID
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("ID"),
                codigoRol);
    }

    @Override
    public List<Long> obtenerEntrenadoresPorEquipo(Long equipoId) {

        String sql = """
                SELECT DISTINCT U.ID
                FROM USUARIOS_APP U
                INNER JOIN USUARIOS_APP_CUERPO_TECNICO UACT
                    ON UACT.USUARIO_APP_ID = U.ID
                INNER JOIN CUERPO_TECNICO_EQUIPO CTE
                    ON CTE.CUERPO_TECNICO_ID = UACT.CUERPO_TECNICO_ID
                INNER JOIN EQUIPO E
                    ON E.ID = CTE.EQUIPO_ID
                INNER JOIN USUARIOS_APP_ROLES UAR
                    ON UAR.USUARIO_APP_ID = U.ID
                INNER JOIN ROLES_APP R
                    ON R.ID = UAR.ROL_ID
                WHERE E.ID = ?
                  AND R.CODIGO = 'ENTRENADOR'
                  AND U.ACTIVO = 1
                ORDER BY U.ID
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("ID"),
                equipoId);
    }

    @Override
    public List<Long> obtenerUsuariosPorJugador(Long jugadorId) {

        String sql = """
                SELECT DISTINCT U.ID
                FROM USUARIOS_APP U
                INNER JOIN USUARIOS_APP_JUGADORES UAJ
                    ON UAJ.USUARIO_APP_ID = U.ID
                WHERE UAJ.JUGADOR_ID = ?
                  AND U.ACTIVO = 1
                ORDER BY U.ID
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("ID"),
                jugadorId);
    }

    @Override
    public List<Long> obtenerUsuariosFamiliaresPorJugador(Long jugadorId) {

        String sql = """
                SELECT DISTINCT U.ID
                FROM USUARIOS_APP U
                INNER JOIN USUARIOS_APP_FAMILIARES UAF
                    ON UAF.USUARIO_APP_ID = U.ID
                INNER JOIN FAMILIARES_JUGADOR FJ
                    ON FJ.FAMILIAR_ID = UAF.FAMILIAR_ID
                WHERE FJ.JUGADOR_ID = ?
                  AND U.ACTIVO = 1
                ORDER BY U.ID
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("ID"),
                jugadorId);
    }

    @Override
    public String obtenerNombreJugador(Long jugadorId) {

        String sql = """
                SELECT NOMBRE
                FROM JUGADORES
                WHERE ID = ?
                """;

        return jdbcTemplate.queryForObject(
                sql,
                String.class,
                jugadorId);
    }

    @Override
    public boolean perteneceJugadorAEquipo(
            Long jugadorId,
            Long equipoId) {

        String sql = """
                SELECT COUNT(*)
                FROM JUGADORES J
                WHERE J.ID = ?
                  AND J.EQUIPO_ID = ?
                  AND J.FECHA_BAJA IS NULL
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                jugadorId,
                equipoId);

        return count != null && count > 0;
    }

    @Override
    public Long obtenerEquipoIdDeJugador(Long jugadorId) {

        List<Long> ids = jdbcTemplate.queryForList(
                """
                        SELECT J.EQUIPO_ID
                        FROM JUGADORES J
                        WHERE J.ID = ?
                          AND J.EQUIPO_ID IS NOT NULL
                          AND J.FECHA_BAJA IS NULL
                        """,
                Long.class,
                jugadorId);

        return ids.isEmpty() ? null : ids.get(0);
    }


    @Override
    public boolean equipoAdmiteEstadisticas(Long equipoId) {

        List<String> modalidades = jdbcTemplate.query(
                """
                SELECT COALESCE(ET.MODALIDAD, E.MODALIDAD)
                FROM EQUIPO E
                LEFT JOIN EQUIPO_TEMPORADA ET ON ET.EQUIPO_ID = E.ID AND ET.TEMPORADA_ID = ?
                WHERE E.ID = ?
                """,
                (rs, fila) -> rs.getString(1),
                TemporadaActivaId.obtenerOMenosUno(jdbcTemplate),
                equipoId);

        if (modalidades.isEmpty()) {
            return false;
        }

        Equipo equipo = new Equipo();
        equipo.setModalidad(modalidades.get(0));

        return equipo.admiteEstadisticas();
    }
}
