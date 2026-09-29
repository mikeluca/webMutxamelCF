package com.mikedev.mutxamelcf.dao.impl;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.mikedev.mutxamelcf.dao.PagoDao;
import com.mikedev.mutxamelcf.model.Pago;

@Repository
public class PagoDaoImpl implements PagoDao {

    private static final Logger logger = LoggerFactory.getLogger(PagoDaoImpl.class);

    private static final RowMapper<Pago> PAGO_ROW_MAPPER = PagoDaoImpl::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public PagoDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean guardarPago(Pago pago) {
        logger.debug("Inicio guardarPago: id={}, cuotaJugadorId={}", pago.getId(), pago.getCuotaJugadorId());

        if (pago.getId() != null && obtenerPorId(pago.getId()) != null) {
            boolean actualizado = actualizar(pago);
            logger.debug("Fin guardarPago: esActualizacion=true, resultado={}", actualizado);
            return actualizado;
        }

        boolean insertado = insertar(pago);
        logger.debug("Fin guardarPago: esActualizacion=false, resultado={}", insertado);
        return insertado;
    }

    private boolean insertar(Pago pago) {
        logger.debug("Inicio insertar: cuotaJugadorId={}", pago.getCuotaJugadorId());

        String sql = """
                INSERT INTO PAGOS
                (CUOTA_JUGADOR_ID, IMPORTE, FECHA_PAGO,
                 METODO_PAGO, REFERENCIA, OBSERVACIONES)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        // BE-01: se usa KeyHolder para obtener el id generado por esta
        // misma insercion. Antes se recuperaba con "el ultimo pago de la
        // cuota" (ORDER BY ID DESC), que con inserciones concurrentes
        // podia devolver el id del pago de OTRA peticion.
        KeyHolder keyHolder = new GeneratedKeyHolder();

        boolean insertado = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setObject(1, pago.getCuotaJugadorId());
            ps.setBigDecimal(2, pago.getImporte());
            if (pago.getFechaPago() != null) {
                ps.setTimestamp(3, new java.sql.Timestamp(pago.getFechaPago().getTime()));
            } else {
                ps.setNull(3, java.sql.Types.DATE);
            }
            ps.setString(4, pago.getMetodoPago());
            ps.setString(5, pago.getReferencia());
            ps.setString(6, pago.getObservaciones());
            return ps;
        }, keyHolder) == 1;

        if (insertado) {
            Number key = keyHolder.getKey();
            if (key != null) {
                pago.setId(key.longValue());
            }
            logger.info("Pago insertado: idGenerado={}, cuotaJugadorId={}", pago.getId(), pago.getCuotaJugadorId());
        }

        logger.debug("Fin insertar: insertado={}", insertado);
        return insertado;
    }

    private boolean actualizar(Pago pago) {
        logger.debug("Inicio actualizar: id={}", pago.getId());

        String sql = """
                UPDATE PAGOS
                SET CUOTA_JUGADOR_ID = ?,
                    IMPORTE = ?,
                    FECHA_PAGO = ?,
                    METODO_PAGO = ?,
                    REFERENCIA = ?,
                    OBSERVACIONES = ?
                WHERE ID = ?
                """;

        boolean actualizado = jdbcTemplate.update(
                sql,
                pago.getCuotaJugadorId(),
                pago.getImporte(),
                pago.getFechaPago(),
                pago.getMetodoPago(),
                pago.getReferencia(),
                pago.getObservaciones(),
                pago.getId()
        ) == 1;
        logger.debug("Fin actualizar: id={}, actualizado={}", pago.getId(), actualizado);
        return actualizado;
    }

    @Override
    public Pago obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        try {
            Pago pago = jdbcTemplate.queryForObject("SELECT * FROM PAGOS WHERE ID = ?", PAGO_ROW_MAPPER, id);
            logger.debug("Fin obtenerPorId: id={}, encontrado=true", id);
            return pago;
        } catch (EmptyResultDataAccessException e) {
            logger.warn("No se encontro pago con id={}", id);
            return null;
        }
    }

    @Override
    public List<Pago> obtenerPorCuota(Long cuotaJugadorId) {
        logger.debug("Inicio obtenerPorCuota: cuotaJugadorId={}", cuotaJugadorId);

        String sql = """
                SELECT *
                FROM PAGOS
                WHERE CUOTA_JUGADOR_ID = ?
                ORDER BY FECHA_PAGO DESC
                """;

        List<Pago> pagos = jdbcTemplate.query(sql, PAGO_ROW_MAPPER, cuotaJugadorId);
        logger.debug("Fin obtenerPorCuota: cuotaJugadorId={}, total={}", cuotaJugadorId, pagos.size());
        return pagos;
    }

    // Oracle limita las clausulas IN a 1000 elementos, de ahi el troceado
    private static final int TAMANO_LOTE_IN = 900;

    @Override
    public List<Pago> obtenerPorCuotas(List<Long> cuotaJugadorIds) {
        logger.debug("Inicio obtenerPorCuotas: total={}", cuotaJugadorIds == null ? 0 : cuotaJugadorIds.size());

        if (cuotaJugadorIds == null || cuotaJugadorIds.isEmpty()) {
            logger.debug("Fin obtenerPorCuotas: sin ids, total=0");
            return List.of();
        }

        List<Pago> resultado = new ArrayList<>();
        for (int inicio = 0; inicio < cuotaJugadorIds.size(); inicio += TAMANO_LOTE_IN) {
            List<Long> lote = cuotaJugadorIds.subList(inicio, Math.min(inicio + TAMANO_LOTE_IN, cuotaJugadorIds.size()));
            String placeholders = lote.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT * FROM PAGOS WHERE CUOTA_JUGADOR_ID IN (" + placeholders
                    + ") ORDER BY CUOTA_JUGADOR_ID, FECHA_PAGO DESC";
            resultado.addAll(jdbcTemplate.query(sql, PAGO_ROW_MAPPER, lote.toArray()));
        }

        logger.debug("Fin obtenerPorCuotas: total={}", resultado.size());
        return resultado;
    }

    @Override
    public BigDecimal obtenerTotalPagado(Long cuotaJugadorId) {
        logger.debug("Inicio obtenerTotalPagado: cuotaJugadorId={}", cuotaJugadorId);

        String sql = """
                SELECT NVL(SUM(IMPORTE), 0)
                FROM PAGOS
                WHERE CUOTA_JUGADOR_ID = ?
                """;

        BigDecimal total = jdbcTemplate.queryForObject(sql, BigDecimal.class, cuotaJugadorId);
        BigDecimal resultado = total != null ? total : BigDecimal.ZERO;
        logger.debug("Fin obtenerTotalPagado: cuotaJugadorId={}, total={}", cuotaJugadorId, resultado);
        return resultado;
    }

    @Override
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        int filasAfectadas = jdbcTemplate.update("DELETE FROM PAGOS WHERE ID = ?", id);
        logger.info("Pago eliminado: id={}, filasAfectadas={}", id, filasAfectadas);
        logger.debug("Fin eliminar: id={}", id);
    }

    private static Pago mapRow(ResultSet rs, int rowNum) throws SQLException {
        Pago pago = new Pago();
        pago.setId(rs.getLong("ID"));
        pago.setCuotaJugadorId(rs.getLong("CUOTA_JUGADOR_ID"));
        pago.setImporte(rs.getBigDecimal("IMPORTE"));
        pago.setFechaPago(rs.getDate("FECHA_PAGO"));
        pago.setMetodoPago(rs.getString("METODO_PAGO"));
        pago.setReferencia(rs.getString("REFERENCIA"));
        pago.setObservaciones(rs.getString("OBSERVACIONES"));
        return pago;
    }
}