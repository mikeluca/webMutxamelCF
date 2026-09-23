package com.mikedev.mutxamelcf.service;

import java.util.List;

import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;

public interface ConceptoPagoService {

    boolean guardarConceptoPago(ConceptoPagoDTO concepto);

    ConceptoPagoDTO obtenerPorId(Long id);

    List<ConceptoPagoDTO> obtenerPorTemporada(Long temporadaId);

    List<ConceptoPagoDTO> obtenerActivosPorTemporada(Long temporadaId);

    List<ConceptoPagoDTO> obtenerTodos();

    void eliminar(Long id);
}