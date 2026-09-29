package com.mikedev.mutxamelcf.serviceimpl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.CuotaJugadorDao;
import com.mikedev.mutxamelcf.dao.PagoDao;
import com.mikedev.mutxamelcf.model.CuotaJugador;
import com.mikedev.mutxamelcf.model.Pago;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.service.PagoService;

@Service
public class PagoServiceImpl implements PagoService {

    private static final Logger logger = LoggerFactory.getLogger(PagoServiceImpl.class);

    private static final String ERROR_CUOTA_INEXISTENTE = "El importe supera el saldo pendiente o la cuota no existe.";

    private final PagoDao pagoDao;
    private final CuotaJugadorDao cuotaJugadorDao;

    public PagoServiceImpl(PagoDao pagoDao, CuotaJugadorDao cuotaJugadorDao) {
        this.pagoDao = pagoDao;
        this.cuotaJugadorDao = cuotaJugadorDao;
    }

    @Override
    @Transactional
    public PagoDTO registrarPago(PagoDTO datos) {
        logger.debug("Inicio registrarPago: id={}, cuotaJugadorId={}",
                datos == null ? null : datos.getId(),
                datos == null ? null : datos.getCuotaJugadorId());

        if (datos == null || datos.getCuotaJugadorId() == null) {
            throw new IllegalArgumentException(ERROR_CUOTA_INEXISTENTE);
        }

        // SELECT ... FOR UPDATE: serializa cualquier otro registrarPago()
        // concurrente sobre la misma cuota hasta que esta transaccion
        // termine, para que el calculo de "pendiente" de abajo sea fiable.
        CuotaJugador cuota = cuotaJugadorDao.bloquearPorId(datos.getCuotaJugadorId());
        if (cuota == null) {
            logger.warn("registrarPago rechazado: la cuota no existe, cuotaJugadorId={}", datos.getCuotaJugadorId());
            throw new IllegalArgumentException(ERROR_CUOTA_INEXISTENTE);
        }

        Pago pagoExistente = datos.getId() == null ? null : pagoDao.obtenerPorId(datos.getId());
        if (datos.getId() != null
                && (pagoExistente == null || !cuota.getId().equals(pagoExistente.getCuotaJugadorId()))) {
            logger.warn("registrarPago rechazado: el pago no existe o no pertenece a la cuota, id={}, cuotaJugadorId={}",
                    datos.getId(), datos.getCuotaJugadorId());
            throw new IllegalArgumentException("El importe supera el saldo pendiente o el pago no existe.");
        }

        BigDecimal pagadoActual = pagoDao.obtenerTotalPagado(cuota.getId());
        if (pagoExistente != null) {
            pagadoActual = pagadoActual.subtract(zeroIfNull(pagoExistente.getImporte()));
        }
        BigDecimal pendiente = zeroIfNull(cuota.getImporte()).subtract(pagadoActual);

        BigDecimal importe = datos.getImporte();
        if (importe == null || importe.signum() <= 0 || importe.compareTo(pendiente) > 0) {
            logger.warn("registrarPago rechazado por importe invalido: cuotaJugadorId={}, importe={}, pendiente={}",
                    cuota.getId(), importe, pendiente);
            throw new IllegalArgumentException(ERROR_CUOTA_INEXISTENTE);
        }

        boolean guardado = guardarPago(datos);
        if (!guardado) {
            throw new IllegalStateException("No se ha podido guardar el pago.");
        }

        cuotaJugadorDao.actualizarEstado(cuota.getId());

        logger.debug("Fin registrarPago: id={}, cuotaJugadorId={}", datos.getId(), cuota.getId());
        return datos;
    }

    private static BigDecimal zeroIfNull(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    @Override
    public boolean guardarPago(PagoDTO pago) {
        logger.debug("Inicio guardarPago: id={}, cuotaJugadorId={}", pago == null ? null : pago.getId(),
                pago == null ? null : pago.getCuotaJugadorId());
        Pago entidad = toEntity(pago);
        boolean guardado = pagoDao.guardarPago(entidad);
        if (guardado && pago != null && entidad != null) {
            // El DAO genera el id en el insert; se propaga al DTO para que el llamante lo
            // conozca
            pago.setId(entidad.getId());
            logger.info("Id de pago generado por la base de datos propagado al DTO: id={}", entidad.getId());
        }
        logger.debug("Fin guardarPago: resultado={}", guardado);
        return guardado;
    }

    @Override
    public PagoDTO obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        PagoDTO pago = toDTO(pagoDao.obtenerPorId(id));
        logger.debug("Fin obtenerPorId: id={}, encontrado={}", id, pago != null);
        return pago;
    }

    @Override
    public List<PagoDTO> obtenerPorCuota(Long cuotaJugadorId) {
        logger.debug("Inicio obtenerPorCuota: cuotaJugadorId={}", cuotaJugadorId);
        List<PagoDTO> pagos = toDTOList(pagoDao.obtenerPorCuota(cuotaJugadorId));
        logger.debug("Fin obtenerPorCuota: cuotaJugadorId={}, total={}", cuotaJugadorId, pagos.size());
        return pagos;
    }

    @Override
    public List<PagoDTO> obtenerPorCuotas(List<Long> cuotaJugadorIds) {
        logger.debug("Inicio obtenerPorCuotas: total={}", cuotaJugadorIds == null ? 0 : cuotaJugadorIds.size());
        List<PagoDTO> pagos = toDTOList(pagoDao.obtenerPorCuotas(cuotaJugadorIds));
        logger.debug("Fin obtenerPorCuotas: total={}", pagos.size());
        return pagos;
    }

    @Override
    public BigDecimal obtenerTotalPagado(Long cuotaJugadorId) {
        logger.debug("Inicio obtenerTotalPagado: cuotaJugadorId={}", cuotaJugadorId);
        BigDecimal total = pagoDao.obtenerTotalPagado(cuotaJugadorId);
        logger.debug("Fin obtenerTotalPagado: cuotaJugadorId={}, total={}", cuotaJugadorId, total);
        return total;
    }

    @Override
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        pagoDao.eliminar(id);
        logger.debug("Fin eliminar: id={}", id);
    }

    private static Pago toEntity(PagoDTO dto) {

        if (dto == null)
            return null;

        Pago pago = new Pago();

        pago.setId(dto.getId());
        pago.setCuotaJugadorId(dto.getCuotaJugadorId());
        pago.setImporte(dto.getImporte());
        pago.setFechaPago(dto.getFechaPago());
        pago.setMetodoPago(dto.getMetodoPago());
        pago.setReferencia(dto.getReferencia());
        pago.setObservaciones(dto.getObservaciones());

        return pago;
    }

    private static PagoDTO toDTO(Pago pago) {

        if (pago == null)
            return null;

        PagoDTO dto = new PagoDTO();

        dto.setId(pago.getId());
        dto.setCuotaJugadorId(pago.getCuotaJugadorId());
        dto.setImporte(pago.getImporte());
        dto.setFechaPago(pago.getFechaPago());
        dto.setMetodoPago(pago.getMetodoPago());
        dto.setReferencia(pago.getReferencia());
        dto.setObservaciones(pago.getObservaciones());

        return dto;
    }

    private static List<PagoDTO> toDTOList(List<Pago> lista) {

        List<PagoDTO> resultado = new ArrayList<>();

        for (Pago pago : lista) {
            resultado.add(toDTO(pago));
        }

        return resultado;
    }
}