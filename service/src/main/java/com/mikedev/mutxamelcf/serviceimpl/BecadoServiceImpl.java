package com.mikedev.mutxamelcf.serviceimpl;

import java.math.BigDecimal;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.service.BecadoService;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.PagoService;

@Service
public class BecadoServiceImpl implements BecadoService {

    private static final Logger logger = LoggerFactory.getLogger(BecadoServiceImpl.class);

    private final CuotaJugadorService cuotaJugadorService;
    private final PagoService pagoService;

    public BecadoServiceImpl(CuotaJugadorService cuotaJugadorService, PagoService pagoService) {
        this.cuotaJugadorService = cuotaJugadorService;
        this.pagoService = pagoService;
    }

    @Override
    @Transactional
    public int marcarCuotasComoBecadas(Long jugadorId) {
        logger.debug("Inicio marcarCuotasComoBecadas: jugadorId={}", jugadorId);
        if (jugadorId == null) {
            throw new IllegalArgumentException("El jugador es obligatorio.");
        }

        int cuotasPagadas = 0;
        for (CuotaJugadorDTO cuota : cuotaJugadorService.obtenerPorJugador(jugadorId)) {
            BigDecimal importe = cuota.getImporte() == null ? BigDecimal.ZERO : cuota.getImporte();
            BigDecimal pagado = pagoService.obtenerTotalPagado(cuota.getId());
            BigDecimal pendiente = importe.subtract(pagado == null ? BigDecimal.ZERO : pagado).max(BigDecimal.ZERO);
            if (pendiente.signum() == 0) {
                continue;
            }

            PagoDTO pago = new PagoDTO();
            pago.setCuotaJugadorId(cuota.getId());
            pago.setImporte(pendiente);
            pago.setFechaPago(new Date());
            pago.setMetodoPago("BECADO");
            pago.setReferencia("Becado");
            pago.setObservaciones("Cuota marcada como pagada por beca.");
            if (!pagoService.guardarPago(pago)) {
                throw new IllegalStateException("No se ha podido registrar una de las cuotas becadas.");
            }
            cuotaJugadorService.actualizarEstado(cuota.getId());
            cuotasPagadas++;
        }

        logger.info("Cuotas marcadas como becadas: jugadorId={}, total={}", jugadorId, cuotasPagadas);
        logger.debug("Fin marcarCuotasComoBecadas: jugadorId={}, cuotasPagadas={}", jugadorId, cuotasPagadas);
        return cuotasPagadas;
    }
}
