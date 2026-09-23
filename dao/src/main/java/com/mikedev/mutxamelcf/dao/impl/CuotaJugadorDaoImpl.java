package com.mikedev.mutxamelcf.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.CuotaJugadorDao;
import com.mikedev.mutxamelcf.model.CuotaJugador;

@Repository
public class CuotaJugadorDaoImpl implements CuotaJugadorDao {

    private static final Logger logger = LoggerFactory.getLogger(CuotaJugadorDaoImpl.class);

    private static final RowMapper<CuotaJugador> CUOTA_ROW_MAPPER = CuotaJugadorDaoImpl::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public CuotaJugadorDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean guardarCuota(CuotaJugador cuota) {
        logger.debug("Inicio guardarCuota: id={}, jugadorId={}", cuota.getId(), cuota.getJugadorId());
        boolean esActualizacion = cuota.getId() != null && obtenerPorId(cuota.getId()) != null;
        boolean resultado = esActualizacion ? actualizar(cuota) : insertar(cuota);
        logger.debug("Fin guardarCuota: esActualizacion={}, resultado={}", esActualizacion, resultado);
        return resultado;
    }

    private boolean insertar(CuotaJugador cuota) {
        logger.debug("Inicio insertar: jugadorId={}, conceptoPagoId={}", cuota.getJugadorId(), cuota.getConceptoPagoId());

        String sql = """
                INSERT INTO CUOTAS_JUGADOR
                (JUGADOR_ID, CONCEPTO_PAGO_ID, PERIODO, IMPORTE,
                 ESTADO, FECHA_LIMITE, OBSERVACIONES)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        boolean insertado = jdbcTemplate.update(
                sql,
                cuota.getJugadorId(),
                cuota.getConceptoPagoId(),
                cuota.getPeriodo(),
                cuota.getImporte(),
                cuota.getEstado(),
                cuota.getFechaLimite(),
                cuota.getObservaciones()
        ) == 1;
        logger.info("Cuota insertada: jugadorId={}, conceptoPagoId={}, insertada={}", cuota.getJugadorId(),
                cuota.getConceptoPagoId(), insertado);
        logger.debug("Fin insertar: insertado={}", insertado);
        return insertado;
    }

    private boolean actualizar(CuotaJugador cuota) {
        logger.debug("Inicio actualizar: id={}", cuota.getId());

        String sql = """
                UPDATE CUOTAS_JUGADOR
                SET JUGADOR_ID = ?,
                    CONCEPTO_PAGO_ID = ?,
                    PERIODO = ?,
                    IMPORTE = ?,
                    ESTADO = ?,
                    FECHA_LIMITE = ?,
                    OBSERVACIONES = ?
                WHERE ID = ?
                """;

        boolean actualizado = jdbcTemplate.update(
                sql,
                cuota.getJugadorId(),
                cuota.getConceptoPagoId(),
                cuota.getPeriodo(),
                cuota.getImporte(),
                cuota.getEstado(),
                cuota.getFechaLimite(),
                cuota.getObservaciones(),
                cuota.getId()
        ) == 1;
        logger.debug("Fin actualizar: id={}, actualizado={}", cuota.getId(), actualizado);
        return actualizado;
    }

    @Override
    public CuotaJugador obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        try {
            CuotaJugador cuota = jdbcTemplate.queryForObject("SELECT * FROM CUOTAS_JUGADOR WHERE ID = ?", CUOTA_ROW_MAPPER, id);
            logger.debug("Fin obtenerPorId: id={}, encontrado=true", id);
            return cuota;
        } catch (EmptyResultDataAccessException e) {
            logger.warn("No se encontro cuota con id={}", id);
            return null;
        }
    }

    @Override
    public List<CuotaJugador> obtenerPorJugador(Long jugadorId) {
        logger.debug("Inicio obtenerPorJugador: jugadorId={}", jugadorId);
        List<CuotaJugador> cuotas = jdbcTemplate.query(
                """
                SELECT *
                FROM CUOTAS_JUGADOR
                WHERE JUGADOR_ID = ?
                ORDER BY PERIODO, FECHA_LIMITE
                """,
                CUOTA_ROW_MAPPER, jugadorId
        );
        logger.debug("Fin obtenerPorJugador: jugadorId={}, total={}", jugadorId, cuotas.size());
        return cuotas;
    }

    @Override
    public List<CuotaJugador> obtenerPorEstado(String estado) {
        logger.debug("Inicio obtenerPorEstado: estado={}", estado);
        List<CuotaJugador> cuotas = jdbcTemplate.query(
                """
                SELECT *
                FROM CUOTAS_JUGADOR
                WHERE ESTADO = ?
                ORDER BY PERIODO, FECHA_LIMITE
                """,
                CUOTA_ROW_MAPPER, estado
        );
        logger.debug("Fin obtenerPorEstado: estado={}, total={}", estado, cuotas.size());
        return cuotas;
    }

    @Override
    public List<CuotaJugador> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<CuotaJugador> cuotas = jdbcTemplate.query(
                "SELECT * FROM CUOTAS_JUGADOR ORDER BY JUGADOR_ID, PERIODO, FECHA_LIMITE",
                CUOTA_ROW_MAPPER
        );
        logger.debug("Fin obtenerTodos: total={}", cuotas.size());
        return cuotas;
    }

    @Override
    public List<CuotaJugador> obtenerPorTemporada(Long temporadaId) {
        logger.debug("Inicio obtenerPorTemporada: temporadaId={}", temporadaId);
        List<CuotaJugador> cuotas = jdbcTemplate.query(
                """
                SELECT cuota.*
                FROM CUOTAS_JUGADOR cuota
                JOIN CONCEPTOS_PAGO concepto ON concepto.ID = cuota.CONCEPTO_PAGO_ID
                WHERE concepto.TEMPORADA_ID = ?
                ORDER BY cuota.JUGADOR_ID, cuota.PERIODO, cuota.FECHA_LIMITE
                """,
                CUOTA_ROW_MAPPER, temporadaId
        );
        logger.debug("Fin obtenerPorTemporada: temporadaId={}, total={}", temporadaId, cuotas.size());
        return cuotas;
    }

    @Override
    public void actualizarEstado(Long id) {
        logger.debug("Inicio actualizarEstado: id={}", id);
        jdbcTemplate.update("""
                UPDATE CUOTAS_JUGADOR cuota
                SET ESTADO = CASE
                    WHEN NVL((SELECT SUM(p.IMPORTE) FROM PAGOS p WHERE p.CUOTA_JUGADOR_ID = cuota.ID), 0) >= cuota.IMPORTE THEN 'PAGADO'
                    WHEN NVL((SELECT SUM(p.IMPORTE) FROM PAGOS p WHERE p.CUOTA_JUGADOR_ID = cuota.ID), 0) > 0 THEN 'PARCIAL'
                    ELSE 'PENDIENTE'
                END
                WHERE cuota.ID = ?
                """, id);
        logger.debug("Fin actualizarEstado: id={}");
    }

    @Override
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        if (id == null) {
            logger.warn("Se intento eliminar una cuota con id nulo");
            return;
        }
        jdbcTemplate.update("DELETE FROM PAGOS WHERE CUOTA_JUGADOR_ID = ?", id);
        int filasAfectadas = jdbcTemplate.update("DELETE FROM CUOTAS_JUGADOR WHERE ID = ?", id);
        logger.info("Cuota eliminada: id={}, filasAfectadas={}", id, filasAfectadas);
        logger.debug("Fin eliminar: id={}", id);
    }

    @Override
    public void eliminarEnLote(List<Long> ids) {
        logger.debug("Inicio eliminarEnLote: ids={}", ids);
        if (ids == null || ids.isEmpty()) {
            logger.debug("Fin eliminarEnLote: sin ids");
            return;
        }
        List<Long> idsValidos = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (idsValidos.isEmpty()) {
            logger.debug("Fin eliminarEnLote: ids validos vacios");
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(idsValidos.size(), "?"));
        jdbcTemplate.update("DELETE FROM PAGOS WHERE CUOTA_JUGADOR_ID IN (" + placeholders + ")", idsValidos.toArray());
        int filasAfectadas = jdbcTemplate.update("DELETE FROM CUOTAS_JUGADOR WHERE ID IN (" + placeholders + ")", idsValidos.toArray());
        logger.info("Cuotas eliminadas en lote: total={}, filasAfectadas={}", idsValidos.size(), filasAfectadas);
        logger.debug("Fin eliminarEnLote: ids={}, filasAfectadas={}", idsValidos.size(), filasAfectadas);
    }

    private static CuotaJugador mapRow(ResultSet rs, int rowNum) throws SQLException {
        CuotaJugador cuota = new CuotaJugador();
        cuota.setId(rs.getLong("ID"));
        cuota.setJugadorId(rs.getLong("JUGADOR_ID"));
        cuota.setConceptoPagoId(rs.getLong("CONCEPTO_PAGO_ID"));
        cuota.setPeriodo(rs.getString("PERIODO"));
        cuota.setImporte(rs.getBigDecimal("IMPORTE"));
        cuota.setEstado(rs.getString("ESTADO"));
        cuota.setFechaLimite(rs.getDate("FECHA_LIMITE"));
        cuota.setObservaciones(rs.getString("OBSERVACIONES"));
        return cuota;
    }
}