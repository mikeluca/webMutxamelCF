package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.CuotaJugadorDao;
import com.mikedev.mutxamelcf.model.CuotaJugador;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;

@Service
public class CuotaJugadorServiceImpl implements CuotaJugadorService {

    private static final Logger logger = LoggerFactory.getLogger(CuotaJugadorServiceImpl.class);

    private final CuotaJugadorDao cuotaJugadorDao;

    public CuotaJugadorServiceImpl(CuotaJugadorDao cuotaJugadorDao) {
        this.cuotaJugadorDao = cuotaJugadorDao;
    }

    @Override
    public boolean guardarCuota(CuotaJugadorDTO cuota) {
        logger.debug("Inicio guardarCuota: id={}, jugadorId={}", cuota == null ? null : cuota.getId(),
                cuota == null ? null : cuota.getJugadorId());
        boolean resultado = cuotaJugadorDao.guardarCuota(toEntity(cuota));
        logger.debug("Fin guardarCuota: resultado={}", resultado);
        return resultado;
    }

    @Override
    public CuotaJugadorDTO obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        CuotaJugadorDTO cuota = toDTO(cuotaJugadorDao.obtenerPorId(id));
        logger.debug("Fin obtenerPorId: id={}, encontrada={}", id, cuota != null);
        return cuota;
    }

    @Override
    public List<CuotaJugadorDTO> obtenerPorJugador(Long jugadorId) {
        logger.debug("Inicio obtenerPorJugador: jugadorId={}", jugadorId);
        List<CuotaJugadorDTO> cuotas = toDTOList(cuotaJugadorDao.obtenerPorJugador(jugadorId));
        logger.debug("Fin obtenerPorJugador: jugadorId={}, total={}", jugadorId, cuotas.size());
        return cuotas;
    }

    @Override
    public List<CuotaJugadorDTO> obtenerPorEstado(String estado) {
        logger.debug("Inicio obtenerPorEstado: estado={}", estado);
        List<CuotaJugadorDTO> cuotas = toDTOList(cuotaJugadorDao.obtenerPorEstado(estado));
        logger.debug("Fin obtenerPorEstado: estado={}, total={}", estado, cuotas.size());
        return cuotas;
    }

    @Override
    public List<CuotaJugadorDTO> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<CuotaJugadorDTO> cuotas = toDTOList(cuotaJugadorDao.obtenerTodos());
        logger.debug("Fin obtenerTodos: total={}", cuotas.size());
        return cuotas;
    }

    @Override
    public List<CuotaJugadorDTO> obtenerPorTemporada(Long temporadaId) {
        logger.debug("Inicio obtenerPorTemporada: temporadaId={}", temporadaId);
        List<CuotaJugadorDTO> cuotas = toDTOList(cuotaJugadorDao.obtenerPorTemporada(temporadaId));
        logger.debug("Fin obtenerPorTemporada: temporadaId={}, total={}", temporadaId, cuotas.size());
        return cuotas;
    }

    @Override
    public void actualizarEstado(Long id) {
        logger.debug("Inicio actualizarEstado: id={}", id);
        cuotaJugadorDao.actualizarEstado(id);
        logger.debug("Fin actualizarEstado: id={}", id);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        cuotaJugadorDao.eliminar(id);
        logger.debug("Fin eliminar: id={}", id);
    }

    @Override
    @Transactional
    public void eliminarEnLote(List<Long> ids) {
        logger.debug("Inicio eliminarEnLote: ids={}", ids);
        cuotaJugadorDao.eliminarEnLote(ids);
        logger.debug("Fin eliminarEnLote: ids={}", ids);
    }

    private static CuotaJugador toEntity(CuotaJugadorDTO dto) {

        if (dto == null)
            return null;

        CuotaJugador cuota = new CuotaJugador();

        cuota.setId(dto.getId());
        cuota.setJugadorId(dto.getJugadorId());
        cuota.setConceptoPagoId(dto.getConceptoPagoId());
        cuota.setPeriodo(dto.getPeriodo());
        cuota.setImporte(dto.getImporte());
        cuota.setEstado(dto.getEstado());
        cuota.setFechaLimite(dto.getFechaLimite());
        cuota.setObservaciones(dto.getObservaciones());

        return cuota;
    }

    private static CuotaJugadorDTO toDTO(CuotaJugador cuota) {

        if (cuota == null)
            return null;

        CuotaJugadorDTO dto = new CuotaJugadorDTO();

        dto.setId(cuota.getId());
        dto.setJugadorId(cuota.getJugadorId());
        dto.setConceptoPagoId(cuota.getConceptoPagoId());
        dto.setPeriodo(cuota.getPeriodo());
        dto.setImporte(cuota.getImporte());
        dto.setEstado(cuota.getEstado());
        dto.setFechaLimite(cuota.getFechaLimite());
        dto.setObservaciones(cuota.getObservaciones());

        return dto;
    }

    private static List<CuotaJugadorDTO> toDTOList(List<CuotaJugador> lista) {

        List<CuotaJugadorDTO> resultado = new ArrayList<>();

        for (CuotaJugador cuota : lista) {
            resultado.add(toDTO(cuota));
        }

        return resultado;
    }
}