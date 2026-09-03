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

import com.mikedev.mutxamelcf.dao.ConceptoPagoDao;
import com.mikedev.mutxamelcf.model.ConceptoPago;

@Repository
public class ConceptoPagoDaoImpl implements ConceptoPagoDao {

    private static final Logger logger = LoggerFactory.getLogger(ConceptoPagoDaoImpl.class);

    private static final RowMapper<ConceptoPago> CONCEPTO_ROW_MAPPER = ConceptoPagoDaoImpl::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public ConceptoPagoDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean guardarConceptoPago(ConceptoPago concepto) {
        logger.debug("Inicio guardarConceptoPago: id={}, nombre={}", concepto.getId(), concepto.getNombre());
        boolean esActualizacion = concepto.getId() != null && obtenerPorId(concepto.getId()) != null;
        boolean resultado = esActualizacion ? actualizar(concepto) : insertar(concepto);
        logger.debug("Fin guardarConceptoPago: esActualizacion={}, resultado={}", esActualizacion, resultado);
        return resultado;
    }

    private boolean insertar(ConceptoPago concepto) {
        logger.debug("Inicio insertar: temporadaId={}, nombre={}", concepto.getTemporadaId(), concepto.getNombre());

        String sql = """
                INSERT INTO CONCEPTOS_PAGO
                (TEMPORADA_ID, NOMBRE, DESCRIPCION, IMPORTE, ACTIVO)
                VALUES (?, ?, ?, ?, ?)
                """;

        boolean insertado = jdbcTemplate.update(
                sql,
                concepto.getTemporadaId(),
                concepto.getNombre(),
                concepto.getDescripcion(),
                concepto.getImporte(),
                concepto.getActivo()
        ) == 1;
        logger.info("Concepto de pago insertado: nombre={}, insertado={}", concepto.getNombre(), insertado);
        logger.debug("Fin insertar: insertado={}", insertado);
        return insertado;
    }

    private boolean actualizar(ConceptoPago concepto) {
        logger.debug("Inicio actualizar: id={}", concepto.getId());

        String sql = """
                UPDATE CONCEPTOS_PAGO
                SET TEMPORADA_ID = ?,
                    NOMBRE = ?,
                    DESCRIPCION = ?,
                    IMPORTE = ?,
                    ACTIVO = ?
                WHERE ID = ?
                """;

        boolean actualizado = jdbcTemplate.update(
                sql,
                concepto.getTemporadaId(),
                concepto.getNombre(),
                concepto.getDescripcion(),
                concepto.getImporte(),
                concepto.getActivo(),
                concepto.getId()
        ) == 1;
        logger.debug("Fin actualizar: id={}, actualizado={}", concepto.getId(), actualizado);
        return actualizado;
    }

    @Override
    public ConceptoPago obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        try {
            ConceptoPago concepto = jdbcTemplate.queryForObject("SELECT * FROM CONCEPTOS_PAGO WHERE ID = ?", CONCEPTO_ROW_MAPPER, id);
            logger.debug("Fin obtenerPorId: id={}, encontrado=true", id);
            return concepto;
        } catch (EmptyResultDataAccessException e) {
            logger.warn("No se encontro concepto de pago con id={}", id);
            return null;
        }
    }

    @Override
    public List<ConceptoPago> obtenerPorTemporada(Long temporadaId) {
        logger.debug("Inicio obtenerPorTemporada: temporadaId={}", temporadaId);

        String sql = """
                SELECT *
                FROM CONCEPTOS_PAGO
                WHERE TEMPORADA_ID = ?
                ORDER BY NOMBRE
                """;

        List<ConceptoPago> conceptos = jdbcTemplate.query(sql, CONCEPTO_ROW_MAPPER, temporadaId);
        logger.debug("Fin obtenerPorTemporada: temporadaId={}, total={}", temporadaId, conceptos.size());
        return conceptos;
    }

    @Override
    public List<ConceptoPago> obtenerActivosPorTemporada(Long temporadaId) {
        logger.debug("Inicio obtenerActivosPorTemporada: temporadaId={}", temporadaId);

        String sql = """
                SELECT *
                FROM CONCEPTOS_PAGO
                WHERE TEMPORADA_ID = ?
                AND ACTIVO = 1
                ORDER BY NOMBRE
                """;

        List<ConceptoPago> conceptos = jdbcTemplate.query(sql, CONCEPTO_ROW_MAPPER, temporadaId);
        logger.debug("Fin obtenerActivosPorTemporada: temporadaId={}, total={}", temporadaId, conceptos.size());
        return conceptos;
    }

    @Override
    public List<ConceptoPago> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<ConceptoPago> conceptos = jdbcTemplate.query(
                "SELECT * FROM CONCEPTOS_PAGO ORDER BY TEMPORADA_ID DESC, NOMBRE",
                CONCEPTO_ROW_MAPPER
        );
        logger.debug("Fin obtenerTodos: total={}", conceptos.size());
        return conceptos;
    }

    @Override
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        int filasAfectadas = jdbcTemplate.update("DELETE FROM CONCEPTOS_PAGO WHERE ID = ?", id);
        logger.info("Concepto de pago eliminado: id={}, filasAfectadas={}", id, filasAfectadas);
        logger.debug("Fin eliminar: id={}", id);
    }

    private static ConceptoPago mapRow(ResultSet rs, int rowNum) throws SQLException {
        ConceptoPago concepto = new ConceptoPago();
        concepto.setId(rs.getLong("ID"));
        concepto.setTemporadaId(rs.getLong("TEMPORADA_ID"));
        concepto.setNombre(rs.getString("NOMBRE"));
        concepto.setDescripcion(rs.getString("DESCRIPCION"));
        concepto.setImporte(rs.getBigDecimal("IMPORTE"));
        concepto.setActivo(rs.getInt("ACTIVO"));
        return concepto;
    }
}