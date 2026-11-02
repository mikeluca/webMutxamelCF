package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.CategoriaEdadDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipacionDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.InvitacionDao;
import com.mikedev.mutxamelcf.dao.InvitacionDao.PartidoDelDia;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.model.Equipacion;
import com.mikedev.mutxamelcf.model.ConflictoInvitacionException;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugadorResponse;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoInvitableResponse;
import com.mikedev.mutxamelcf.model.EstadoInvitacion;
import com.mikedev.mutxamelcf.model.InvitacionDetalle;
import com.mikedev.mutxamelcf.model.InvitacionPendienteResponse;
import com.mikedev.mutxamelcf.model.InvitacionPendienteResponse.OtraInvitacion;
import com.mikedev.mutxamelcf.model.InvitacionPendienteResponse.PartidoSolapado;
import com.mikedev.mutxamelcf.model.InvitacionResponse;
import com.mikedev.mutxamelcf.model.JugadorInvitable;
import com.mikedev.mutxamelcf.model.JugadorInvitableResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartidoResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.InvitadosService;
import com.mikedev.mutxamelcf.service.JustificacionFaltaPartidoService;

/**
 * Jugadores de otros equipos invitados a una convocatoria. El diseño y el flujo están en
 * docs/convocatoria-invitados.md; el contrato de la API, en docs/api-app-convocatoria-invitados.md.
 *
 * <p>Los avisos se crean dentro de la transacción del cambio y el push sale al confirmarla (ComunicacionService). Los
 * textos del push no llevan el motivo de la familia ni el de coordinación: esos van solo en la bandeja.
 */
@Service
public class InvitadosServiceImpl implements InvitadosService {

    private static final Logger logger = LoggerFactory.getLogger(InvitadosServiceImpl.class);

    /** Valores de "tipo" en el data de los push de las invitaciones. */
    static final String TIPO_PENDIENTE = "INVITACION_PENDIENTE";
    static final String TIPO_RECHAZADA = "INVITACION_RECHAZADA";
    static final String TIPO_LIBERAR = "INVITACION_LIBERAR";
    static final String TIPO_INFORMATIVA = "INVITACION_INFORMATIVA";
    static final String TIPO_PLAZO = "INVITACION_PLAZO";
    static final String TIPO_AUTORIZADA = "INVITACION_AUTORIZADA";
    static final String TIPO_DESESTIMADA = "INVITACION_DESESTIMADA";
    static final String TIPO_ANULADA = "INVITACION_ANULADA";
    static final String TIPO_LIBERADA_COORDINACION = "INVITACION_LIBERADA_COORDINACION";
    static final String TIPO_FECHA_CAMBIADA = "INVITACION_FECHA_CAMBIADA";
    static final String TIPO_SOLAPAMIENTO = "INVITACION_SOLAPAMIENTO";

    /** El tipo del push de una convocatoria (ConvocatoriaServiceImpl): la app abre el calendario en ese día. */
    private static final String TIPO_PUSH_CONVOCATORIA = ConvocatoriaServiceImpl.TIPO_PUSH_CONVOCATORIA;

    static final Duration PLAZO_ENTRENADOR = Duration.ofHours(12);

    private static final int MOTIVO_MAXIMO = 500;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
    private final InvitacionDao invitacionDao;
    private final ConvocatoriaDao convocatoriaDao;
    private final PartidoDao partidoDao;
    private final EquipoDao equipoDao;
    private final CategoriaEdadDao categoriaEdadDao;
    private final TemporadaDao temporadaDao;
    private final EquipoGestionDao equipoGestionDao;
    private final JustificacionFaltaPartidoDao justificacionDao;
    private final UsuarioAppDao usuarioAppDao;
    private final ComunicacionService comunicacionService;
    private final JustificacionFaltaPartidoService justificacionService;
    private final ReglaInvitados regla;
    private final EquipacionDao equipacionDao;
    private final Clock clock;
    private final TextosAvisos textos;
    private final IdiomasUsuarios idiomas;

    public InvitadosServiceImpl(
            ConvocatoriaJugadorDao convocatoriaJugadorDao,
            InvitacionDao invitacionDao,
            ConvocatoriaDao convocatoriaDao,
            PartidoDao partidoDao,
            EquipoDao equipoDao,
            CategoriaEdadDao categoriaEdadDao,
            TemporadaDao temporadaDao,
            EquipoGestionDao equipoGestionDao,
            JustificacionFaltaPartidoDao justificacionDao,
            UsuarioAppDao usuarioAppDao,
            ComunicacionService comunicacionService,
            JustificacionFaltaPartidoService justificacionService,
            ReglaInvitados regla,
            EquipacionDao equipacionDao,
            Clock clock,
            TextosAvisos textos,
            IdiomasUsuarios idiomas) {
        this.textos = textos;
        this.idiomas = idiomas;
        this.equipacionDao = equipacionDao;

        this.convocatoriaJugadorDao = convocatoriaJugadorDao;
        this.invitacionDao = invitacionDao;
        this.convocatoriaDao = convocatoriaDao;
        this.partidoDao = partidoDao;
        this.equipoDao = equipoDao;
        this.categoriaEdadDao = categoriaEdadDao;
        this.temporadaDao = temporadaDao;
        this.equipoGestionDao = equipoGestionDao;
        this.justificacionDao = justificacionDao;
        this.usuarioAppDao = usuarioAppDao;
        this.comunicacionService = comunicacionService;
        this.justificacionService = justificacionService;
        this.regla = regla;
        this.clock = clock;
    }

    // ==================================================================
    // Equipos y jugadores invitables
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<EquipoInvitableResponse> equiposInvitables(Long usuarioAppId, Long partidoId) {

        Partido partido = partidoParaGestionar(usuarioAppId, partidoId);

        ReglaInvitados.EquipoRegla destino = reglaDelEquipo(partido.getEquipoId());
        List<CategoriaEdad> categorias = categoriaEdadDao.obtenerTodas();

        List<EquipoInvitableResponse> equipos = new ArrayList<>();

        for (Equipo equipo : equipoDao.obtenerActivos()) {

            if (regla.puedeInvitar(destino, ReglaInvitados.EquipoRegla.de(equipo, esPretemporada(equipo)),
                    categorias)) {

                equipos.add(new EquipoInvitableResponse(
                        equipo.getId(), equipo.getNombre(), equipo.getCategoria(), equipo.getGrupo()));
            }
        }

        return equipos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JugadorInvitableResponse> jugadoresInvitables(Long usuarioAppId, Long partidoId, Long equipoOrigenId) {

        Partido partido = partidoParaGestionar(usuarioAppId, partidoId);

        if (equipoOrigenId == null || equipoOrigenId <= 0) {
            throw new IllegalArgumentException("El equipo no es válido");
        }

        validarEquipoOrigen(partido.getEquipoId(), equipoOrigenId);

        LocalDate dia = PlazoJustificacionPartido.dia(partido);
        Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(partido.getId());

        Set<Long> yaInvitados = new LinkedHashSet<>();

        if (convocatoria != null) {

            for (ConvocatoriaJugador fila : convocatoriaJugadorDao.obtenerTodasPorConvocatoria(convocatoria.getId())) {

                if (fila.isInvitado() && fila.getEstadoInvitacion() != EstadoInvitacion.RECHAZADO) {
                    yaInvitados.add(fila.getJugadorId());
                }
            }
        }

        List<JugadorInvitableResponse> jugadores = new ArrayList<>();

        for (JugadorInvitable jugador : invitacionDao.jugadoresInvitables(equipoOrigenId, temporadaActivaId())) {

            InvitacionDetalle autorizada = dia == null ? null : autorizadaEseDia(jugador.id(), dia, null);

            jugadores.add(new JugadorInvitableResponse(
                    jugador.id(),
                    nombreCompleto(jugador.nombre(), jugador.apellidos()),
                    jugador.dorsal(),
                    yaInvitados.contains(jugador.id()),
                    autorizada != null,
                    autorizada == null ? null
                            : "Ya está invitado con " + autorizada.equipoDestino() + " el " + FECHA.format(dia)));
        }

        return jugadores;
    }

    // ==================================================================
    // Altas y bajas desde la convocatoria
    // ==================================================================

    @Override
    @Transactional
    public void sincronizarInvitados(Long usuarioAppId, Convocatoria convocatoria, Partido partido,
            List<Long> invitadosIds) {

        if (invitadosIds == null) {
            return;
        }

        Set<Long> deseados = new LinkedHashSet<>();

        for (Long id : invitadosIds) {

            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Hay un jugador invitado no válido");
            }

            deseados.add(id);
        }

        Map<Long, ConvocatoriaJugador> actuales = new LinkedHashMap<>();

        for (ConvocatoriaJugador fila : convocatoriaJugadorDao.obtenerTodasPorConvocatoria(convocatoria.getId())) {

            if (fila.isInvitado()) {
                actuales.put(fila.getJugadorId(), fila);
            }
        }

        Set<Long> justificados = justificacionDao.obtenerPorPartido(partido.getId()).stream()
                .map(JustificacionFaltaPartido::getJugadorId)
                .collect(Collectors.toSet());

        // Los nuevos: los que no están o estaban rechazados (se reinvitan sobre la misma fila).
        List<Long> nuevos = deseados.stream()
                .filter(id -> !actuales.containsKey(id)
                        || actuales.get(id).getEstadoInvitacion() == EstadoInvitacion.RECHAZADO)
                .toList();

        long quedan = actuales.values().stream()
                .filter(fila -> deseados.contains(fila.getJugadorId()))
                .filter(fila -> fila.getEstadoInvitacion() != EstadoInvitacion.RECHAZADO)
                .filter(fila -> !justificados.contains(fila.getJugadorId()))
                .count();

        if (quedan + nuevos.size() > ReglaInvitados.MAXIMO_INVITADOS) {
            throw new IllegalArgumentException("Una convocatoria admite como máximo " + ReglaInvitados.MAXIMO_INVITADOS
                    + " jugadores de otros equipos (sin contar los rechazados ni los desestimados por la familia)");
        }

        for (ConvocatoriaJugador fila : actuales.values()) {

            if (!deseados.contains(fila.getJugadorId())) {
                quitarInvitado(fila, convocatoria, partido);
            }
        }

        for (Long jugadorId : nuevos) {
            invitar(usuarioAppId, convocatoria, partido, jugadorId, actuales.get(jugadorId));
        }
    }

    private void invitar(Long usuarioAppId, Convocatoria convocatoria, Partido partido, Long jugadorId,
            ConvocatoriaJugador rechazada) {

        Long equipoDestinoId = convocatoria.getEquipoId();
        String nombre = equipoGestionDao.obtenerNombreJugador(jugadorId);

        if (equipoGestionDao.perteneceJugadorAEquipo(jugadorId, equipoDestinoId)) {
            throw new IllegalArgumentException(nombre + " ya es de este equipo: no hace falta invitarlo");
        }

        Long equipoOrigenId = equipoGestionDao.obtenerEquipoIdDeJugador(jugadorId);

        if (equipoOrigenId == null) {
            throw new IllegalArgumentException(nombre + " no tiene equipo: no se puede invitar");
        }

        validarEquipoOrigen(equipoDestinoId, equipoOrigenId);

        boolean activo = invitacionDao.jugadoresInvitables(equipoOrigenId, temporadaActivaId()).stream()
                .anyMatch(j -> j.id().equals(jugadorId));

        if (!activo) {
            throw new IllegalArgumentException(nombre + " no está activo (baja, a prueba o sin equipo definitivo)");
        }

        if (justificacionDao.obtenerPorPartidoYJugador(partido.getId(), jugadorId) != null) {
            throw new IllegalArgumentException(nombre + " ha justificado su ausencia a este partido");
        }

        LocalDate dia = PlazoJustificacionPartido.dia(partido);

        if (dia != null) {
            InvitacionDetalle autorizada = autorizadaEseDia(jugadorId, dia, rechazada != null ? rechazada.getId() : null);

            if (autorizada != null) {
                throw conflictoYaInvitado(autorizada, nombre, dia);
            }
        }

        LocalDateTime ahora = LocalDateTime.now(clock);

        ConvocatoriaJugador fila = rechazada != null ? rechazada : new ConvocatoriaJugador();

        fila.setConvocatoriaId(convocatoria.getId());
        fila.setJugadorId(jugadorId);
        fila.setEquipoOrigenId(equipoOrigenId);
        fila.setEstadoInvitacion(EstadoInvitacion.PENDIENTE_COORDINACION);
        fila.setFechaSolicitud(ahora);
        fila.setUsuarioInvitaId(usuarioAppId);
        fila.setUsuarioCoordinadorId(null);
        fila.setFechaCoordinacion(null);
        fila.setFechaPeticionEntrenador(null);
        fila.setAviso12hEnviado(false);
        fila.setFechaAutorizacion(null);
        fila.setMotivoRechazo(null);

        if (rechazada != null) {
            convocatoriaJugadorDao.actualizarInvitacion(fila);
        } else {
            convocatoriaJugadorDao.guardar(fila);
        }

        // Si convoca un coordinador (o un administrador), no hace falta otro visto bueno.
        if (equipoGestionDao.esCoordinadorOAdmin(usuarioAppId)) {

            fila.setUsuarioCoordinadorId(usuarioAppId);
            fila.setFechaCoordinacion(ahora);

            resolverSolapamiento(fila, convocatoria, partido, usuarioAppId);

            return;
        }

        avisarCoordinacion(usuarioAppId, fila, convocatoria, partido);
    }

    private void quitarInvitado(ConvocatoriaJugador fila, Convocatoria convocatoria, Partido partido) {

        convocatoriaJugadorDao.eliminarPorConvocatoriaYJugador(convocatoria.getId(), fila.getJugadorId());

        // Si ya estaba autorizado, también se avisa a su familia; a su entrenador, si ya se le había pedido algo.
        if (fila.getEstadoInvitacion() == EstadoInvitacion.AUTORIZADO
                || fila.getEstadoInvitacion() == EstadoInvitacion.PENDIENTE_ENTRENADOR) {

            anular(fila, convocatoria, partido, "invitacion.anulada.quitado.titulo");
        }
    }

    // ==================================================================
    // Visto bueno de coordinación
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<InvitacionPendienteResponse> pendientes(Long usuarioAppId) {

        comprobarCoordinacion(usuarioAppId);

        LocalDateTime ahora = LocalDateTime.now(clock);

        List<InvitacionPendienteResponse> respuesta = new ArrayList<>();

        for (InvitacionDetalle invitacion : invitacionDao.pendientes(LocalDate.now(clock))) {

            List<PartidoSolapado> solapamiento = invitacion.dia() == null ? List.<PartidoSolapado>of()
                    : invitacionDao.partidosDelEquipoEnDia(invitacion.equipoOrigenId(), invitacion.dia()).stream()
                            .map(p -> new PartidoSolapado(p.id(), p.hora(), p.rival()))
                            .toList();

            boolean convocadoEnOrigen = !solapamiento.isEmpty()
                    && !invitacionDao.filasPropiasDelJugadorEnDia(
                            invitacion.equipoOrigenId(), invitacion.jugadorId(), invitacion.dia()).isEmpty();

            OtraInvitacion otra = null;

            if (invitacion.dia() != null) {

                List<InvitacionDetalle> otras = invitacionDao.delJugadorEnDia(
                        invitacion.jugadorId(), invitacion.dia(), invitacion.id());

                InvitacionDetalle elegida = otras.stream()
                        .filter(o -> o.estado() == EstadoInvitacion.AUTORIZADO)
                        .findFirst()
                        .orElse(otras.isEmpty() ? null : otras.get(0));

                if (elegida != null) {
                    otra = new OtraInvitacion(elegida.id(), elegida.equipoDestinoId(), elegida.equipoDestino(),
                            elegida.partidoId(), elegida.hora(), elegida.rival(), elegida.estado().name());
                }
            }

            boolean plazoVencido = invitacion.estado() == EstadoInvitacion.PENDIENTE_ENTRENADOR
                    && invitacion.fechaPeticionEntrenador() != null
                    && !ahora.isBefore(invitacion.fechaPeticionEntrenador().plus(PLAZO_ENTRENADOR));

            respuesta.add(new InvitacionPendienteResponse(
                    invitacion.id(),
                    invitacion.estado().name(),
                    invitacion.convocatoriaId(),
                    invitacion.partidoId(),
                    invitacion.jugadorId(),
                    invitacion.jugador(),
                    invitacion.equipoOrigenId(),
                    invitacion.equipoOrigen(),
                    invitacion.equipoDestinoId(),
                    invitacion.equipoDestino(),
                    invitacion.dia(),
                    invitacion.hora(),
                    invitacion.rival(),
                    invitacion.usuarioInvitaId(),
                    quienConvoca(invitacion),
                    invitacion.fechaSolicitud(),
                    invitacion.fechaPeticionEntrenador(),
                    plazoVencido,
                    convocadoEnOrigen,
                    solapamiento,
                    otra));
        }

        return respuesta;
    }

    @Override
    @Transactional
    public InvitacionResponse aprobar(Long usuarioAppId, Long convocatoriaJugadorId) {

        comprobarCoordinacion(usuarioAppId);

        ConvocatoriaJugador fila = invitacionExistente(convocatoriaJugadorId);
        Convocatoria convocatoria = convocatoriaDao.obtenerPorId(fila.getConvocatoriaId());
        Partido partido = partidoBloqueado(convocatoria);

        // Con el partido bloqueado se vuelve a leer: otro coordinador pudo resolverla mientras tanto.
        fila = invitacionExistente(convocatoriaJugadorId);

        if (fila.getEstadoInvitacion() != EstadoInvitacion.PENDIENTE_COORDINACION) {
            throw new IllegalArgumentException("La invitación ya no está pendiente de coordinación");
        }

        if (partido.isCancelado()) {
            throw new IllegalArgumentException("El partido está cancelado");
        }

        LocalDate dia = PlazoJustificacionPartido.dia(partido);

        if (dia != null) {

            InvitacionDetalle autorizada = autorizadaEseDia(fila.getJugadorId(), dia, fila.getId());

            if (autorizada != null) {
                throw conflictoYaInvitado(autorizada, equipoGestionDao.obtenerNombreJugador(fila.getJugadorId()), dia);
            }
        }

        fila.setUsuarioCoordinadorId(usuarioAppId);
        fila.setFechaCoordinacion(LocalDateTime.now(clock));

        resolverSolapamiento(fila, convocatoria, partido, usuarioAppId);

        return respuesta(fila);
    }

    @Override
    @Transactional
    public InvitacionResponse rechazar(Long usuarioAppId, Long convocatoriaJugadorId, String motivo) {

        comprobarCoordinacion(usuarioAppId);

        ConvocatoriaJugador fila = invitacionExistente(convocatoriaJugadorId);
        Convocatoria convocatoria = convocatoriaDao.obtenerPorId(fila.getConvocatoriaId());
        Partido partido = partidoBloqueado(convocatoria);

        fila = invitacionExistente(convocatoriaJugadorId);

        if (fila.getEstadoInvitacion() != EstadoInvitacion.PENDIENTE_COORDINACION) {
            throw new IllegalArgumentException("La invitación ya no está pendiente de coordinación");
        }

        String texto = motivo == null ? null : motivo.trim();

        if (texto != null && texto.length() > MOTIVO_MAXIMO) {
            throw new IllegalArgumentException("El motivo no puede superar los " + MOTIVO_MAXIMO + " caracteres");
        }

        fila.setEstadoInvitacion(EstadoInvitacion.RECHAZADO);
        fila.setUsuarioCoordinadorId(usuarioAppId);
        fila.setFechaCoordinacion(LocalDateTime.now(clock));
        fila.setMotivoRechazo(texto == null || texto.isEmpty() ? null : texto);

        convocatoriaJugadorDao.actualizarInvitacion(fila);

        String jugador = equipoGestionDao.obtenerNombreJugador(fila.getJugadorId());
        String equipo = equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId());
                String motivoRechazo = fila.getMotivoRechazo();

        avisar(destinatarioInvitador(fila, convocatoria), usuarioAppId,
                datos(TIPO_RECHAZADA, convocatoria, partido, fila),
                idioma -> {
                    String base = textos.texto(idioma, "invitacion.rechazada.texto",
                            "jugador", jugador, "equipo", equipo, "rival", textoOVacio(partido.getRival()),
                            "fecha", fecha(idioma, partido));

                    // El motivo lo escribe coordinación: solo en la bandeja, nunca en el push.
                    return new TextoAviso(textos.texto(idioma, "invitacion.rechazada.titulo"),
                            textos.conMotivo(idioma, base, motivoRechazo), base);
                });

        return respuesta(fila);
    }

    /*
     * Tras el visto bueno (de coordinación o automático): si su equipo de origen no juega ese día queda autorizado; si
     * juega y su entrenador lo tiene convocado, queda pendiente de él; si juega y no lo tiene convocado, queda
     * autorizado y su entrenador recibe un aviso informativo.
     */
    private void resolverSolapamiento(ConvocatoriaJugador fila, Convocatoria convocatoria, Partido partido,
            Long actorId) {

        LocalDate dia = PlazoJustificacionPartido.dia(partido);

        List<PartidoDelDia> partidosOrigen = dia == null ? List.of()
                : invitacionDao.partidosDelEquipoEnDia(fila.getEquipoOrigenId(), dia);

        if (partidosOrigen.isEmpty()) {
            autorizar(fila, convocatoria, partido, actorId, AvisoEntrenadorOrigen.AUTORIZADA);
            return;
        }

        if (invitacionDao.filasPropiasDelJugadorEnDia(fila.getEquipoOrigenId(), fila.getJugadorId(), dia).isEmpty()) {
            autorizar(fila, convocatoria, partido, actorId, AvisoEntrenadorOrigen.INFORMATIVA);
            return;
        }

        pedirLiberacion(fila, convocatoria, partido, actorId, dia);
    }

    /* Pasa a pendiente de su entrenador (arranca el plazo de 12 horas) y se le pide que lo libere. */
    private void pedirLiberacion(ConvocatoriaJugador fila, Convocatoria convocatoria, Partido partido, Long actorId,
            LocalDate dia) {

        fila.setEstadoInvitacion(EstadoInvitacion.PENDIENTE_ENTRENADOR);
        fila.setFechaPeticionEntrenador(LocalDateTime.now(clock));
        fila.setAviso12hEnviado(false);

        convocatoriaJugadorDao.actualizarInvitacion(fila);

        String jugador = equipoGestionDao.obtenerNombreJugador(fila.getJugadorId());
        String destino = equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId());
        String origen = equipoGestionDao.obtenerNombreEquipo(fila.getEquipoOrigenId());

        Map<String, String> data = datos(TIPO_LIBERAR, convocatoria, partido, fila);
        // La app lleva al entrenador a su convocatoria de ese día para quitarlo: el partido del equipo de origen.
        invitacionDao.filasPropiasDelJugadorEnDia(fila.getEquipoOrigenId(), fila.getJugadorId(), dia).stream()
                .findFirst()
                .ifPresent(propia -> data.put("convocatoriaOrigenId", String.valueOf(propia.getConvocatoriaId())));

        avisar(destinatariosEquipo(fila.getEquipoOrigenId()), actorId, data,
                idioma -> {
                    Map<String, String> p = Map.of("destino", destino, "jugador", jugador,
                            "rival", textoOVacio(partido.getRival()), "fecha", fecha(idioma, partido),
                            "origen", origen);

                    return new TextoAviso(textos.texto(idioma, "invitacion.liberar.titulo"),
                            textos.texto(idioma, "invitacion.liberar.texto", p),
                            textos.texto(idioma, "invitacion.liberar.push", p));
                });
    }

    private enum AvisoEntrenadorOrigen {
        AUTORIZADA, INFORMATIVA, NINGUNO
    }

    /*
     * El invitado queda autorizado: solo ahora se avisa a su familia, al jugador y, según el caso, a su entrenador.
     */
    private void autorizar(ConvocatoriaJugador fila, Convocatoria convocatoria, Partido partido, Long actorId,
            AvisoEntrenadorOrigen aviso) {

        fila.setEstadoInvitacion(EstadoInvitacion.AUTORIZADO);
        fila.setFechaAutorizacion(LocalDateTime.now(clock));

        convocatoriaJugadorDao.actualizarInvitacion(fila);

        String jugador = equipoGestionDao.obtenerNombreJugador(fila.getJugadorId());
        String destino = equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId());
        String origen = equipoGestionDao.obtenerNombreEquipo(fila.getEquipoOrigenId());

        Map<String, String> data = datos(TIPO_AUTORIZADA, convocatoria, partido, fila);

        Equipacion principal = convocatoria.getEquipacionId() == null ? null
                : equipacionDao.obtenerPorId(convocatoria.getEquipacionId());
        Equipacion mochila = convocatoria.getEquipacionMochilaId() == null ? null
                : equipacionDao.obtenerPorId(convocatoria.getEquipacionMochilaId());

        Set<Long> directos = Set.copyOf(equipoGestionDao.obtenerUsuariosPorJugador(fila.getJugadorId()));

        for (Long usuarioId : usuariosDelJugador(fila.getJugadorId())) {

            boolean esJugador = directos.contains(usuarioId);

            avisar(List.of(usuarioId), actorId,
                    datosConvocatoria(convocatoria, partido, ConvocatoriaServiceImpl.ACCION_NUEVA),
                    idioma -> new TextoAviso(
                            textos.texto(idioma, "invitacion.convocado.titulo"),
                            TextosConvocatoria.componer(textos, idioma,
                                    textos.texto(idioma,
                                            (esJugador ? "invitacion.convocado.jugador" : "invitacion.convocado.familia")
                                                    + TextosConvocatoria.sufijoSinHora(partido.getHora()),
                                            "jugador", jugador, "destino", destino,
                                            "rival", textoOVacio(partido.getRival()),
                                            "fecha", fecha(idioma, partido),
                                            "hora", TextosConvocatoria.hora(textos, partido.getHora()),
                                            "horaCitacion", TextosConvocatoria.hora(textos, convocatoria.getHoraConvocatoria()),
                                            "lugar", textoOVacio(convocatoria.getLugarConvocatoria())),
                                    TextosConvocatoria.parrafoEquipaje(textos, idioma, principal, mochila))));
        }

        if (aviso == AvisoEntrenadorOrigen.AUTORIZADA) {

            avisar(destinatariosEquipo(fila.getEquipoOrigenId()), actorId, data,
                    idioma -> new TextoAviso(
                            textos.texto(idioma, "invitacion.autorizadaOrigen.titulo"),
                            textos.texto(idioma, "invitacion.autorizadaOrigen.texto",
                                    "jugador", jugador, "destino", destino,
                                    "rival", textoOVacio(partido.getRival()), "fecha", fecha(idioma, partido))));

        } else if (aviso == AvisoEntrenadorOrigen.INFORMATIVA) {

            avisar(destinatariosEquipo(fila.getEquipoOrigenId()), actorId,
                    datos(TIPO_INFORMATIVA, convocatoria, partido, fila),
                    idioma -> new TextoAviso(
                            textos.texto(idioma, "invitacion.informativa.titulo"),
                            textos.texto(idioma, "invitacion.informativa.texto",
                                    "jugador", jugador, "destino", destino, "origen", origen,
                                    "rival", textoOVacio(partido.getRival()), "fecha", fecha(idioma, partido))));
        }
    }

    // ==================================================================
    // Liberar
    // ==================================================================

    @Override
    @Transactional
    public InvitacionResponse liberar(Long usuarioAppId, Long convocatoriaJugadorId) {

        validarUsuario(usuarioAppId);

        ConvocatoriaJugador fila = invitacionExistente(convocatoriaJugadorId);

        boolean coordinacion = equipoGestionDao.esCoordinadorOAdmin(usuarioAppId);

        if (!coordinacion && !equipoGestionDao.puedeGestionarEquipo(usuarioAppId, fila.getEquipoOrigenId())) {
            throw new SecurityException("No tienes permiso para liberar a este jugador");
        }

        Convocatoria convocatoria = convocatoriaDao.obtenerPorId(fila.getConvocatoriaId());
        Partido partido = partidoBloqueado(convocatoria);

        fila = invitacionExistente(convocatoriaJugadorId);

        if (fila.getEstadoInvitacion() != EstadoInvitacion.PENDIENTE_ENTRENADOR) {
            throw new IllegalArgumentException("El jugador no está pendiente de que su entrenador lo autorice");
        }

        LocalDate dia = PlazoJustificacionPartido.dia(partido);

        if (dia != null) {

            InvitacionDetalle autorizada = autorizadaEseDia(fila.getJugadorId(), dia, fila.getId());

            if (autorizada != null) {
                throw conflictoYaInvitado(autorizada, equipoGestionDao.obtenerNombreJugador(fila.getJugadorId()), dia);
            }
        }

        quitarDeLaConvocatoriaDeOrigen(fila, dia, usuarioAppId,
                equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId()));

        autorizar(fila, convocatoria, partido, usuarioAppId, AvisoEntrenadorOrigen.NINGUNO);

        if (coordinacion) {

            String jugador = equipoGestionDao.obtenerNombreJugador(fila.getJugadorId());
            String destino = equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId());

            avisar(destinatariosEquipo(fila.getEquipoOrigenId()), usuarioAppId,
                    datos(TIPO_LIBERADA_COORDINACION, convocatoria, partido, fila),
                    idioma -> new TextoAviso(
                            textos.texto(idioma, "invitacion.liberadaCoordinacion.titulo"),
                            textos.texto(idioma, "invitacion.liberadaCoordinacion.texto",
                                    "jugador", jugador, "destino", destino, "fecha", fecha(idioma, partido))));
        }

        return respuesta(fila);
    }

    /* Lo quita de la convocatoria de su equipo ese día y avisa a su familia de que ya no está convocado con él. */
    private void quitarDeLaConvocatoriaDeOrigen(ConvocatoriaJugador invitacion, LocalDate dia, Long actorId,
            String destino) {

        if (dia == null) {
            return;
        }

        String jugador = equipoGestionDao.obtenerNombreJugador(invitacion.getJugadorId());
        String origen = equipoGestionDao.obtenerNombreEquipo(invitacion.getEquipoOrigenId());

        for (ConvocatoriaJugador propia : invitacionDao.filasPropiasDelJugadorEnDia(
                invitacion.getEquipoOrigenId(), invitacion.getJugadorId(), dia)) {

            convocatoriaJugadorDao.eliminarPorConvocatoriaYJugador(propia.getConvocatoriaId(), propia.getJugadorId());

            Convocatoria convocatoriaOrigen = convocatoriaDao.obtenerPorId(propia.getConvocatoriaId());
            Partido partidoOrigen = convocatoriaOrigen == null ? null
                    : partidoDao.obtenerPorId(convocatoriaOrigen.getPartidoId());

            // Una fila propia siempre cuelga de una convocatoria con partido; sin él no hay nada que contar.
            if (partidoOrigen == null) {
                continue;
            }

            Set<Long> directos = Set.copyOf(equipoGestionDao.obtenerUsuariosPorJugador(invitacion.getJugadorId()));

            for (Long usuarioId : usuariosDelJugador(invitacion.getJugadorId())) {

                boolean esJugador = directos.contains(usuarioId);

                avisar(List.of(usuarioId), actorId,
                        datosConvocatoria(convocatoriaOrigen, partidoOrigen, ConvocatoriaServiceImpl.ACCION_QUITADO),
                        idioma -> new TextoAviso(
                                textos.texto(idioma, "invitacion.liberado.titulo"),
                                textos.texto(idioma,
                                        esJugador ? "invitacion.liberado.jugador" : "invitacion.liberado.familia",
                                        "jugador", jugador, "origen", origen, "destino", destino,
                                        "rival", textoOVacio(partidoOrigen.getRival()),
                                        "fecha", fecha(idioma, partidoOrigen))));
            }
        }
    }

    @Override
    @Transactional
    public void alQuitarJugadoresPropios(Long usuarioAppId, Long equipoOrigenId, LocalDate dia,
            Collection<Long> jugadoresIds) {

        if (dia == null || jugadoresIds == null || jugadoresIds.isEmpty()) {
            return;
        }

        for (Long jugadorId : jugadoresIds) {

            for (InvitacionDetalle pendiente : invitacionDao.delJugadorEnDia(jugadorId, dia, null)) {

                if (pendiente.estado() != EstadoInvitacion.PENDIENTE_ENTRENADOR
                        || !equipoOrigenId.equals(pendiente.equipoOrigenId())) {
                    continue;
                }

                // Si mientras tanto otro equipo lo tiene ya autorizado ese día, esta se queda como está.
                if (autorizadaEseDia(jugadorId, dia, pendiente.id()) != null) {
                    continue;
                }

                ConvocatoriaJugador fila = convocatoriaJugadorDao.obtenerPorId(pendiente.id());
                Convocatoria convocatoria = convocatoriaDao.obtenerPorId(pendiente.convocatoriaId());
                Partido partido = partidoDao.obtenerPorId(pendiente.partidoId());

                if (fila != null && convocatoria != null && partido != null) {
                    autorizar(fila, convocatoria, partido, usuarioAppId, AvisoEntrenadorOrigen.NINGUNO);
                }
            }
        }
    }

    // ==================================================================
    // Desestimar (familia)
    // ==================================================================

    @Override
    @Transactional
    public JustificacionFaltaPartidoResponse desestimar(Long usuarioAppId, Long partidoId, Long jugadorId,
            String motivo) {

        validarUsuario(usuarioAppId);

        if (partidoId == null || partidoId <= 0) {
            throw new IllegalArgumentException("El partido no es válido");
        }

        if (jugadorId == null || jugadorId <= 0) {
            throw new IllegalArgumentException("El jugador es obligatorio");
        }

        Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(partidoId);

        ConvocatoriaJugador fila = convocatoria == null ? null
                : convocatoriaJugadorDao.obtenerPorConvocatoriaYJugador(convocatoria.getId(), jugadorId);

        if (fila == null || !fila.isInvitado() || fila.getEstadoInvitacion() != EstadoInvitacion.AUTORIZADO) {
            throw new IllegalArgumentException("El jugador no es un invitado autorizado en este partido");
        }

        JustificacionFaltaRequest request = new JustificacionFaltaRequest();
        request.setJugadorId(jugadorId);
        request.setMotivo(motivo);

        // El vínculo, el plazo (hasta la hora de convocatoria), el motivo y el aviso al entrenador que lo convocó
        // (sin el motivo en el push) los resuelve la justificación de ausencia.
        return justificacionService.justificar(usuarioAppId, partidoId, request);
    }

    // ==================================================================
    // Consultas para la convocatoria y el calendario
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ConvocatoriaJugadorResponse> invitadosSinAutorizar(Long convocatoriaId) {

        List<ConvocatoriaJugadorResponse> respuesta = new ArrayList<>();

        for (ConvocatoriaJugador fila : convocatoriaJugadorDao.obtenerTodasPorConvocatoria(convocatoriaId)) {

            if (fila.isInvitado() && fila.getEstadoInvitacion() != EstadoInvitacion.AUTORIZADO) {

                ConvocatoriaJugadorResponse jugador = new ConvocatoriaJugadorResponse(
                        fila.getJugadorId(), equipoGestionDao.obtenerNombreJugador(fila.getJugadorId()));

                jugador.setInvitado(true);
                jugador.setEquipoOrigenId(fila.getEquipoOrigenId());
                jugador.setEquipoOrigen(equipoGestionDao.obtenerNombreEquipo(fila.getEquipoOrigenId()));
                jugador.setEstadoInvitacion(fila.getEstadoInvitacion().name());
                jugador.setMotivoRechazo(fila.getMotivoRechazo());

                respuesta.add(jugador);
            }
        }

        return respuesta;
    }

    @Override
    @Transactional(readOnly = true)
    public void comprobarNoInvitadoEseDia(Collection<Long> jugadoresIds, Partido partido) {

        LocalDate dia = PlazoJustificacionPartido.dia(partido);

        if (dia == null || jugadoresIds == null) {
            return;
        }

        for (Long jugadorId : jugadoresIds) {

            InvitacionDetalle autorizada = autorizadaEseDia(jugadorId, dia, null);

            if (autorizada != null && !autorizada.equipoDestinoId().equals(partido.getEquipoId())) {

                String jugador = equipoGestionDao.obtenerNombreJugador(jugadorId);

                throw new ConflictoInvitacionException(
                        ConflictoInvitacionException.JUGADOR_INVITADO_ESE_DIA,
                        jugador + " ya está convocado como invitado con " + autorizada.equipoDestino() + " el "
                                + FECHA.format(dia) + ": no puedes convocarlo ese día.",
                        jugadorId, autorizada.equipoDestinoId(), autorizada.equipoDestino(), dia);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void comprobarSinInvitadosAlCambiarPartido(Long convocatoriaId) {

        boolean hayInvitados = convocatoriaJugadorDao.obtenerTodasPorConvocatoria(convocatoriaId).stream()
                .anyMatch(fila -> fila.isInvitado() && fila.getEstadoInvitacion() != EstadoInvitacion.RECHAZADO);

        if (hayInvitados) {
            throw new IllegalArgumentException("La convocatoria tiene jugadores de otros equipos: quítalos antes de"
                    + " cambiar el partido y vuelve a invitarlos después");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Partido> partidosComoInvitado(Long jugadorId, LocalDate desde, LocalDate hasta) {

        List<Partido> partidos = new ArrayList<>();

        for (InvitacionDetalle invitacion : invitacionDao.autorizadasDelJugador(jugadorId, desde, hasta)) {

            Partido partido = partidoDao.obtenerPorId(invitacion.partidoId());

            if (partido != null) {
                partidos.add(partido);
            }
        }

        return partidos;
    }

    // ==================================================================
    // Si se cancela algo
    // ==================================================================

    @Override
    @Transactional
    public void alEliminarConvocatoria(Convocatoria convocatoria, Partido partido) {

        if (partido == null) {
            return;
        }

        for (ConvocatoriaJugador fila : convocatoriaJugadorDao.obtenerTodasPorConvocatoria(convocatoria.getId())) {

            if (fila.isInvitado() && (fila.getEstadoInvitacion() == EstadoInvitacion.AUTORIZADO
                    || fila.getEstadoInvitacion() == EstadoInvitacion.PENDIENTE_ENTRENADOR)) {

                anular(fila, convocatoria, partido, "invitacion.anulada.borrada.titulo");
            }
        }
    }

    @Override
    @Transactional
    public void alCancelarPartido(Partido partido) {

        Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(partido.getId());

        if (convocatoria == null) {
            return;
        }

        for (ConvocatoriaJugador fila : convocatoriaJugadorDao.obtenerTodasPorConvocatoria(convocatoria.getId())) {

            if (fila.isInvitado() && (fila.getEstadoInvitacion() == EstadoInvitacion.AUTORIZADO
                    || fila.getEstadoInvitacion() == EstadoInvitacion.PENDIENTE_ENTRENADOR)) {

                anular(fila, convocatoria, partido, "invitacion.anulada.cancelado.titulo");
            }
        }
    }

    /*
     * Avisa de que el invitado deja de jugar este partido: a su familia (y a él) si ya estaba autorizado, y a su
     * entrenador si ya se le había contado (autorizado o pendiente de él).
     */
    private void anular(ConvocatoriaJugador fila, Convocatoria convocatoria, Partido partido, String claveTitulo) {

        anular(fila, convocatoria, partido, PlazoJustificacionPartido.dia(partido), claveTitulo);
    }

    /* dia: el día en que se jugaba el partido que se anula (si el partido cambia de fecha, el antiguo). */
    private void anular(ConvocatoriaJugador fila, Convocatoria convocatoria, Partido partido, LocalDate dia,
            String claveTitulo) {

        String jugador = equipoGestionDao.obtenerNombreJugador(fila.getJugadorId());
        String destino = equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId());
        String rival = textoOVacio(partido.getRival());

        Map<String, String> data = datos(TIPO_ANULADA, convocatoria, partido, fila);
        data.remove("convocatoriaId");

        if (dia != null) {
            data.put("fechaPartido", dia.toString());
        }

        if (fila.getEstadoInvitacion() == EstadoInvitacion.AUTORIZADO) {

            Set<Long> directos = Set.copyOf(equipoGestionDao.obtenerUsuariosPorJugador(fila.getJugadorId()));

            for (Long usuarioId : usuariosDelJugador(fila.getJugadorId())) {

                boolean esJugador = directos.contains(usuarioId);

                avisar(List.of(usuarioId), convocatoria.getUsuarioEntrenadorId(),
                        dia == null ? Map.of("tipo", TIPO_ANULADA, "partidoId", String.valueOf(partido.getId()))
                                : Map.of("tipo", TIPO_ANULADA, "partidoId", String.valueOf(partido.getId()),
                                        "fechaPartido", dia.toString()),
                        idioma -> textoAnulada(idioma, claveTitulo, esJugador, jugador, destino, rival, dia));
            }
        }

        // Al entrenador de su equipo de origen le sirve el mismo texto que a la familia.
        avisar(destinatariosEquipo(fila.getEquipoOrigenId()), convocatoria.getUsuarioEntrenadorId(), data,
                idioma -> textoAnulada(idioma, claveTitulo, false, jugador, destino, rival, dia));
    }

    private TextoAviso textoAnulada(String idioma, String claveTitulo, boolean esJugador, String jugador,
            String destino, String rival, LocalDate dia) {

        return new TextoAviso(
                textos.texto(idioma, claveTitulo),
                textos.texto(idioma, esJugador ? "invitacion.anulada.jugador" : "invitacion.anulada.familia",
                        "jugador", jugador, "destino", destino, "rival", rival, "fecha", textos.fecha(idioma, dia)));
    }

    // ==================================================================
    // Si cambia la fecha de un partido
    // ==================================================================

    @Override
    @Transactional
    public void alCambiarFechaPartido(Partido partido, LocalDate diaAntiguo, LocalDate diaNuevo) {

        if (partido == null || diaAntiguo == null || diaNuevo == null || diaAntiguo.equals(diaNuevo)) {
            return;
        }

        anularInvitacionesDelPartido(partido, diaAntiguo, diaNuevo);
        reevaluarSolapamientosDeOrigen(partido, diaAntiguo, diaNuevo);
    }

    /*
     * El partido es el de destino: las invitaciones que no estaban rechazadas se anulan como al quitar al invitado, y
     * quien invitó recibe un único aviso para que, si quiere, vuelva a invitarlos para la fecha nueva.
     */
    private void anularInvitacionesDelPartido(Partido partido, LocalDate diaAntiguo, LocalDate diaNuevo) {

        Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(partido.getId());

        if (convocatoria == null) {
            return;
        }

        Map<Long, List<String>> nombresPorInvitador = new LinkedHashMap<>();
        Map<Long, List<Long>> idsPorInvitador = new LinkedHashMap<>();

        for (ConvocatoriaJugador fila : convocatoriaJugadorDao.obtenerTodasPorConvocatoria(convocatoria.getId())) {

            if (!fila.isInvitado() || fila.getEstadoInvitacion() == EstadoInvitacion.RECHAZADO) {
                continue;
            }

            convocatoriaJugadorDao.eliminarPorConvocatoriaYJugador(convocatoria.getId(), fila.getJugadorId());

            // Si ya estaba autorizado, también se avisa a su familia; a su entrenador, si ya se le había pedido algo.
            if (fila.getEstadoInvitacion() == EstadoInvitacion.AUTORIZADO
                    || fila.getEstadoInvitacion() == EstadoInvitacion.PENDIENTE_ENTRENADOR) {

                anular(fila, convocatoria, partido, diaAntiguo, "invitacion.anulada.aplazado.titulo");
            }

            Long invitador = fila.getUsuarioInvitaId() != null ? fila.getUsuarioInvitaId()
                    : convocatoria.getUsuarioEntrenadorId();

            if (invitador != null) {
                nombresPorInvitador.computeIfAbsent(invitador, k -> new ArrayList<>())
                        .add(equipoGestionDao.obtenerNombreJugador(fila.getJugadorId()));
                idsPorInvitador.computeIfAbsent(invitador, k -> new ArrayList<>()).add(fila.getJugadorId());
            }
        }

        String destino = equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId());

        for (Map.Entry<Long, List<String>> entrada : nombresPorInvitador.entrySet()) {

            Map<String, String> data = new LinkedHashMap<>();
            data.put("tipo", TIPO_FECHA_CAMBIADA);
            data.put("convocatoriaId", String.valueOf(convocatoria.getId()));
            data.put("partidoId", String.valueOf(partido.getId()));
            data.put("fechaPartido", diaNuevo.toString());
            data.put("fechaAnterior", diaAntiguo.toString());
            data.put("jugadorIds", idsPorInvitador.get(entrada.getKey()).stream().map(String::valueOf)
                    .collect(Collectors.joining(",")));

            avisar(List.of(entrada.getKey()), null, data,
                    idioma -> {
                        Map<String, String> p = Map.of("destino", destino,
                                "rival", textoOVacio(partido.getRival()),
                                "fechaAntigua", textos.fecha(idioma, diaAntiguo),
                                "fechaNueva", textos.fecha(idioma, diaNuevo),
                                "nombres", textos.lista(idioma, entrada.getValue()));

                        return new TextoAviso(textos.texto(idioma, "invitacion.aplazadas.titulo"),
                                textos.texto(idioma, "invitacion.aplazadas.texto", p),
                                textos.texto(idioma, "invitacion.aplazadas.push", p));
                    });
        }
    }

    /*
     * El partido es el del equipo de origen: las invitaciones pendientes de su entrenador o autorizadas de ese equipo
     * cuyo partido de destino cae en la fecha antigua o en la nueva vuelven a pasar por la regla del solapamiento.
     */
    private void reevaluarSolapamientosDeOrigen(Partido partido, LocalDate diaAntiguo, LocalDate diaNuevo) {

        for (InvitacionDetalle invitacion : invitacionDao.deOrigenEnDias(partido.getEquipoId(), diaAntiguo, diaNuevo)) {

            ConvocatoriaJugador fila = convocatoriaJugadorDao.obtenerPorId(invitacion.id());
            Convocatoria convocatoria = convocatoriaDao.obtenerPorId(invitacion.convocatoriaId());
            Partido destino = partidoDao.obtenerPorId(invitacion.partidoId());

            if (fila == null || convocatoria == null || destino == null || invitacion.dia() == null) {
                continue;
            }

            LocalDate dia = invitacion.dia();

            boolean juega = !invitacionDao.partidosDelEquipoEnDia(fila.getEquipoOrigenId(), dia).isEmpty();
            boolean convocado = juega && !invitacionDao.filasPropiasDelJugadorEnDia(
                    fila.getEquipoOrigenId(), fila.getJugadorId(), dia).isEmpty();

            if (!convocado) {

                // Ya no hay nada que liberar: lo que esperaba a su entrenador queda autorizado.
                if (fila.getEstadoInvitacion() == EstadoInvitacion.PENDIENTE_ENTRENADOR
                        && autorizadaEseDia(fila.getJugadorId(), dia, fila.getId()) == null) {

                    autorizar(fila, convocatoria, destino, null,
                            juega ? AvisoEntrenadorOrigen.INFORMATIVA : AvisoEntrenadorOrigen.AUTORIZADA);
                }

                continue;
            }

            // Su equipo juega ese día y lo tiene convocado: si ya estaba autorizado vuelve a esperar a su entrenador.
            if (fila.getEstadoInvitacion() == EstadoInvitacion.AUTORIZADO) {

                pedirLiberacion(fila, convocatoria, destino, null, dia);

                String jugador = equipoGestionDao.obtenerNombreJugador(fila.getJugadorId());
                String nombreDestino = equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId());
                String origen = equipoGestionDao.obtenerNombreEquipo(fila.getEquipoOrigenId());
                Set<Long> directos = Set.copyOf(equipoGestionDao.obtenerUsuariosPorJugador(fila.getJugadorId()));

                for (Long usuarioId : usuariosDelJugador(fila.getJugadorId())) {

                    boolean esJugador = directos.contains(usuarioId);

                    avisar(List.of(usuarioId), null, datos(TIPO_SOLAPAMIENTO, convocatoria, destino, fila),
                            idioma -> new TextoAviso(
                                    textos.texto(idioma, "invitacion.enEspera.titulo"),
                                    textos.texto(idioma,
                                            esJugador ? "invitacion.enEspera.jugador" : "invitacion.enEspera.familia",
                                            "jugador", jugador, "destino", nombreDestino, "origen", origen,
                                            "fecha", textos.fecha(idioma, dia))));
                }
            }
        }
    }

    // ==================================================================
    // Plazo de 12 horas
    // ==================================================================

    @Override
    @Transactional
    public int avisarPlazosVencidos() {

        LocalDateTime limite = LocalDateTime.now(clock).minus(PLAZO_ENTRENADOR);

        int avisadas = 0;

        for (InvitacionDetalle invitacion : invitacionDao.vencidasSinAviso(limite)) {

            ConvocatoriaJugador fila = convocatoriaJugadorDao.obtenerPorId(invitacion.id());

            if (fila == null) {
                continue;
            }

            // Se marca como avisada se haya encontrado o no a quién avisar, para no reintentarlo cada 15 minutos.
            fila.setAviso12hEnviado(true);
            convocatoriaJugadorDao.actualizarInvitacion(fila);

            Map<String, String> data = new LinkedHashMap<>();
            data.put("tipo", TIPO_PLAZO);
            data.put("invitacionId", String.valueOf(invitacion.id()));
            data.put("partidoId", String.valueOf(invitacion.partidoId()));
            data.put("jugadorId", String.valueOf(invitacion.jugadorId()));

            if (invitacion.dia() != null) {
                data.put("fechaPartido", invitacion.dia().toString());
            }

            avisar(destinatariosCoordinacion(null), null, data,
                    idioma -> {
                        Map<String, String> p = Map.of("origen", textoOVacio(invitacion.equipoOrigen()),
                                "jugador", textoOVacio(invitacion.jugador()),
                                "destino", textoOVacio(invitacion.equipoDestino()),
                                "fecha", textos.fecha(idioma, invitacion.dia()));

                        return new TextoAviso(textos.texto(idioma, "invitacion.plazo.titulo"),
                                textos.texto(idioma, "invitacion.plazo.texto", p),
                                textos.texto(idioma, "invitacion.plazo.push", p));
                    });

            avisadas++;
        }

        if (avisadas > 0) {
            logger.info("Invitaciones con el plazo de 12 horas vencido avisadas a coordinación: {}", avisadas);
        }

        return avisadas;
    }

    // ==================================================================
    // Avisos
    // ==================================================================

    private void avisarCoordinacion(Long actorId, ConvocatoriaJugador fila, Convocatoria convocatoria,
            Partido partido) {

        String jugador = equipoGestionDao.obtenerNombreJugador(fila.getJugadorId());
        String destino = equipoGestionDao.obtenerNombreEquipo(convocatoria.getEquipoId());
        String origen = equipoGestionDao.obtenerNombreEquipo(fila.getEquipoOrigenId());

        avisar(destinatariosCoordinacion(actorId), actorId, datos(TIPO_PENDIENTE, convocatoria, partido, fila),
                idioma -> {
                    Map<String, String> p = Map.of("destino", destino, "jugador", jugador, "origen", origen,
                            "rival", textoOVacio(partido.getRival()), "fecha", fecha(idioma, partido));

                    return new TextoAviso(textos.texto(idioma, "invitacion.pendiente.titulo"),
                            textos.texto(idioma, "invitacion.pendiente.texto", p),
                            textos.texto(idioma, "invitacion.pendiente.push", p));
                });
    }

    /**
     * Envía un aviso privado (bandeja + push tras el commit) a cada destinatario, sin repetidos ni al propio actor. Una
     * comunicación por idioma: cada destinatario recibe el texto en el idioma de su cuenta.
     */
    private void avisar(Collection<Long> destinatarios, Long remitenteId, Map<String, String> data,
            Function<String, TextoAviso> texto) {

        Set<Long> destinos = new LinkedHashSet<>(destinatarios);

        // Quien provoca el cambio ya lo sabe: no se avisa a sí mismo.
        if (remitenteId != null) {
            destinos.remove(remitenteId);
        }

        if (destinos.isEmpty()) {
            return;
        }

        Long remitente = remitenteId != null ? remitenteId : destinos.iterator().next();

        idiomas.crearPrivadaPorIdioma(comunicacionService, List.copyOf(destinos), remitente, data, texto);
    }

    private List<Long> destinatariosCoordinacion(Long excluir) {

        Set<Long> coordinacion = new LinkedHashSet<>(equipoGestionDao.obtenerCoordinadores());
        coordinacion.addAll(equipoGestionDao.obtenerAdministradoresApp());

        if (excluir != null) {
            coordinacion.remove(excluir);
        }

        return List.copyOf(coordinacion);
    }

    /* El cuerpo técnico del equipo; sin ningún entrenador con cuenta, coordinación. */
    private List<Long> destinatariosEquipo(Long equipoId) {

        List<Long> entrenadores = equipoGestionDao.obtenerEntrenadoresPorEquipo(equipoId);

        return entrenadores.isEmpty() ? destinatariosCoordinacion(null) : entrenadores;
    }

    private List<Long> destinatarioInvitador(ConvocatoriaJugador fila, Convocatoria convocatoria) {

        Long invitador = fila.getUsuarioInvitaId() != null ? fila.getUsuarioInvitaId()
                : convocatoria.getUsuarioEntrenadorId();

        return invitador == null ? List.of() : List.of(invitador);
    }

    /* El jugador y sus familiares, una sola vez cada uno. */
    private Set<Long> usuariosDelJugador(Long jugadorId) {

        Set<Long> usuarios = new LinkedHashSet<>(equipoGestionDao.obtenerUsuariosPorJugador(jugadorId));
        usuarios.addAll(equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(jugadorId));

        return usuarios;
    }

    private Map<String, String> datos(String tipo, Convocatoria convocatoria, Partido partido,
            ConvocatoriaJugador fila) {

        Map<String, String> data = new LinkedHashMap<>();

        data.put("tipo", tipo);
        data.put("invitacionId", String.valueOf(fila.getId()));
        data.put("convocatoriaId", String.valueOf(convocatoria.getId()));
        data.put("partidoId", String.valueOf(partido.getId()));
        data.put("jugadorId", String.valueOf(fila.getJugadorId()));

        String fecha = fechaIso(partido);

        if (!fecha.isEmpty()) {
            data.put("fechaPartido", fecha);
        }

        return data;
    }

    /* El data de una convocatoria normal: la app abre el detalle o el calendario en ese día. */
    private Map<String, String> datosConvocatoria(Convocatoria convocatoria, Partido partido, String accion) {

        Map<String, String> data = new LinkedHashMap<>();

        data.put("tipo", TIPO_PUSH_CONVOCATORIA);
        data.put("accion", accion);
        data.put("convocatoriaId", String.valueOf(convocatoria.getId()));
        data.put("partidoId", String.valueOf(partido.getId()));

        String fecha = fechaIso(partido);

        if (!fecha.isEmpty()) {
            data.put("fechaPartido", fecha);
        }

        return data;
    }

    // ==================================================================
    // Validaciones y utilidades
    // ==================================================================

    private static void validarUsuario(Long usuarioAppId) {

        if (usuarioAppId == null || usuarioAppId <= 0) {
            throw new SecurityException("Usuario no válido");
        }
    }

    private void comprobarCoordinacion(Long usuarioAppId) {

        validarUsuario(usuarioAppId);

        if (!equipoGestionDao.esCoordinadorOAdmin(usuarioAppId)) {
            throw new SecurityException("Solo coordinación puede gestionar las invitaciones");
        }
    }

    private Partido partidoParaGestionar(Long usuarioAppId, Long partidoId) {

        validarUsuario(usuarioAppId);

        if (partidoId == null || partidoId <= 0) {
            throw new IllegalArgumentException("El partido no es válido");
        }

        Partido partido = partidoDao.obtenerPorId(partidoId);

        if (partido == null) {
            throw new IllegalArgumentException("El partido no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(usuarioAppId, partido.getEquipoId())) {
            throw new SecurityException("No tienes permiso para gestionar este equipo");
        }

        return partido;
    }

    /* La invitación (fila de CONVOCATORIA_JUGADOR de un invitado) o 400 si no existe. */
    private ConvocatoriaJugador invitacionExistente(Long convocatoriaJugadorId) {

        if (convocatoriaJugadorId == null || convocatoriaJugadorId <= 0) {
            throw new IllegalArgumentException("La invitación no es válida");
        }

        ConvocatoriaJugador fila = convocatoriaJugadorDao.obtenerPorId(convocatoriaJugadorId);

        if (fila == null || !fila.isInvitado()) {
            throw new IllegalArgumentException("La invitación no existe");
        }

        return fila;
    }

    /* El partido de la convocatoria, bloqueado: serializa con la edición de la convocatoria y las justificaciones. */
    private Partido partidoBloqueado(Convocatoria convocatoria) {

        if (convocatoria == null || convocatoria.getPartidoId() == null) {
            throw new IllegalArgumentException("La convocatoria de la invitación no existe");
        }

        partidoDao.bloquearPorId(convocatoria.getPartidoId());

        Partido partido = partidoDao.obtenerPorId(convocatoria.getPartidoId());

        if (partido == null) {
            throw new IllegalArgumentException("El partido no existe");
        }

        return partido;
    }

    private void validarEquipoOrigen(Long equipoDestinoId, Long equipoOrigenId) {

        ReglaInvitados.EquipoRegla destino = reglaDelEquipo(equipoDestinoId);

        for (Equipo equipo : equipoDao.obtenerActivos()) {

            if (equipo.getId().equals(equipoOrigenId)) {

                if (regla.puedeInvitar(destino, ReglaInvitados.EquipoRegla.de(equipo, esPretemporada(equipo)),
                        categoriaEdadDao.obtenerTodas())) {
                    return;
                }

                break;
            }
        }

        throw new IllegalArgumentException("Este equipo no puede invitar a jugadores de ese equipo");
    }

    private ReglaInvitados.EquipoRegla reglaDelEquipo(Long equipoId) {

        for (Equipo equipo : equipoDao.obtenerActivos()) {

            if (equipo.getId().equals(equipoId)) {
                return ReglaInvitados.EquipoRegla.de(equipo, esPretemporada(equipo));
            }
        }

        Equipo equipo = equipoDao.obtenerEquipoPorId(equipoId);

        if (equipo == null) {
            throw new IllegalArgumentException("El equipo no existe");
        }

        // No está activo en la temporada: no participa.
        return new ReglaInvitados.EquipoRegla(equipo.getId(), equipo.getCategoria(), "P", true);
    }

    private static boolean esPretemporada(Equipo equipo) {
        return "PRETEMPORADA".equals(equipo.getTipoTemporada());
    }

    private Long temporadaActivaId() {

        Temporada temporada = temporadaDao.obtenerTemporadaActiva();

        if (temporada == null) {
            throw new IllegalArgumentException("No hay temporada activa");
        }

        return temporada.getId();
    }

    /* La invitación AUTORIZADA del jugador ese día (otra distinta de la excluida), o null. */
    private InvitacionDetalle autorizadaEseDia(Long jugadorId, LocalDate dia, Long excluirId) {

        return invitacionDao.delJugadorEnDia(jugadorId, dia, excluirId).stream()
                .filter(invitacion -> invitacion.estado() == EstadoInvitacion.AUTORIZADO)
                .findFirst()
                .orElse(null);
    }

    private ConflictoInvitacionException conflictoYaInvitado(InvitacionDetalle autorizada, String jugador,
            LocalDate dia) {

        return new ConflictoInvitacionException(
                ConflictoInvitacionException.JUGADOR_YA_INVITADO_ESE_DIA,
                jugador + " ya está invitado con " + autorizada.equipoDestino() + " el " + FECHA.format(dia)
                        + ": no se le puede invitar con otro equipo ese día.",
                autorizada.jugadorId(), autorizada.equipoDestinoId(), autorizada.equipoDestino(), dia);
    }

    private InvitacionResponse respuesta(ConvocatoriaJugador fila) {

        Convocatoria convocatoria = convocatoriaDao.obtenerPorId(fila.getConvocatoriaId());

        return new InvitacionResponse(
                fila.getId(),
                fila.getConvocatoriaId(),
                convocatoria != null ? convocatoria.getPartidoId() : null,
                fila.getJugadorId(),
                equipoGestionDao.obtenerNombreJugador(fila.getJugadorId()),
                fila.getEquipoOrigenId(),
                equipoGestionDao.obtenerNombreEquipo(fila.getEquipoOrigenId()),
                fila.getEstadoInvitacion().name(),
                fila.getMotivoRechazo());
    }

    /* El nombre de quien invitó o, si su cuenta no lo tiene, el cuerpo técnico del equipo de destino. */
    private String quienConvoca(InvitacionDetalle invitacion) {

        if (invitacion.usuarioInvitaId() != null) {

            UsuarioApp usuario = usuarioAppDao.obtenerPorId(invitacion.usuarioInvitaId().intValue());

            if (usuario != null) {

                String nombre = nombreCompleto(usuario.getNombre(), usuario.getApellidos());

                if (!nombre.isBlank()) {
                    return nombre;
                }
            }
        }

        return "Cuerpo técnico de " + invitacion.equipoDestino();
    }

    private static String nombreCompleto(String nombre, String apellidos) {

        String n = nombre == null ? "" : nombre.trim();
        String a = apellidos == null ? "" : apellidos.trim();

        return (n + " " + a).trim();
    }

    private static String textoFecha(Partido partido) {

        LocalDate dia = PlazoJustificacionPartido.dia(partido);

        return dia == null ? "sin fecha" : FECHA.format(dia);
    }

    private static String fechaIso(Partido partido) {

        LocalDate dia = PlazoJustificacionPartido.dia(partido);

        return dia == null ? "" : dia.toString();
    }

    private String fecha(String idioma, Partido partido) {
        return textos.fecha(idioma, PlazoJustificacionPartido.dia(partido));
    }

    private static String textoOVacio(String valor) {
        return Objects.toString(valor, "");
    }
}
