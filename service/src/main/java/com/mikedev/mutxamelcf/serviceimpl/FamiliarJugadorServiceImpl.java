package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.FamiliarDao;
import com.mikedev.mutxamelcf.dao.FamiliarJugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.FamiliarJugador;
import com.mikedev.mutxamelcf.model.FamiliarJugadorDTO;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;

@Service
public class FamiliarJugadorServiceImpl implements FamiliarJugadorService {

    private static final Logger logger = LoggerFactory.getLogger(FamiliarJugadorServiceImpl.class);

    private final FamiliarJugadorDao familiarJugadorDao;

    private final FamiliarDao familiarDao;

    private final JugadorDao jugadorDao;

    public FamiliarJugadorServiceImpl(FamiliarJugadorDao familiarJugadorDao, FamiliarDao familiarDao,
            JugadorDao jugadorDao) {
        this.familiarJugadorDao = familiarJugadorDao;
        this.familiarDao = familiarDao;
        this.jugadorDao = jugadorDao;
    }

    @Override
    public boolean guardarFamiliarJugador(FamiliarJugadorDTO familiarJugador) {
        logger.debug("Inicio guardarFamiliarJugador: id={}, familiarId={}, jugadorId={}",
                familiarJugador != null ? familiarJugador.getId() : null,
                familiarJugador != null ? familiarJugador.getFamiliarId() : null,
                familiarJugador != null ? familiarJugador.getJugadorId() : null);

        if (familiarJugador == null || familiarJugador.getFamiliarId() == null
                || familiarJugador.getJugadorId() == null) {
            logger.warn("guardarFamiliarJugador fallido: datos incompletos");
            return false;
        }

        if (familiarJugador.getId() == null
                && familiarJugadorDao.existeRelacion(
                        familiarJugador.getFamiliarId(),
                        familiarJugador.getJugadorId())) {
            logger.warn("Relacion familiar-jugador ya existe: familiarId={}, jugadorId={}",
                    familiarJugador.getFamiliarId(), familiarJugador.getJugadorId());
            return false;
        }

        boolean resultado = familiarJugadorDao.guardarFamiliarJugador(toEntity(familiarJugador));
        logger.debug("Fin guardarFamiliarJugador: resultado={}", resultado);
        return resultado;
    }

    @Override
    public FamiliarJugadorDTO obtenerPorId(Long id) {
        logger.debug("Inicio obtenerPorId: id={}", id);
        FamiliarJugador relacion = familiarJugadorDao.obtenerPorId(id);
        FamiliarJugadorDTO dto = toDTO(relacion);
        if (dto != null) {
            enriquecerDTO(dto);
        }
        logger.debug("Fin obtenerPorId: encontrado={}", dto != null);
        return dto;
    }

    @Override
    public List<FamiliarJugadorDTO> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<FamiliarJugador> relaciones = familiarJugadorDao.obtenerTodos();
        List<FamiliarJugadorDTO> lista = new ArrayList<>();
        for (FamiliarJugador relacion : relaciones) {
            FamiliarJugadorDTO dto = toDTO(relacion);
            enriquecerDTO(dto);
            lista.add(dto);
        }
        logger.debug("Fin obtenerTodos: total={}", lista.size());
        return lista;
    }

    @Override
    public List<FamiliarJugadorDTO> obtenerFamiliaresDeJugador(Long jugadorId) {
        logger.debug("Inicio obtenerFamiliaresDeJugador: jugadorId={}", jugadorId);
        List<FamiliarJugador> entidades = familiarJugadorDao.obtenerFamiliaresDeJugador(jugadorId);
        List<FamiliarJugadorDTO> resultado = new ArrayList<>();
        for (FamiliarJugador relacion : entidades) {
            FamiliarJugadorDTO dto = toDTO(relacion);
            if (dto.getFamiliarId() != null) {
                Familiar f = familiarDao.obtenerPorId(dto.getFamiliarId());
                if (f != null) {
                    dto.setFamiliarNombre(f.getNombre());
                    dto.setFamiliarApellidos(f.getApellidos());
                    dto.setFamiliarTelefono(f.getTelefono());
                    dto.setFamiliarEmail(f.getEmail());
                }
            }
            resultado.add(dto);
        }
        logger.debug("Fin obtenerFamiliaresDeJugador: total={}", resultado.size());
        return resultado;
    }

    @Override
    public List<FamiliarJugadorDTO> obtenerJugadoresDeFamiliar(Long familiarId) {
        logger.debug("Inicio obtenerJugadoresDeFamiliar: familiarId={}", familiarId);
        List<FamiliarJugador> entidades = familiarJugadorDao.obtenerJugadoresDeFamiliar(familiarId);
        List<FamiliarJugadorDTO> resultado = new ArrayList<>();
        for (FamiliarJugador relacion : entidades) {
            FamiliarJugadorDTO dto = toDTO(relacion);
            if (dto.getJugadorId() != null) {
                Jugador j = jugadorDao.obtenerPorId(dto.getJugadorId());
                if (j != null) {
                    dto.setJugadorNombre(j.getNombre());
                    dto.setJugadorApellidos(j.getApellidos());
                    dto.setJugadorEquipo(j.getEquipo());
                    dto.setJugadorCategoria(j.getCategoria());
                    dto.setJugadorDorsal(j.getDorsal());
                    dto.setJugadorPosicion(j.getPosicion());
                }
            }
            resultado.add(dto);
        }
        logger.debug("Fin obtenerJugadoresDeFamiliar: total={}", resultado.size());
        return resultado;
    }

    @Override
    public void eliminar(Long id) {
        logger.debug("Inicio eliminar: id={}", id);
        familiarJugadorDao.eliminar(id);
        logger.debug("Fin eliminar: id={}", id);
    }

    @Override
    public FamiliarJugadorDTO obtenerPrincipalDeJugador(Long jugadorId) {
        logger.debug("Inicio obtenerPrincipalDeJugador: jugadorId={}", jugadorId);
        FamiliarJugador relacion = familiarJugadorDao.obtenerPrincipalDeJugador(jugadorId);
        FamiliarJugadorDTO dto = toDTO(relacion);
        if (dto != null) {
            enriquecerDTO(dto);
        }
        logger.debug("Fin obtenerPrincipalDeJugador: encontrado={}", dto != null);
        return dto;
    }

    @Override
    public boolean existeRelacion(Long familiarId, Long jugadorId) {
        logger.debug("Inicio existeRelacion: familiarId={}, jugadorId={}", familiarId, jugadorId);
        boolean existe = familiarJugadorDao.existeRelacion(familiarId, jugadorId);
        logger.debug("Fin existeRelacion: existe={}", existe);
        return existe;
    }

    @Override
    public boolean tieneJugadores(Long familiarId) {
        logger.debug("Inicio tieneJugadores: familiarId={}", familiarId);
        boolean tieneJugadores = familiarJugadorDao.tieneJugadores(familiarId);
        logger.debug("Fin tieneJugadores: resultado={}", tieneJugadores);
        return tieneJugadores;
    }

    private void enriquecerDTO(FamiliarJugadorDTO dto) {
        if (dto == null) {
            return;
        }
        if (dto.getFamiliarId() != null) {
            Familiar f = familiarDao.obtenerPorId(dto.getFamiliarId());
            if (f != null) {
                dto.setFamiliarNombre(f.getNombre());
                dto.setFamiliarApellidos(f.getApellidos());
                dto.setFamiliarTelefono(f.getTelefono());
                dto.setFamiliarEmail(f.getEmail());
            }
        }
        if (dto.getJugadorId() != null) {
            Jugador j = jugadorDao.obtenerPorId(dto.getJugadorId());
            if (j != null) {
                dto.setJugadorNombre(j.getNombre());
                dto.setJugadorApellidos(j.getApellidos());
                dto.setJugadorEquipo(j.getEquipo());
                dto.setJugadorCategoria(j.getCategoria());
                dto.setJugadorDorsal(j.getDorsal());
                dto.setJugadorPosicion(j.getPosicion());
            }
        }
    }

    private static FamiliarJugador toEntity(FamiliarJugadorDTO dto) {
        if (dto == null) {
            return null;
        }

        FamiliarJugador relacion = new FamiliarJugador();
        relacion.setId(dto.getId());
        relacion.setFamiliarId(dto.getFamiliarId());
        relacion.setJugadorId(dto.getJugadorId());
        relacion.setParentesco(dto.getParentesco());
        relacion.setEsPrincipal(dto.getEsPrincipal());

        return relacion;
    }

    private static FamiliarJugadorDTO toDTO(FamiliarJugador relacion) {
        if (relacion == null) {
            return null;
        }

        FamiliarJugadorDTO dto = new FamiliarJugadorDTO();
        dto.setId(relacion.getId());
        dto.setFamiliarId(relacion.getFamiliarId());
        dto.setJugadorId(relacion.getJugadorId());
        dto.setParentesco(relacion.getParentesco());
        dto.setEsPrincipal(relacion.getEsPrincipal());

        return dto;
    }
}