package com.mikedev.mutxamelcf.serviceimpl;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.ConvocatoriaPartidoResponse;
import com.mikedev.mutxamelcf.model.JugadorBasico;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ConvocatoriaPartidoService;

@Service
public class ConvocatoriaPartidoServiceImpl implements ConvocatoriaPartidoService {

    private final ConvocatoriaDao convocatoriaDao;
    private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
    private final PartidoDao partidoDao;
    private final EquipoGestionDao equipoGestionDao;
    private final UsuarioAppVinculoDao usuarioAppVinculoDao;
    private final PartidoLiveEventoDao partidoLiveEventoDao;

    public ConvocatoriaPartidoServiceImpl(
            ConvocatoriaDao convocatoriaDao,
            ConvocatoriaJugadorDao convocatoriaJugadorDao,
            PartidoDao partidoDao,
            EquipoGestionDao equipoGestionDao,
            UsuarioAppVinculoDao usuarioAppVinculoDao,
            PartidoLiveEventoDao partidoLiveEventoDao) {

        this.convocatoriaDao = convocatoriaDao;
        this.convocatoriaJugadorDao = convocatoriaJugadorDao;
        this.partidoDao = partidoDao;
        this.equipoGestionDao = equipoGestionDao;
        this.usuarioAppVinculoDao = usuarioAppVinculoDao;
        this.partidoLiveEventoDao = partidoLiveEventoDao;
    }

    @Override
    @Transactional(readOnly = true)
    public ConvocatoriaPartidoResponse obtenerParaJugador(Long usuarioAppId, Long partidoId, Long jugadorId) {

        if (usuarioAppId == null || usuarioAppId <= 0) {
            throw new SecurityException("Usuario no válido");
        }

        if (partidoId == null || partidoId <= 0) {
            throw new IllegalArgumentException("El partido no es válido");
        }

        if (jugadorId == null || jugadorId <= 0) {
            throw new IllegalArgumentException("El jugador es obligatorio");
        }

        // El vínculo, lo primero: quien no lo tiene recibe 403 sea cual sea el resto.
        if (!usuarioAppVinculoDao.tieneVinculoConJugador(usuarioAppId.intValue(), jugadorId)) {
            throw new SecurityException("No tienes permiso para consultar este jugador");
        }

        Partido partido = partidoDao.obtenerPorId(partidoId);

        if (partido == null) {
            throw new NoSuchElementException("El partido no existe");
        }

        if (!equipoGestionDao.perteneceJugadorAEquipo(jugadorId, partido.getEquipoId())) {
            throw new SecurityException("El jugador no pertenece al equipo de este partido");
        }

        Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(partidoId);

        if (convocatoria == null) {
            throw new NoSuchElementException("El partido todavía no tiene convocatoria");
        }

        List<Long> ids = convocatoriaJugadorDao.obtenerPorConvocatoria(convocatoria.getId()).stream()
                .map(ConvocatoriaJugador::getJugadorId)
                .toList();

        List<ConvocatoriaPartidoResponse.Convocado> convocados = partidoLiveEventoDao.obtenerJugadoresBasicos(ids)
                .stream()
                .sorted(Comparator
                        .comparing(JugadorBasico::dorsal, Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
                        .thenComparing(JugadorBasico::apellidos, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(jugador -> new ConvocatoriaPartidoResponse.Convocado(
                        jugador.nombre(), jugador.apellidos(), jugador.dorsal()))
                .toList();

        return new ConvocatoriaPartidoResponse(
                convocatoria.getId(),
                partido.getRival(),
                PlazoJustificacionPartido.dia(partido),
                partido.getHora(),
                partido.getCampo(),
                partido.isLocal(),
                convocatoria.getHoraConvocatoria(),
                convocatoria.getLugarConvocatoria(),
                ids.contains(jugadorId),
                convocados);
    }
}
