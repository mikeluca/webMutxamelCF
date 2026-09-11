package com.mikedev.mutxamelcf.serviceimpl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.FamiliarDao;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.FamiliarJugadorDTO;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.model.FamiliarContactoDTO;

@Service
public class FamiliarServiceImpl implements FamiliarService {

    private static final Logger logger = LoggerFactory.getLogger(FamiliarServiceImpl.class);

    private final FamiliarDao familiarDao;

    private final FamiliarJugadorService familiarJugadorService;

    public FamiliarServiceImpl(FamiliarDao familiarDao, FamiliarJugadorService familiarJugadorService) {
        this.familiarDao = familiarDao;
        this.familiarJugadorService = familiarJugadorService;
    }

    @Override
    public boolean guardarFamiliar(FamiliarDTO familiar) {
        logger.debug("Inicio guardarFamiliar: id={}, nombre={}",
                familiar != null ? familiar.getId() : null,
                familiar != null ? familiar.getNombre() : null);
        boolean resultado = familiarDao.guardarFamiliar(toEntity(familiar));
        logger.debug("Fin guardarFamiliar: resultado={}", resultado);
        return resultado;
    }

    @Override
    public FamiliarDTO obtenerFamiliarPorId(Long id) {
        logger.debug("Inicio obtenerFamiliarPorId: id={}", id);
        FamiliarDTO dto = toDTO(familiarDao.obtenerPorId(id));
        logger.debug("Fin obtenerFamiliarPorId: encontrado={}", dto != null);
        return dto;
    }

    @Override
    public List<FamiliarDTO> obtenerTodos() {
        logger.debug("Inicio obtenerTodos");
        List<FamiliarDTO> lista = toDTOList(familiarDao.obtenerTodos());
        logger.debug("Fin obtenerTodos: total={}", lista.size());
        return lista;
    }

    @Override
    public void eliminarFamiliar(Long id) {
        logger.debug("Inicio eliminarFamiliar: id={}", id);
        if (familiarJugadorService.tieneJugadores(id)) {
            logger.warn("No se puede eliminar el familiar id={} porque tiene jugadores asociados", id);
            throw new IllegalStateException(
                    "No se puede eliminar el familiar porque está asociado a uno o varios jugadores.");
        }

        familiarDao.eliminar(id);
        logger.debug("Fin eliminarFamiliar: id={}", id);
    }

    private static Familiar toEntity(FamiliarDTO dto) {

        if (dto == null) {
            return null;
        }

        Familiar familiar = new Familiar();

        familiar.setId(dto.getId());
        familiar.setNombre(dto.getNombre());
        familiar.setApellidos(dto.getApellidos());
        familiar.setTelefono(dto.getTelefono());
        familiar.setEmail(dto.getEmail());
        familiar.setRecibeInfoClub(dto.getRecibeInfoClub());
        familiar.setWhatsappActivo(dto.getWhatsappActivo());

        return familiar;
    }

    private static FamiliarDTO toDTO(Familiar familiar) {

        if (familiar == null) {
            return null;
        }

        FamiliarDTO dto = new FamiliarDTO();

        dto.setId(familiar.getId());
        dto.setNombre(familiar.getNombre());
        dto.setApellidos(familiar.getApellidos());
        dto.setTelefono(familiar.getTelefono());
        dto.setEmail(familiar.getEmail());
        dto.setRecibeInfoClub(familiar.getRecibeInfoClub());
        dto.setWhatsappActivo(familiar.getWhatsappActivo());

        return dto;
    }

    private static List<FamiliarDTO> toDTOList(List<Familiar> familiares) {

        List<FamiliarDTO> lista = new ArrayList<FamiliarDTO>();

        for (Familiar familiar : familiares) {
            lista.add(toDTO(familiar));
        }

        return lista;
    }

    @Override
    public FamiliarDTO obtenerFamiliarPrincipalDeJugador(
            Long jugadorId) {
        logger.debug("Inicio obtenerFamiliarPrincipalDeJugador: jugadorId={}", jugadorId);
        FamiliarJugadorDTO relacion = familiarJugadorService
                .obtenerPrincipalDeJugador(jugadorId);

        if (relacion == null) {
            logger.debug("Fin obtenerFamiliarPrincipalDeJugador: no existe relacion principal");
            return null;
        }

        FamiliarDTO resultado = obtenerFamiliarPorId(relacion.getFamiliarId());
        logger.debug("Fin obtenerFamiliarPrincipalDeJugador: encontrado={}", resultado != null);
        return resultado;
    }

    @Override
    public List<FamiliarContactoDTO> obtenerFamiliaresPorJugador(
            Long jugadorId) {

        List<Familiar> familiares = familiarDao.obtenerPorJugador(jugadorId);

        List<FamiliarContactoDTO> resultado = new ArrayList<>();

        for (Familiar familiar : familiares) {

            resultado.add(
                    new FamiliarContactoDTO(
                            familiar.getId(),
                            familiar.getNombre(),
                            familiar.getApellidos(),
                            familiar.getTelefono(),
                            familiar.getEmail(),
                            familiar.getWhatsappActivo(),
                            familiar.getParentesco(),
                            familiar.getEsPrincipal()));
        }

        return resultado;
    }
}