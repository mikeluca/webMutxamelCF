package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.CoordinacionDao;
import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoCoordinacion;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.ProximosPartidosResponse;
import com.mikedev.mutxamelcf.model.ProximosPartidosResponse.Entrada;
import com.mikedev.mutxamelcf.model.ProximosPartidosResponse.EstadoConvocatoria;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.service.ConvocatoriaPartidoService;
import com.mikedev.mutxamelcf.service.InicioAppService;
import com.mikedev.mutxamelcf.service.PartidoService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * "Tu próximo partido". No duplica lógica: el calendario del jugador ({@link PartidoService}, que ya incluye los
 * partidos como invitado autorizado y las ausencias justificadas), el detalle de la convocatoria
 * ({@link ConvocatoriaPartidoService}, que comprueba el vínculo) y los descansos de la FFCV ({@link CoordinacionDao}).
 *
 * <p>Privacidad: solo los datos de la propia cuenta y sus hijos; de la convocatoria nunca sale la lista de convocados
 * (al entrenador, solo cuántos son).
 */
@Service
public class InicioAppServiceImpl implements InicioAppService {

    private final PerfilAppDao perfilAppDao;
    private final UsuarioAppService usuarioAppService;
    private final PartidoService partidoService;
    private final ConvocatoriaPartidoService convocatoriaPartidoService;
    private final ConvocatoriaDao convocatoriaDao;
    private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
    private final CoordinacionDao coordinacionDao;
    private final Clock clock;

    public InicioAppServiceImpl(
            PerfilAppDao perfilAppDao,
            UsuarioAppService usuarioAppService,
            PartidoService partidoService,
            ConvocatoriaPartidoService convocatoriaPartidoService,
            ConvocatoriaDao convocatoriaDao,
            ConvocatoriaJugadorDao convocatoriaJugadorDao,
            CoordinacionDao coordinacionDao,
            Clock clock) {

        this.perfilAppDao = perfilAppDao;
        this.usuarioAppService = usuarioAppService;
        this.partidoService = partidoService;
        this.convocatoriaPartidoService = convocatoriaPartidoService;
        this.convocatoriaDao = convocatoriaDao;
        this.convocatoriaJugadorDao = convocatoriaJugadorDao;
        this.coordinacionDao = coordinacionDao;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ProximosPartidosResponse obtenerProximosPartidos(Long usuarioAppId) {

        if (usuarioAppId == null || usuarioAppId <= 0) {
            throw new SecurityException("Usuario no válido");
        }

        int usuario = usuarioAppId.intValue();

        Set<String> roles = usuarioAppService.obtenerRoles(usuario).stream()
                .map(RolApp::getCodigo)
                .collect(Collectors.toSet());

        LocalDateTime ahora = LocalDateTime.now(clock);
        Peticion peticion = new Peticion(usuarioAppId, ahora, ahora.toLocalDate(),
                ahora.toLocalDate().plusDays(DIAS_VENTANA));

        List<Entrada> entradas = new ArrayList<>();
        Set<Long> jugadoresYaVistos = new HashSet<>();

        if (roles.contains("JUGADOR")) {
            for (Jugador jugador : perfilAppDao.obtenerJugadoresPropiosActivos(usuario)) {
                if (jugadoresYaVistos.add(jugador.getId())) {
                    entradaDeJugador(peticion, ProximosPartidosResponse.TIPO_JUGADOR, jugador)
                            .ifPresent(entradas::add);
                }
            }
        }

        // Quien es jugador y a la vez familiar no repite a un jugador ya mostrado.
        if (roles.contains("FAMILIAR")) {
            for (Jugador hijo : perfilAppDao.obtenerHijosActivos(usuario)) {
                if (jugadoresYaVistos.add(hijo.getId())) {
                    entradaDeJugador(peticion, ProximosPartidosResponse.TIPO_FAMILIAR, hijo)
                            .ifPresent(entradas::add);
                }
            }
        }

        // Coordinación no recibe todos los equipos: solo los que entrena (si los tiene).
        if (roles.contains("ENTRENADOR")) {
            for (Equipo equipo : perfilAppDao.obtenerEquiposPorUsuario(usuario)) {
                entradaDeEntrenador(peticion, equipo).ifPresent(entradas::add);
            }
        }

        // Por fecha y hora; los descansos (sin partido) al final.
        entradas.sort(Comparator.comparing(
                (Entrada e) -> e.partido() == null ? null : inicio(e.partido()),
                Comparator.nullsLast(Comparator.naturalOrder())));

        return new ProximosPartidosResponse(
                entradas.size() > MAXIMO_ENTRADAS ? List.copyOf(entradas.subList(0, MAXIMO_ENTRADAS)) : entradas);
    }

    // ---- jugador y familiar --------------------------------------------------------------------------------------

    private Optional<Entrada> entradaDeJugador(Peticion p, String tipo, Jugador jugador) {

        if (jugador.getEquipoId() == null) {
            return Optional.empty();
        }

        // Ya trae, mezclados, los partidos del equipo y los de otros equipos como invitado autorizado.
        PartidoDTO proximo = proximo(p, partidoService.obtenerPorEquipoYRangoFechas(
                jugador.getEquipoId(), p.hoy(), p.hasta(), jugador.getId()));

        String nombre = nombreCompleto(jugador);

        if (proximo == null) {
            return descansa(p, jugador.getEquipoId())
                    ? Optional.of(new Entrada(tipo, jugador.getId(), nombre, jugador.getEquipoId(),
                            jugador.getEquipo(), null, null, true))
                    : Optional.empty();
        }

        return Optional.of(new Entrada(tipo, jugador.getId(), nombre, jugador.getEquipoId(), jugador.getEquipo(),
                proximo, convocatoriaDeJugador(p, proximo, jugador.getId()), false));
    }

    private EstadoConvocatoria convocatoriaDeJugador(Peticion p, PartidoDTO partido, Long jugadorId) {

        if (partido.isAusenciaJustificada()) {
            return EstadoConvocatoria.de(EstadoConvocatoria.AUSENCIA_JUSTIFICADA);
        }

        // null: el partido todavía no tiene convocatoria (no es "no convocado").
        if (partido.getConvocado() == null) {
            return EstadoConvocatoria.de(EstadoConvocatoria.SIN_CONVOCATORIA);
        }

        if (!partido.getConvocado()) {
            return EstadoConvocatoria.de(EstadoConvocatoria.NO_CONVOCADO);
        }

        String estado = partido.isInvitado() ? EstadoConvocatoria.INVITADO : EstadoConvocatoria.CONVOCADO;
        String equipoInvita = partido.isInvitado() ? partido.getEquipo() : null;

        try {

            // Comprueba el vínculo; de aquí solo se coge la citación y el equipaje, nunca la lista de convocados.
            var detalle = convocatoriaPartidoService.obtenerParaJugador(p.usuarioId(), partido.getId(), jugadorId);

            return new EstadoConvocatoria(estado, detalle.horaCitacion(), detalle.lugarCitacion(),
                    detalle.equipacion(), detalle.equipacionMochila(), equipoInvita, null);

        } catch (NoSuchElementException e) {

            return new EstadoConvocatoria(estado, null, null, null, null, equipoInvita, null);
        }
    }

    // ---- entrenador ----------------------------------------------------------------------------------------------

    private Optional<Entrada> entradaDeEntrenador(Peticion p, Equipo equipo) {

        PartidoDTO proximo = proximo(p, partidoService.obtenerPorEquipoYRangoFechas(equipo.getId(), p.hoy(), p.hasta()));

        if (proximo == null) {
            return descansa(p, equipo.getId())
                    ? Optional.of(new Entrada(ProximosPartidosResponse.TIPO_ENTRENADOR, null, null, equipo.getId(),
                            equipo.getNombre(), null, null, true))
                    : Optional.empty();
        }

        Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(proximo.getId());

        EstadoConvocatoria estado = convocatoria == null
                ? EstadoConvocatoria.de(EstadoConvocatoria.SIN_CONVOCATORIA)
                : new EstadoConvocatoria(EstadoConvocatoria.PUBLICADA, convocatoria.getHoraConvocatoria(),
                        convocatoria.getLugarConvocatoria(), null, null, null,
                        convocatoriaJugadorDao.obtenerPorConvocatoria(convocatoria.getId()).size());

        return Optional.of(new Entrada(ProximosPartidosResponse.TIPO_ENTRENADOR, null, null, equipo.getId(),
                equipo.getNombre(), proximo, estado, false));
    }

    // ---- común ---------------------------------------------------------------------------------------------------

    /** El primero que no está cancelado y aún no ha empezado. */
    private static PartidoDTO proximo(Peticion p, List<PartidoDTO> partidos) {

        return partidos.stream()
                .filter(x -> !x.isCancelado() && x.getDia() != null && inicio(x).isAfter(p.ahora()))
                .min(Comparator.comparing(InicioAppServiceImpl::inicio))
                .orElse(null);
    }

    private static LocalDateTime inicio(PartidoDTO partido) {

        LocalDate dia = partido.getDia() instanceof java.sql.Date sql
                ? sql.toLocalDate()
                : new java.sql.Date(partido.getDia().getTime()).toLocalDate();

        return CriterioResultadoPartido.inicio(dia, partido.getHora());
    }

    /** Sin partido en la ventana pero con jornada de descanso de la FFCV en ella (una consulta por petición). */
    private boolean descansa(Peticion p, Long equipoId) {

        if (p.descansos == null) {
            p.descansos = coordinacionDao.descansosEntre(p.hoy(), p.hasta()).stream()
                    .map(EquipoCoordinacion::equipoId)
                    .collect(Collectors.toSet());
        }

        return p.descansos.contains(equipoId);
    }

    private static String nombreCompleto(Jugador jugador) {

        String apellidos = jugador.getApellidos();

        return apellidos == null || apellidos.isBlank() ? jugador.getNombre() : jugador.getNombre() + " " + apellidos;
    }

    /** Lo que comparten las entradas de una misma petición. */
    private static final class Peticion {

        private final Long usuarioId;
        private final LocalDateTime ahora;
        private final LocalDate hoy;
        private final LocalDate hasta;
        private Set<Long> descansos;

        Peticion(Long usuarioId, LocalDateTime ahora, LocalDate hoy, LocalDate hasta) {
            this.usuarioId = usuarioId;
            this.ahora = ahora;
            this.hoy = hoy;
            this.hasta = hasta;
        }

        Long usuarioId() {
            return usuarioId;
        }

        LocalDateTime ahora() {
            return ahora;
        }

        LocalDate hoy() {
            return hoy;
        }

        LocalDate hasta() {
            return hasta;
        }
    }
}
