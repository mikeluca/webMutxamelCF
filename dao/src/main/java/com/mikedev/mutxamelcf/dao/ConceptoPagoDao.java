package com.mikedev.mutxamelcf.dao;

import java.util.List;

import com.mikedev.mutxamelcf.model.ConceptoPago;

public interface ConceptoPagoDao {

    boolean guardarConceptoPago(ConceptoPago concepto);

    ConceptoPago obtenerPorId(Long id);

    List<ConceptoPago> obtenerPorTemporada(Long temporadaId);

    List<ConceptoPago> obtenerActivosPorTemporada(Long temporadaId);

    List<ConceptoPago> obtenerTodos();

    void eliminar(Long id);
}