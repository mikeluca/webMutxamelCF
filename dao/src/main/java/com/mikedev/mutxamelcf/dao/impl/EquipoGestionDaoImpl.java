package com.mikedev.mutxamelcf.dao.impl;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;

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
    public boolean puedeGestionarEquipo(Long usuarioAppId, Long equipoId) {

        String sql = """
                SELECT COUNT(*)
                FROM USUARIOS_APP_CUERPO_TECNICO UACT
                INNER JOIN CUERPO_TECNICO CT
                    ON CT.ID = UACT.CUERPO_TECNICO_ID
                INNER JOIN EQUIPO E
                    ON E.NOMBRE = CT.EQUIPO
                WHERE UACT.USUARIO_APP_ID = ?
                  AND E.ID = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                usuarioAppId,
                equipoId);

        return count != null && count > 0;
    }

    @Override
    public List<Long> obtenerJugadoresPorEquipo(Long equipoId) {

        String sql = """
                SELECT J.ID
                FROM JUGADORES J
                INNER JOIN EQUIPO E
                    ON E.NOMBRE = J.EQUIPO
                WHERE E.ID = ?
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

        String sql = """
                SELECT DISTINCT U.ID
                FROM USUARIOS_APP U
                INNER JOIN USUARIOS_APP_ROLES UAR
                    ON UAR.USUARIO_APP_ID = U.ID
                INNER JOIN ROLES_APP R
                    ON R.ID = UAR.ROL_ID
                WHERE R.CODIGO = 'COORDINADOR'
                  AND U.ACTIVO = 1
                ORDER BY U.ID
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("ID"));
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
                INNER JOIN EQUIPO E
                    ON E.NOMBRE = J.EQUIPO
                WHERE J.ID = ?
                  AND E.ID = ?
                """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                jugadorId,
                equipoId);

        return count != null && count > 0;
    }

}