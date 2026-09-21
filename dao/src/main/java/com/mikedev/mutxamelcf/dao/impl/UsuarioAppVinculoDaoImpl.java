package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.PersonaVinculable;
import com.mikedev.mutxamelcf.model.VinculoUsuarioApp;

@Repository
public class UsuarioAppVinculoDaoImpl implements UsuarioAppVinculoDao {

    private final JdbcTemplate jdbcTemplate;

    public UsuarioAppVinculoDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PersonaVinculable> obtenerJugadoresSinCuenta() {

        String sql = """
                SELECT J.ID, J.NOMBRE, J.APELLIDOS, J.EQUIPO
                FROM JUGADORES J
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM USUARIOS_APP_JUGADORES UAJ
                    WHERE UAJ.JUGADOR_ID = J.ID
                )
                ORDER BY J.APELLIDOS, J.NOMBRE
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new PersonaVinculable(
                rs.getLong("ID"),
                nombreCompleto(rs.getString("NOMBRE"), rs.getString("APELLIDOS")),
                rs.getString("EQUIPO"),
                null));
    }

    @Override
    public List<PersonaVinculable> obtenerFamiliaresSinCuenta() {

        String sql = """
                SELECT F.ID, F.NOMBRE, F.APELLIDOS, F.EMAIL
                FROM FAMILIARES F
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM USUARIOS_APP_FAMILIARES UAF
                    WHERE UAF.FAMILIAR_ID = F.ID
                )
                ORDER BY F.APELLIDOS, F.NOMBRE
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new PersonaVinculable(
                rs.getLong("ID"),
                nombreCompleto(rs.getString("NOMBRE"), rs.getString("APELLIDOS")),
                null,
                rs.getString("EMAIL")));
    }

    @Override
    public List<PersonaVinculable> obtenerCuerpoTecnicoSinCuenta() {

        String sql = """
                SELECT CT.ID, CT.NOMBRE, CT.APELLIDOS, CT.EQUIPO
                FROM CUERPO_TECNICO CT
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM USUARIOS_APP_CUERPO_TECNICO UACT
                    WHERE UACT.CUERPO_TECNICO_ID = CT.ID
                )
                ORDER BY CT.APELLIDOS, CT.NOMBRE
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new PersonaVinculable(
                rs.getLong("ID"),
                nombreCompleto(rs.getString("NOMBRE"), rs.getString("APELLIDOS")),
                rs.getString("EQUIPO"),
                null));
    }

    @Override
    public boolean jugadorTieneCuenta(Long jugadorId) {

        String sql = """
                SELECT COUNT(*)
                FROM USUARIOS_APP_JUGADORES
                WHERE JUGADOR_ID = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, jugadorId);

        return count != null && count > 0;
    }

    @Override
    public boolean familiarTieneCuenta(Long familiarId) {

        String sql = """
                SELECT COUNT(*)
                FROM USUARIOS_APP_FAMILIARES
                WHERE FAMILIAR_ID = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, familiarId);

        return count != null && count > 0;
    }

    @Override
    public boolean cuerpoTecnicoTieneCuenta(Long cuerpoTecnicoId) {

        String sql = """
                SELECT COUNT(*)
                FROM USUARIOS_APP_CUERPO_TECNICO
                WHERE CUERPO_TECNICO_ID = ?
                """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, cuerpoTecnicoId);

        return count != null && count > 0;
    }

    @Override
    public void vincularJugador(int usuarioAppId, Long jugadorId) {

        String sql = """
                INSERT INTO USUARIOS_APP_JUGADORES (USUARIO_APP_ID, JUGADOR_ID)
                VALUES (?, ?)
                """;

        jdbcTemplate.update(sql, usuarioAppId, jugadorId);
    }

    @Override
    public void vincularFamiliar(int usuarioAppId, Long familiarId) {

        String sql = """
                INSERT INTO USUARIOS_APP_FAMILIARES (USUARIO_APP_ID, FAMILIAR_ID)
                VALUES (?, ?)
                """;

        jdbcTemplate.update(sql, usuarioAppId, familiarId);
    }

    @Override
    public void vincularCuerpoTecnico(int usuarioAppId, Long cuerpoTecnicoId) {

        String sql = """
                INSERT INTO USUARIOS_APP_CUERPO_TECNICO (USUARIO_APP_ID, CUERPO_TECNICO_ID)
                VALUES (?, ?)
                """;

        jdbcTemplate.update(sql, usuarioAppId, cuerpoTecnicoId);
    }

    @Override
    public void desvincularTodo(int usuarioAppId) {

        jdbcTemplate.update(
                "DELETE FROM USUARIOS_APP_JUGADORES WHERE USUARIO_APP_ID = ?",
                usuarioAppId);

        jdbcTemplate.update(
                "DELETE FROM USUARIOS_APP_FAMILIARES WHERE USUARIO_APP_ID = ?",
                usuarioAppId);

        jdbcTemplate.update(
                "DELETE FROM USUARIOS_APP_CUERPO_TECNICO WHERE USUARIO_APP_ID = ?",
                usuarioAppId);
    }

    @Override
    public VinculoUsuarioApp obtenerVinculo(int usuarioAppId) {

        VinculoUsuarioApp vinculo = obtenerVinculoJugador(usuarioAppId);

        if (vinculo != null) {
            return vinculo;
        }

        vinculo = obtenerVinculoFamiliar(usuarioAppId);

        if (vinculo != null) {
            return vinculo;
        }

        return obtenerVinculoCuerpoTecnico(usuarioAppId);
    }

    private VinculoUsuarioApp obtenerVinculoJugador(int usuarioAppId) {

        String sql = """
                SELECT J.ID, J.NOMBRE, J.APELLIDOS
                FROM JUGADORES J
                INNER JOIN USUARIOS_APP_JUGADORES UAJ
                    ON UAJ.JUGADOR_ID = J.ID
                WHERE UAJ.USUARIO_APP_ID = ?
                """;

        return consultarVinculo(sql, usuarioAppId, "JUGADOR");
    }

    private VinculoUsuarioApp obtenerVinculoFamiliar(int usuarioAppId) {

        String sql = """
                SELECT F.ID, F.NOMBRE, F.APELLIDOS
                FROM FAMILIARES F
                INNER JOIN USUARIOS_APP_FAMILIARES UAF
                    ON UAF.FAMILIAR_ID = F.ID
                WHERE UAF.USUARIO_APP_ID = ?
                """;

        return consultarVinculo(sql, usuarioAppId, "FAMILIAR");
    }

    private VinculoUsuarioApp obtenerVinculoCuerpoTecnico(int usuarioAppId) {

        String sql = """
                SELECT CT.ID, CT.NOMBRE, CT.APELLIDOS
                FROM CUERPO_TECNICO CT
                INNER JOIN USUARIOS_APP_CUERPO_TECNICO UACT
                    ON UACT.CUERPO_TECNICO_ID = CT.ID
                WHERE UACT.USUARIO_APP_ID = ?
                """;

        return consultarVinculo(sql, usuarioAppId, "ENTRENADOR");
    }

    @Override
    public String obtenerNombrePersona(String tipo, Long personaId) {

        String tabla = switch (tipo) {
            case "JUGADOR" -> "JUGADORES";
            case "FAMILIAR" -> "FAMILIARES";
            case "ENTRENADOR" -> "CUERPO_TECNICO";
            default -> null;
        };

        if (tabla == null || personaId == null) {
            return null;
        }

        String sql = "SELECT NOMBRE, APELLIDOS FROM " + tabla + " WHERE ID = ?";

        return jdbcTemplate.query(
                sql,
                ps -> ps.setLong(1, personaId),
                (ResultSet rs) -> rs.next()
                        ? nombreCompleto(rs.getString("NOMBRE"), rs.getString("APELLIDOS"))
                        : null);
    }

    @Override
    public String obtenerEmailFamiliar(Long familiarId) {

        if (familiarId == null) {
            return null;
        }

        String sql = "SELECT EMAIL FROM FAMILIARES WHERE ID = ?";

        return jdbcTemplate.query(
                sql,
                ps -> ps.setLong(1, familiarId),
                (ResultSet rs) -> rs.next() ? rs.getString("EMAIL") : null);
    }

    private VinculoUsuarioApp consultarVinculo(String sql, int usuarioAppId, String tipo) {

        return jdbcTemplate.query(
                sql,
                ps -> ps.setInt(1, usuarioAppId),
                (ResultSet rs) -> {

                    if (rs.next()) {
                        return new VinculoUsuarioApp(
                                tipo,
                                rs.getLong("ID"),
                                nombreCompleto(rs.getString("NOMBRE"), rs.getString("APELLIDOS")));
                    }

                    return null;
                });
    }

    private static String nombreCompleto(String nombre, String apellidos) {

        String base = nombre == null ? "" : nombre.trim();
        String ape = apellidos == null ? "" : apellidos.trim();

        return (base + " " + ape).trim();
    }
}
