package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.ConceptoPagoDao;
import com.mikedev.mutxamelcf.model.ConceptoPago;
import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;

@Service
public class ConceptoPagoServiceImpl implements ConceptoPagoService {

    private static final Logger logger = LoggerFactory.getLogger(ConceptoPagoServiceImpl.class);

    private final ConceptoPagoDao conceptoPagoDao;

    public ConceptoPagoServiceImpl(ConceptoPagoDao conceptoPagoDao) {
        this.conceptoPagoDao = conceptoPagoDao;
    }

    @Override
    public boolean guardarConceptoPago(ConceptoPagoDTO concepto) {
        logger.debug("Inicio guardarConceptoPago: id={}", concepto == null ? null : concepto.getId());
        boolean resultado = conceptoPagoDao.guardarConceptoPago(toEntity(concepto));
        logger.debug("Fin guardarConceptoPago: resultado={}", resultado);
        return resultado;
    }

    @Override
    public ConceptoPagoDTO obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        ConceptoPagoDTO concepto = toDTO(conceptoPagoDao.obtenerPorId(id));
        logger.debug("Fin obtenerPorId: id={}, encontrado={}", id, concepto != null);
        return concepto;
    }

    @Override
    public List<ConceptoPagoDTO> obtenerPorTemporada(Long temporadaId) {
        logger.debug("Inicio obtenerPorTemporada: temporadaId={}", temporadaId);
        List<ConceptoPagoDTO> conceptos = toDTOList(conceptoPagoDao.obtenerPorTemporada(temporadaId));
        logger.debug("Fin obtenerPorTemporada: temporadaId={}, total={}", temporadaId, conceptos.size());
        return conceptos;
    }

    @Override
    public List<ConceptoPagoDTO> obtenerActivosPorTemporada(Long temporadaId) {
        logger.debug("Inicio obtenerActivosPorTemporada: temporadaId={}", temporadaId);
        List<ConceptoPagoDTO> conceptos = toDTOList(conceptoPagoDao.obtenerActivosPorTemporada(temporadaId));
        logger.debug("Fin obtenerActivosPorTemporada: temporadaId={}, total={}", temporadaId, conceptos.size());
        return conceptos;
    }

    @Override
    public List<ConceptoPagoDTO> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<ConceptoPagoDTO> conceptos = toDTOList(conceptoPagoDao.obtenerTodos());
        logger.debug("Fin obtenerTodos: total={}", conceptos.size());
        return conceptos;
    }

    @Override
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        conceptoPagoDao.eliminar(id);
        logger.debug("Fin eliminar: id={}", id);
    }

    private static ConceptoPago toEntity(ConceptoPagoDTO dto) {

        if (dto == null)
            return null;

        ConceptoPago concepto = new ConceptoPago();

        concepto.setId(dto.getId());
        concepto.setTemporadaId(dto.getTemporadaId());
        concepto.setNombre(dto.getNombre());
        concepto.setDescripcion(dto.getDescripcion());
        concepto.setImporte(dto.getImporte());
        concepto.setActivo(dto.getActivo());

        return concepto;
    }

    private static ConceptoPagoDTO toDTO(ConceptoPago concepto) {

        if (concepto == null)
            return null;

        ConceptoPagoDTO dto = new ConceptoPagoDTO();

        dto.setId(concepto.getId());
        dto.setTemporadaId(concepto.getTemporadaId());
        dto.setNombre(concepto.getNombre());
        dto.setDescripcion(concepto.getDescripcion());
        dto.setImporte(concepto.getImporte());
        dto.setActivo(concepto.getActivo());

        return dto;
    }

    private static List<ConceptoPagoDTO> toDTOList(List<ConceptoPago> lista) {

        List<ConceptoPagoDTO> resultado = new ArrayList<>();

        for (ConceptoPago concepto : lista) {
            resultado.add(toDTO(concepto));
        }

        return resultado;
    }
}