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

import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;

@RestController
@RequestMapping("/admin/conceptos-pago")
public class ConceptoPagoController {

    private static final Logger logger = LoggerFactory.getLogger(ConceptoPagoController.class);

    private final ConceptoPagoService conceptoPagoService;

    public ConceptoPagoController(ConceptoPagoService conceptoPagoService) {
        this.conceptoPagoService = conceptoPagoService;
    }

    @PostMapping
    public ResponseEntity<Boolean> guardar(@RequestBody ConceptoPagoDTO concepto) {
        logger.debug("Inicio guardar: id={}", concepto == null ? null : concepto.getId());
        boolean guardado = conceptoPagoService.guardarConceptoPago(concepto);
        logger.debug("Fin guardar: resultado={}", guardado);
        return guardado ? ResponseEntity.ok(true) : ResponseEntity.badRequest().body(false);
    }

    @GetMapping
    public List<ConceptoPagoDTO> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<ConceptoPagoDTO> conceptos = conceptoPagoService.obtenerTodos();
        logger.debug("Fin obtenerTodos: total={}", conceptos.size());
        return conceptos;
    }

    @GetMapping("/temporada/{temporadaId}")
    public List<ConceptoPagoDTO> obtenerPorTemporada(@PathVariable Long temporadaId) {
        logger.debug("Inicio obtenerPorTemporada: temporadaId={}", temporadaId);
        List<ConceptoPagoDTO> conceptos = conceptoPagoService.obtenerPorTemporada(temporadaId);
        logger.debug("Fin obtenerPorTemporada: temporadaId={}, total={}", temporadaId, conceptos.size());
        return conceptos;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConceptoPagoDTO> obtenerPorId(@PathVariable Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        ConceptoPagoDTO concepto = conceptoPagoService.obtenerPorId(id);
        logger.debug("Fin obtenerPorId: id={}, encontrado={}", id, concepto != null);
        return concepto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(concepto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        conceptoPagoService.eliminar(id);
        logger.debug("Fin eliminar: id={}", id);
        return ResponseEntity.noContent().build();
    }
}