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

import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.Temporada;

@Repository
public class TemporadaDaoImpl implements TemporadaDao {

    private static final Logger logger = LoggerFactory.getLogger(TemporadaDaoImpl.class);

    private static final RowMapper<Temporada> TEMPORADA_ROW_MAPPER = TemporadaDaoImpl::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public TemporadaDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean guardarTemporada(Temporada temporada) {
        logger.debug("Inicio guardarTemporada: id={}, nombre={}", temporada.getId(), temporada.getNombre());
        boolean esActualizacion = temporada.getId() != null && obtenerPorId(temporada.getId()) != null;
        boolean resultado = esActualizacion ? actualizarTemporada(temporada) : insertarTemporada(temporada);
        logger.debug("Fin guardarTemporada: esActualizacion={}, resultado={}", esActualizacion, resultado);
        return resultado;
    }

    @Override
    public void desactivarOtrasTemporadas(Long temporadaId) {
        logger.debug("Inicio desactivarOtrasTemporadas: temporadaId={}", temporadaId);
        int filasAfectadas = temporadaId == null
                ? jdbcTemplate.update("UPDATE TEMPORADAS SET ACTIVA = 0")
                : jdbcTemplate.update("UPDATE TEMPORADAS SET ACTIVA = 0 WHERE ID <> ?", temporadaId);
        logger.info("Temporadas desactivadas: exceptoId={}, filasAfectadas={}", temporadaId, filasAfectadas);
        logger.debug("Fin desactivarOtrasTemporadas: filasAfectadas={}", filasAfectadas);
    }

    private boolean insertarTemporada(Temporada temporada) {
        logger.debug("Inicio insertarTemporada: nombre={}", temporada.getNombre());

        String sql = """
                INSERT INTO TEMPORADAS
                (NOMBRE, FECHA_INICIO, FECHA_FIN, ACTIVA)
                VALUES (?, ?, ?, ?)
                """;

        boolean insertada = jdbcTemplate.update(
                sql,
                temporada.getNombre(),
                temporada.getFechaInicio(),
                temporada.getFechaFin(),
                temporada.getActiva()) == 1;
        logger.info("Temporada insertada: nombre={}, insertada={}", temporada.getNombre(), insertada);
        logger.debug("Fin insertarTemporada: insertada={}", insertada);
        return insertada;
    }

    private boolean actualizarTemporada(Temporada temporada) {
        logger.debug("Inicio actualizarTemporada: id={}", temporada.getId());

        String sql = """
                UPDATE TEMPORADAS
                SET NOMBRE = ?,
                    FECHA_INICIO = ?,
                    FECHA_FIN = ?,
                    ACTIVA = ?
                WHERE ID = ?
                """;

        boolean actualizada = jdbcTemplate.update(
                sql,
                temporada.getNombre(),
                temporada.getFechaInicio(),
                temporada.getFechaFin(),
                temporada.getActiva(),
                temporada.getId()) == 1;
        logger.debug("Fin actualizarTemporada: id={}, actualizada={}", temporada.getId(), actualizada);
        return actualizada;
    }

    @Override
    public Temporada obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        try {
            Temporada temporada = jdbcTemplate.queryForObject("SELECT * FROM TEMPORADAS WHERE ID = ?", TEMPORADA_ROW_MAPPER, id);
            logger.debug("Fin obtenerPorId: id={}, encontrada=true", id);
            return temporada;
        } catch (EmptyResultDataAccessException e) {
            logger.warn("No se encontro temporada con id={}", id);
            return null;
        }
    }

    @Override
    public Temporada obtenerTemporadaActiva() {
        logger.debug("Inicio obtenerTemporadaActiva");
        try {
            Temporada temporada = jdbcTemplate.queryForObject("SELECT * FROM TEMPORADAS WHERE ACTIVA = 1", TEMPORADA_ROW_MAPPER);
            logger.debug("Fin obtenerTemporadaActiva: encontrada=true, id={}", temporada.getId());
            return temporada;
        } catch (EmptyResultDataAccessException e) {
            logger.warn("No hay ninguna temporada activa");
            return null;
        }
    }

    @Override
    public List<Temporada> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");

        String sql = """
                SELECT *
                FROM TEMPORADAS
                ORDER BY FECHA_INICIO DESC
                """;

        List<Temporada> temporadas = jdbcTemplate.query(sql, TEMPORADA_ROW_MAPPER);
        logger.debug("Fin obtenerTodos: total={}", temporadas.size());
        return temporadas;
    }

    @Override
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        int filasAfectadas = jdbcTemplate.update("DELETE FROM TEMPORADAS WHERE ID = ?", id);
        logger.info("Temporada eliminada: id={}, filasAfectadas={}", id, filasAfectadas);
        logger.debug("Fin eliminar: id={}", id);
    }

    private static Temporada mapRow(ResultSet rs, int rowNum) throws SQLException {
        Temporada temporada = new Temporada();
        temporada.setId(rs.getLong("ID"));
        temporada.setNombre(rs.getString("NOMBRE"));
        temporada.setFechaInicio(rs.getDate("FECHA_INICIO"));
        temporada.setFechaFin(rs.getDate("FECHA_FIN"));
        temporada.setActiva(rs.getInt("ACTIVA"));
        return temporada;
    }
}