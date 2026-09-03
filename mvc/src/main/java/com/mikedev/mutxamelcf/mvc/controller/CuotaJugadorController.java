package com.mikedev.mutxamelcf.mvc.controller;

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

import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;

@RestController
@RequestMapping("/admin/cuotas")
public class CuotaJugadorController {

    private static final Logger logger = LoggerFactory.getLogger(CuotaJugadorController.class);

    private final CuotaJugadorService cuotaJugadorService;

    public CuotaJugadorController(CuotaJugadorService cuotaJugadorService) {
        this.cuotaJugadorService = cuotaJugadorService;
    }

    @PostMapping
    public ResponseEntity<Boolean> guardar(@RequestBody CuotaJugadorDTO cuota) {
        logger.debug("Inicio guardar: id={}, jugadorId={}", cuota == null ? null : cuota.getId(),
                cuota == null ? null : cuota.getJugadorId());
        boolean guardado = cuotaJugadorService.guardarCuota(cuota);
        logger.debug("Fin guardar: resultado={}", guardado);
        return guardado ? ResponseEntity.ok(true) : ResponseEntity.badRequest().body(false);
    }

    @GetMapping
    public List<CuotaJugadorDTO> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<CuotaJugadorDTO> cuotas = cuotaJugadorService.obtenerTodos();
        logger.debug("Fin obtenerTodos: total={}", cuotas.size());
        return cuotas;
    }

    @GetMapping("/{id}")
    public ResponseEntity<CuotaJugadorDTO> obtenerPorId(@PathVariable Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        CuotaJugadorDTO cuota = cuotaJugadorService.obtenerPorId(id);
        logger.debug("Fin obtenerPorId: id={}, encontrada={}", id, cuota != null);
        return cuota == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(cuota);
    }

    @GetMapping("/jugador/{jugadorId}")
    public List<CuotaJugadorDTO> obtenerPorJugador(@PathVariable Long jugadorId) {
        logger.debug("Inicio obtenerPorJugador: jugadorId={}", jugadorId);
        List<CuotaJugadorDTO> cuotas = cuotaJugadorService.obtenerPorJugador(jugadorId);
        logger.debug("Fin obtenerPorJugador: jugadorId={}, total={}", jugadorId, cuotas.size());
        return cuotas;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        cuotaJugadorService.eliminar(id);
        logger.debug("Fin eliminar: id={}", id);
        return ResponseEntity.noContent().build();
    }
}