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
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.FamiliarJugadorDao;
import com.mikedev.mutxamelcf.model.FamiliarJugador;

@Repository
public class FamiliarJugadorDaoImpl implements FamiliarJugadorDao {

    private static final Logger logger = LoggerFactory.getLogger(FamiliarJugadorDaoImpl.class);

    private static final RowMapper<FamiliarJugador> FAMILIAR_JUGADOR_ROW_MAPPER = FamiliarJugadorDaoImpl::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public FamiliarJugadorDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static FamiliarJugador mapRow(ResultSet rs, int rowNum) throws SQLException {
        FamiliarJugador relacion = new FamiliarJugador();
        relacion.setId(rs.getLong("ID"));
        relacion.setFamiliarId(rs.getLong("FAMILIAR_ID"));
        relacion.setJugadorId(rs.getLong("JUGADOR_ID"));
        relacion.setParentesco(rs.getString("PARENTESCO"));
        relacion.setEsPrincipal(rs.getInt("ES_PRINCIPAL"));
        return relacion;
    }

    /**
     * Guarda una relación entre familiar y jugador.
     *
     * La operación es transaccional para garantizar que si el familiar
     * se marca como principal, el cambio de principal anterior y el
     * INSERT/UPDATE se realizan como una única operación.
     */
    @Override
    @Transactional
    public boolean guardarFamiliarJugador(FamiliarJugador familiarJugador) {
        logger.debug("Inicio guardarFamiliarJugador: id={}, familiarId={}, jugadorId={}",
                familiarJugador != null ? familiarJugador.getId() : null,
                familiarJugador != null ? familiarJugador.getFamiliarId() : null,
                familiarJugador != null ? familiarJugador.getJugadorId() : null);

        if (familiarJugador == null) {
            return false;
        }

        boolean resultado;
        if (familiarJugador.getId() != null && obtenerPorId(familiarJugador.getId()) != null) {
            resultado = actualizarFamiliarJugador(familiarJugador);
        } else {
            resultado = insertarFamiliarJugador(familiarJugador);
        }
        logger.debug("Fin guardarFamiliarJugador: resultado={}", resultado);
        return resultado;
    }

    /**
     * Inserta una nueva relación entre familiar y jugador.
     *
     * Si el familiar se marca como principal, se desmarcan previamente
     * todos los demás familiares de ese jugador.
     */
    private boolean insertarFamiliarJugador(FamiliarJugador familiarJugador) {
        logger.debug("Inicio insertarFamiliarJugador: familiarId={}, jugadorId={}",
                familiarJugador.getFamiliarId(), familiarJugador.getJugadorId());

        if (esPrincipal(familiarJugador)) {
            quitarPrincipalDelJugador(familiarJugador.getJugadorId(), null);
        }

        String sql = "INSERT INTO FAMILIARES_JUGADOR "
                + "(ID, FAMILIAR_ID, JUGADOR_ID, PARENTESCO, ES_PRINCIPAL) "
                + "VALUES (FAMILIARES_JUGADOR_SEQ.NEXTVAL, ?, ?, ?, ?)";

        boolean insertado = jdbcTemplate.update(
                sql,
                familiarJugador.getFamiliarId(),
                familiarJugador.getJugadorId(),
                familiarJugador.getParentesco(),
                familiarJugador.getEsPrincipal() != null ? familiarJugador.getEsPrincipal() : 0) == 1;

        logger.info("Relacion familiar-jugador insertada: familiarId={}, jugadorId={}, insertado={}",
                familiarJugador.getFamiliarId(), familiarJugador.getJugadorId(), insertado);
        logger.debug("Fin insertarFamiliarJugador: insertado={}", insertado);
        return insertado;
    }

    /**
     * Actualiza una relación existente.
     *
     * Si se marca como principal, se desmarcan todos los demás
     * familiares del mismo jugador.
     */
    private boolean actualizarFamiliarJugador(FamiliarJugador familiarJugador) {
        logger.debug("Inicio actualizarFamiliarJugador: id={}", familiarJugador.getId());

        if (esPrincipal(familiarJugador)) {
            quitarPrincipalDelJugador(familiarJugador.getJugadorId(), familiarJugador.getId());
        }

        String sql = "UPDATE FAMILIARES_JUGADOR SET "
                + "FAMILIAR_ID = ?, "
                + "JUGADOR_ID = ?, "
                + "PARENTESCO = ?, "
                + "ES_PRINCIPAL = ? "
                + "WHERE ID = ?";

        boolean actualizado = jdbcTemplate.update(
                sql,
                familiarJugador.getFamiliarId(),
                familiarJugador.getJugadorId(),
                familiarJugador.getParentesco(),
                familiarJugador.getEsPrincipal() != null ? familiarJugador.getEsPrincipal() : 0,
                familiarJugador.getId()) == 1;

        logger.debug("Fin actualizarFamiliarJugador: id={}, actualizado={}", familiarJugador.getId(), actualizado);
        return actualizado;
    }

    /**
     * Desmarca como principal cualquier otra relación del jugador.
     *
     * @param jugadorId  ID del jugador
     * @param relacionId ID de la relación que estamos guardando.
     *                   Se excluye durante una edición.
     */
    private void quitarPrincipalDelJugador(Long jugadorId, Long relacionId) {
        String sql;

        if (relacionId == null) {
            sql = "UPDATE FAMILIARES_JUGADOR SET ES_PRINCIPAL = 0 WHERE JUGADOR_ID = ? AND ES_PRINCIPAL = 1";
            jdbcTemplate.update(sql, jugadorId);
        } else {
            sql = "UPDATE FAMILIARES_JUGADOR SET ES_PRINCIPAL = 0 WHERE JUGADOR_ID = ? AND ID <> ? AND ES_PRINCIPAL = 1";
            jdbcTemplate.update(sql, jugadorId, relacionId);
        }
    }

    /**
     * Comprueba si la relación está marcada como principal.
     */
    private boolean esPrincipal(FamiliarJugador familiarJugador) {
        return familiarJugador.getEsPrincipal() != null && familiarJugador.getEsPrincipal() == 1;
    }

    @Override
    public FamiliarJugador obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        String sql = "SELECT * FROM FAMILIARES_JUGADOR WHERE ID = ?";

        try {
            FamiliarJugador relacion = jdbcTemplate.queryForObject(sql, FAMILIAR_JUGADOR_ROW_MAPPER, id);
            logger.debug("Fin obtenerPorId: id={}, encontrado=true", id);
            return relacion;
        } catch (EmptyResultDataAccessException e) {
            logger.warn("No se encontro relacion con id={}", id);
            return null;
        }
    }

    @Override
    public List<FamiliarJugador> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        String sql = "SELECT * FROM FAMILIARES_JUGADOR ORDER BY JUGADOR_ID, FAMILIAR_ID";
        List<FamiliarJugador> lista = jdbcTemplate.query(sql, FAMILIAR_JUGADOR_ROW_MAPPER);
        logger.debug("Fin obtenerTodos: total={}", lista.size());
        return lista;
    }

    @Override
    public List<FamiliarJugador> obtenerFamiliaresDeJugador(Long jugadorId) {
        logger.debug("Inicio obtenerFamiliaresDeJugador: jugadorId={}", jugadorId);
        String sql = "SELECT * FROM FAMILIARES_JUGADOR WHERE JUGADOR_ID = ? ORDER BY ES_PRINCIPAL DESC, FAMILIAR_ID";
        List<FamiliarJugador> lista = jdbcTemplate.query(sql, FAMILIAR_JUGADOR_ROW_MAPPER, jugadorId);
        logger.debug("Fin obtenerFamiliaresDeJugador: total={}", lista.size());
        return lista;
    }

    @Override
    public List<FamiliarJugador> obtenerJugadoresDeFamiliar(Long familiarId) {
        logger.debug("Inicio obtenerJugadoresDeFamiliar: familiarId={}", familiarId);
        String sql = "SELECT * FROM FAMILIARES_JUGADOR WHERE FAMILIAR_ID = ? ORDER BY JUGADOR_ID";
        List<FamiliarJugador> lista = jdbcTemplate.query(sql, FAMILIAR_JUGADOR_ROW_MAPPER, familiarId);
        logger.debug("Fin obtenerJugadoresDeFamiliar: total={}", lista.size());
        return lista;
    }

    @Override
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        String sql = "DELETE FROM FAMILIARES_JUGADOR WHERE ID = ?";
        jdbcTemplate.update(sql, id);
        logger.debug("Fin eliminar: id={}", id);
    }

    @Override
    public FamiliarJugador obtenerPrincipalDeJugador(Long jugadorId) {
        logger.debug("Inicio obtenerPrincipalDeJugador: jugadorId={}", jugadorId);
        String sql = "SELECT * FROM FAMILIARES_JUGADOR WHERE JUGADOR_ID = ? AND ES_PRINCIPAL = 1";

        try {
            FamiliarJugador relacion = jdbcTemplate.queryForObject(sql, FAMILIAR_JUGADOR_ROW_MAPPER, jugadorId);
            logger.debug("Fin obtenerPrincipalDeJugador: encontrado=true");
            return relacion;
        } catch (EmptyResultDataAccessException e) {
            logger.debug("Fin obtenerPrincipalDeJugador: encontrado=false");
            return null;
        }
    }

    @Override
    public boolean existeRelacion(Long familiarId, Long jugadorId) {
        String sql = "SELECT COUNT(*) FROM FAMILIARES_JUGADOR WHERE FAMILIAR_ID = ? AND JUGADOR_ID = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, familiarId, jugadorId);
        return count != null && count > 0;
    }

    @Override
    public boolean tieneJugadores(Long familiarId) {
        String sql = "SELECT COUNT(*) FROM FAMILIARES_JUGADOR WHERE FAMILIAR_ID = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, familiarId);
        return count != null && count > 0;
    }
}