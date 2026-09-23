package com.mikedev.mutxamelcf.mvc.controller;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.PagoService;

@RestController
@RequestMapping("/admin/pagos/api")
public class PagoController {

    private static final Logger logger = LoggerFactory.getLogger(PagoController.class);

    private final PagoService pagoService;
    private final CuotaJugadorService cuotaJugadorService;

    public PagoController(PagoService pagoService, CuotaJugadorService cuotaJugadorService) {
        this.pagoService = pagoService;
        this.cuotaJugadorService = cuotaJugadorService;
    }

    @PostMapping
    public ResponseEntity<Boolean> guardar(@RequestBody PagoDTO pago) {
        logger.debug("Inicio guardar: id={}, cuotaJugadorId={}", pago == null ? null : pago.getId(),
                pago == null ? null : pago.getCuotaJugadorId());
        boolean guardado = pagoService.guardarPago(pago);
        if (guardado) {
            cuotaJugadorService.actualizarEstado(pago.getCuotaJugadorId());
        }
        logger.debug("Fin guardar: resultado={}", guardado);
        return guardado ? ResponseEntity.ok(true) : ResponseEntity.badRequest().body(false);
    }

    @GetMapping("/cuota/{cuotaJugadorId}")
    public List<PagoDTO> obtenerPorCuota(@PathVariable Long cuotaJugadorId) {
        logger.debug("Inicio obtenerPorCuota: cuotaJugadorId={}", cuotaJugadorId);
        List<PagoDTO> pagos = pagoService.obtenerPorCuota(cuotaJugadorId);
        logger.debug("Fin obtenerPorCuota: cuotaJugadorId={}, total={}", cuotaJugadorId, pagos.size());
        return pagos;
    }

    @GetMapping("/cuota/{cuotaJugadorId}/total")
    public BigDecimal obtenerTotal(@PathVariable Long cuotaJugadorId) {
        logger.debug("Inicio obtenerTotal: cuotaJugadorId={}", cuotaJugadorId);
        BigDecimal total = pagoService.obtenerTotalPagado(cuotaJugadorId);
        logger.debug("Fin obtenerTotal: cuotaJugadorId={}, total={}", cuotaJugadorId, total);
        return total;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        PagoDTO pago = pagoService.obtenerPorId(id);
        pagoService.eliminar(id);
        if (pago != null) {
            cuotaJugadorService.actualizarEstado(pago.getCuotaJugadorId());
        }
        logger.debug("Fin eliminar: id={}", id);
        return ResponseEntity.noContent().build();
    }
}