package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.Jugador;

@Repository
public class PerfilAppDaoImpl implements PerfilAppDao {

    private static final Logger logger = LoggerFactory.getLogger(PerfilAppDaoImpl.class);

    private final JdbcTemplate jdbcTemplate;

    public PerfilAppDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Familiar> FAMILIAR_ROW_MAPPER = PerfilAppDaoImpl::mapFamiliar;

    private static final RowMapper<Jugador> JUGADOR_ROW_MAPPER = PerfilAppDaoImpl::mapJugador;

    private static final RowMapper<CuerpoTecnico> CUERPO_TECNICO_ROW_MAPPER = PerfilAppDaoImpl::mapCuerpoTecnico;

    private static final RowMapper<Equipo> EQUIPO_ROW_MAPPER = PerfilAppDaoImpl::mapEquipo;

    // ============================================================
    // FAMILIAR
    // ============================================================

    @Override
    public Familiar obtenerFamiliarPorUsuario(int usuarioAppId) {

        logger.debug(
                "Inicio obtenerFamiliarPorUsuario: usuarioAppId={}",
                usuarioAppId);

        String sql = """
                SELECT
                    f.ID,
                    f.NOMBRE,
                    f.APELLIDOS,
                    f.TELEFONO,
                    f.EMAIL,
                    f.RECIBE_INFO_CLUB,
                    f.WHATSAPP_ACTIVO
                FROM USUARIOS_APP_FAMILIARES uaf
                INNER JOIN FAMILIARES f
                    ON f.ID = uaf.FAMILIAR_ID
                WHERE uaf.USUARIO_APP_ID = ?
                """;

        try {

            Familiar familiar = jdbcTemplate.queryForObject(
                    sql,
                    FAMILIAR_ROW_MAPPER,
                    usuarioAppId);

            logger.debug(
                    "Fin obtenerFamiliarPorUsuario: usuarioAppId={}, encontrado=true",
                    usuarioAppId);

            return familiar;

        } catch (EmptyResultDataAccessException e) {

            logger.debug(
                    "No existe familiar asociado al usuario: usuarioAppId={}",
                    usuarioAppId);

            return null;
        }
    }

    // ============================================================
    // JUGADORES
    // ============================================================

    @Override
    public List<Jugador> obtenerJugadoresPorUsuario(int usuarioAppId) {

        logger.debug(
                "Inicio obtenerJugadoresPorUsuario: usuarioAppId={}",
                usuarioAppId);

        /*
         * IMPORTANTE:
         *
         * No seleccionamos FOTO porque es un BLOB y el perfil
         * no necesita cargar la fotografía.
         */

        String sql = """
                SELECT *
                FROM (
                    SELECT DISTINCT
                        j.ID,
                        j.NOMBRE,
                        j.APELLIDOS,
                        j.CATEGORIA,
                        j.DEPORTE,
                        j.EQUIPO,
                        j.DORSAL,
                        j.POSICION
                    FROM JUGADORES j
                    INNER JOIN USUARIOS_APP_JUGADORES uaj
                        ON uaj.JUGADOR_ID = j.ID
                    WHERE uaj.USUARIO_APP_ID = ?

                    UNION

                    SELECT DISTINCT
                        j.ID,
                        j.NOMBRE,
                        j.APELLIDOS,
                        j.CATEGORIA,
                        j.DEPORTE,
                        j.EQUIPO,
                        j.DORSAL,
                        j.POSICION
                    FROM JUGADORES j
                    INNER JOIN FAMILIARES_JUGADOR fj
                        ON fj.JUGADOR_ID = j.ID
                    INNER JOIN USUARIOS_APP_FAMILIARES uaf
                        ON uaf.FAMILIAR_ID = fj.FAMILIAR_ID
                    WHERE uaf.USUARIO_APP_ID = ?
                )
                ORDER BY NOMBRE, APELLIDOS
                """;

        List<Jugador> jugadores = jdbcTemplate.query(
                sql,
                JUGADOR_ROW_MAPPER,
                usuarioAppId,
                usuarioAppId);

        logger.debug(
                "Fin obtenerJugadoresPorUsuario: usuarioAppId={}, total={}",
                usuarioAppId,
                jugadores.size());

        return jugadores;
    }

    // ============================================================
    // CUERPO TÉCNICO
    // ============================================================

    @Override
    public List<CuerpoTecnico> obtenerCuerpoTecnicoPorUsuario(
            int usuarioAppId) {

        logger.debug(
                "Inicio obtenerCuerpoTecnicoPorUsuario: usuarioAppId={}",
                usuarioAppId);

        String sql = """
                SELECT
                    ct.ID,
                    ct.DNI,
                    ct.NOMBRE,
                    ct.APELLIDOS,
                    ct.CATEGORIA,
                    ct.DEPORTE,
                    ct.EQUIPO,
                    ct.PUESTO
                FROM USUARIOS_APP_CUERPO_TECNICO uact
                INNER JOIN CUERPO_TECNICO ct
                    ON ct.ID = uact.CUERPO_TECNICO_ID
                WHERE uact.USUARIO_APP_ID = ?
                ORDER BY ct.EQUIPO, ct.APELLIDOS, ct.NOMBRE
                """;

        List<CuerpoTecnico> cuerpoTecnico = jdbcTemplate.query(
                sql,
                CUERPO_TECNICO_ROW_MAPPER,
                usuarioAppId);

        logger.debug(
                "Fin obtenerCuerpoTecnicoPorUsuario: usuarioAppId={}, total={}",
                usuarioAppId,
                cuerpoTecnico.size());

        return cuerpoTecnico;
    }

    // ============================================================
    // EQUIPOS
    // ============================================================

    @Override
    public List<Equipo> obtenerEquiposPorUsuario(int usuarioAppId) {

        logger.debug(
                "Inicio obtenerEquiposPorUsuario: usuarioAppId={}",
                usuarioAppId);

        /*
         * CUERPO_TECNICO.EQUIPO contiene el nombre del equipo.
         * EQUIPO.NOMBRE contiene el mismo nombre.
         *
         * Utilizamos TRIM + UPPER para evitar problemas por
         * diferencias de espacios o mayúsculas.
         */

        String sql = """
                SELECT DISTINCT
                    e.ID,
                    e.CATEGORIA,
                    e.GRUPO,
                    e.ORDEN,
                    e.NOMBRE,
                    e.DEPORTE
                FROM USUARIOS_APP_CUERPO_TECNICO uact
                INNER JOIN CUERPO_TECNICO ct
                    ON ct.ID = uact.CUERPO_TECNICO_ID
                INNER JOIN EQUIPO e
                    ON UPPER(TRIM(e.NOMBRE)) = UPPER(TRIM(ct.EQUIPO))
                WHERE uact.USUARIO_APP_ID = ?
                ORDER BY e.ORDEN, e.NOMBRE
                """;

        List<Equipo> equipos = jdbcTemplate.query(
                sql,
                EQUIPO_ROW_MAPPER,
                usuarioAppId);

        logger.debug(
                "Fin obtenerEquiposPorUsuario: usuarioAppId={}, total={}",
                usuarioAppId,
                equipos.size());

        return equipos;
    }

    // ============================================================
    // ROW MAPPERS
    // ============================================================

    private static Familiar mapFamiliar(
            ResultSet rs,
            int rowNum) throws SQLException {

        Familiar familiar = new Familiar();

        familiar.setId(rs.getLong("ID"));
        familiar.setNombre(rs.getString("NOMBRE"));
        familiar.setApellidos(rs.getString("APELLIDOS"));
        familiar.setTelefono(rs.getString("TELEFONO"));
        familiar.setEmail(rs.getString("EMAIL"));
        familiar.setRecibeInfoClub(
                rs.getInt("RECIBE_INFO_CLUB"));
        familiar.setWhatsappActivo(
                rs.getInt("WHATSAPP_ACTIVO"));

        return familiar;
    }

    private static Jugador mapJugador(
            ResultSet rs,
            int rowNum) throws SQLException {

        Jugador jugador = new Jugador();

        jugador.setId(rs.getLong("ID"));
        jugador.setNombre(rs.getString("NOMBRE"));
        jugador.setApellidos(rs.getString("APELLIDOS"));
        jugador.setCategoria(rs.getString("CATEGORIA"));
        jugador.setDeporte(rs.getString("DEPORTE"));
        jugador.setEquipo(rs.getString("EQUIPO"));
        jugador.setDorsal(rs.getInt("DORSAL"));
        jugador.setPosicion(rs.getString("POSICION"));

        /*
         * FOTO no se carga intencionadamente.
         */

        return jugador;
    }

    private static CuerpoTecnico mapCuerpoTecnico(
            ResultSet rs,
            int rowNum) throws SQLException {

        CuerpoTecnico cuerpoTecnico = new CuerpoTecnico();

        cuerpoTecnico.setId(rs.getLong("ID"));
        cuerpoTecnico.setDni(rs.getString("DNI"));
        cuerpoTecnico.setNombre(rs.getString("NOMBRE"));
        cuerpoTecnico.setApellidos(rs.getString("APELLIDOS"));
        cuerpoTecnico.setCategoria(rs.getString("CATEGORIA"));
        cuerpoTecnico.setDeporte(rs.getString("DEPORTE"));
        cuerpoTecnico.setEquipo(rs.getString("EQUIPO"));
        cuerpoTecnico.setPuesto(rs.getString("PUESTO"));

        /*
         * FOTO no se carga intencionadamente.
         */

        return cuerpoTecnico;
    }

    private static Equipo mapEquipo(
            ResultSet rs,
            int rowNum) throws SQLException {

        Equipo equipo = new Equipo();

        equipo.setId(rs.getLong("ID"));
        equipo.setCategoria(rs.getString("CATEGORIA"));
        equipo.setGrupo(rs.getString("GRUPO"));
        equipo.setOrden(rs.getString("ORDEN"));
        equipo.setNombre(rs.getString("NOMBRE"));
        equipo.setDeporte(rs.getString("DEPORTE"));

        return equipo;
    }
}