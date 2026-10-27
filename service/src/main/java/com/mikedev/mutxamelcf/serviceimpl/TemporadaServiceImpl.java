package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.BorradorPlantillasException;
import com.mikedev.mutxamelcf.model.BorradorPlantillasException;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.TemporadaService;

@Service
public class TemporadaServiceImpl implements TemporadaService {

    private static final Logger logger = LoggerFactory.getLogger(TemporadaServiceImpl.class);

    private final TemporadaDao temporadaDao;

    static final String MENSAJE_HISTORIAL = "Esta temporada tiene historial de plantillas y no se puede borrar";

    private final JugadorTemporadaDao jugadorTemporadaDao;

    public TemporadaServiceImpl(TemporadaDao temporadaDao, JugadorTemporadaDao jugadorTemporadaDao) {
        this.jugadorTemporadaDao = jugadorTemporadaDao;
        this.temporadaDao = temporadaDao;
    }

    @Override
    @Transactional
    public boolean guardarTemporada(TemporadaDTO temporada) {
        logger.debug("Inicio guardarTemporada: id={}, nombre={}", temporada == null ? null : temporada.getId(),
                temporada == null ? null : temporada.getNombre());
        Temporada entidad = toEntity(temporada);
        if (entidad != null && Integer.valueOf(1).equals(entidad.getActiva())) {
            // Regla de negocio: solo puede haber una temporada activa a la vez
            logger.info("Temporada marcada como activa, se desactivaran las demas: id={}", entidad.getId());
            temporadaDao.desactivarOtrasTemporadas(entidad.getId());
        }
        boolean resultado = temporadaDao.guardarTemporada(entidad);
        logger.debug("Fin guardarTemporada: resultado={}", resultado);
        return resultado;
    }

    @Override
    public TemporadaDTO obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        TemporadaDTO temporada = toDTO(temporadaDao.obtenerPorId(id));
        logger.debug("Fin obtenerPorId: id={}, encontrada={}", id, temporada != null);
        return temporada;
    }

    @Override
    public TemporadaDTO obtenerTemporadaActiva() {
        logger.debug("Inicio obtenerTemporadaActiva");
        TemporadaDTO temporada = toDTO(temporadaDao.obtenerTemporadaActiva());
        logger.debug("Fin obtenerTemporadaActiva: encontrada={}", temporada != null);
        return temporada;
    }

    @Override
    public java.time.LocalDate obtenerFechaFinTemporadaActiva() {
        TemporadaDTO temporada = obtenerTemporadaActiva();

        if (temporada == null || temporada.getFechaFin() == null) {
            return null;
        }

        java.util.Date fin = temporada.getFechaFin();

        // java.sql.Date no admite toInstant().
        return fin instanceof java.sql.Date sqlDate
                ? sqlDate.toLocalDate()
                : fin.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    @Override
    public List<TemporadaDTO> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<TemporadaDTO> temporadas = toDTOList(temporadaDao.obtenerTodos());
        logger.debug("Fin obtenerTodos: total={}", temporadas.size());
        return temporadas;
    }

    @Override
    @Transactional
    public void eliminar(Long id, boolean confirmarBorrador) {
        logger.debug("Inicio eliminar: id={}, confirmarBorrador={}", id, confirmarBorrador);

        Temporada temporada = temporadaDao.obtenerPorId(id);
        if (temporada == null) {
            throw new IllegalArgumentException("La temporada no existe.");
        }

        int filas = jugadorTemporadaDao.contarPorTemporada(id);

        if (filas > 0) {
            Temporada activa = temporadaDao.obtenerTemporadaActiva();
            boolean futuraConBorrador = !Integer.valueOf(1).equals(temporada.getActiva())
                    && activa != null && activa.getFechaInicio() != null && temporada.getFechaInicio() != null
                    && temporada.getFechaInicio().after(activa.getFechaInicio());

            // Activa, o que ya estuvo activa: sus plantillas son historial
            if (!futuraConBorrador) {
                logger.warn("No se puede borrar la temporada id={}: tiene historial de plantillas", id);
                throw new IllegalStateException(MENSAJE_HISTORIAL);
            }
            if (!confirmarBorrador) {
                throw new BorradorPlantillasException(filas);
            }
            jugadorTemporadaDao.eliminarPorTemporada(id);
        } else if (Integer.valueOf(1).equals(temporada.getActiva())) {
            // Sin filas todavía, pero es la activa: tampoco se borra, es la que usa la app
            throw new IllegalStateException(MENSAJE_HISTORIAL);
        }

        try {
            temporadaDao.eliminar(id);
        } catch (DataIntegrityViolationException e) {
            logger.warn("No se puede borrar la temporada id={} por una restricción de integridad: {}", id,
                    e.getMostSpecificCause().getMessage());
            throw new IllegalStateException("No se puede borrar la temporada porque tiene datos relacionados "
                    + "(por ejemplo cuotas o conceptos de pago). Bórralos antes.", e);
        }
        logger.debug("Fin eliminar: id={}", id);
    }

    @Override
    public int jugadoresConBorrador(Long id) {
        return jugadorTemporadaDao.contarPorTemporada(id);
    }

    private static Temporada toEntity(TemporadaDTO dto) {

        if (dto == null) {
            return null;
        }

        Temporada temporada = new Temporada();

        temporada.setId(dto.getId());
        temporada.setNombre(dto.getNombre());
        temporada.setFechaInicio(dto.getFechaInicio());
        temporada.setFechaFin(dto.getFechaFin());
        temporada.setActiva(dto.getActiva());

        return temporada;
    }

    private static TemporadaDTO toDTO(Temporada temporada) {

        if (temporada == null) {
            return null;
        }

        TemporadaDTO dto = new TemporadaDTO();

        dto.setId(temporada.getId());
        dto.setNombre(temporada.getNombre());
        dto.setFechaInicio(temporada.getFechaInicio());
        dto.setFechaFin(temporada.getFechaFin());
        dto.setActiva(temporada.getActiva());

        return dto;
    }

    private static List<TemporadaDTO> toDTOList(List<Temporada> temporadas) {

        List<TemporadaDTO> lista = new ArrayList<>();

        for (Temporada temporada : temporadas) {
            lista.add(toDTO(temporada));
        }

        return lista;
    }
}