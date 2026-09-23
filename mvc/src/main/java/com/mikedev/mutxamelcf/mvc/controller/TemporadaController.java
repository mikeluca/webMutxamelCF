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

import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.TemporadaService;

/**
 * Endpoints de administración de temporadas.
 *
 * <p>El controlador delega las operaciones en {@link TemporadaService}; de
 * esta forma el acceso al DAO permanece encapsulado en la capa de servicio.</p>
 */
@RestController
@RequestMapping("/admin/temporadas")
public class TemporadaController {

    private static final Logger logger = LoggerFactory.getLogger(TemporadaController.class);

    private final TemporadaService temporadaService;

    public TemporadaController(TemporadaService temporadaService) {
        this.temporadaService = temporadaService;
    }

    /**
     * Guarda una temporada nueva o actualiza una existente cuando el DTO
     * contiene un identificador.
     */
    @PostMapping({"", "/guardar"})
    public ResponseEntity<Boolean> guardarTemporada(@RequestBody TemporadaDTO temporada) {
        logger.debug("Inicio guardarTemporada: id={}, nombre={}", temporada == null ? null : temporada.getId(),
                temporada == null ? null : temporada.getNombre());
        boolean guardada = temporadaService.guardarTemporada(temporada);
        logger.debug("Fin guardarTemporada: resultado={}", guardada);
        return guardada ? ResponseEntity.ok(true) : ResponseEntity.badRequest().body(false);
    }

    /** Obtiene una temporada por su identificador. */
    @GetMapping("/{id}")
    public ResponseEntity<TemporadaDTO> obtenerPorId(@PathVariable Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        TemporadaDTO temporada = temporadaService.obtenerPorId(id);
        logger.debug("Fin obtenerPorId: id={}, encontrada={}", id, temporada != null);
        return temporada == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(temporada);
    }

    /** Obtiene la temporada marcada como activa. */
    @GetMapping("/activa")
    public ResponseEntity<TemporadaDTO> obtenerTemporadaActiva() {
        logger.debug("Inicio obtenerTemporadaActiva");
        TemporadaDTO temporada = temporadaService.obtenerTemporadaActiva();
        logger.debug("Fin obtenerTemporadaActiva: encontrada={}", temporada != null);
        return temporada == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(temporada);
    }

    /** Obtiene todas las temporadas ordenadas por la capa DAO. */
    @GetMapping
    public ResponseEntity<List<TemporadaDTO>> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<TemporadaDTO> temporadas = temporadaService.obtenerTodos();
        logger.debug("Fin obtenerTodos: total={}", temporadas.size());
        return ResponseEntity.ok(temporadas);
    }

    /** Elimina una temporada por su identificador. */
    @DeleteMapping({"/{id}", "/borrar/{id}"})
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        temporadaService.eliminar(id);
        logger.debug("Fin eliminar: id={}", id);
        return ResponseEntity.noContent().build();
    }
}
