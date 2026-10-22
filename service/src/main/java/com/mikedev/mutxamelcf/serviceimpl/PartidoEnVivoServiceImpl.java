package com.mikedev.mutxamelcf.serviceimpl;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse.Convocado;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse.EventoEnVivo;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse.PartidoDisponible;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse.UltimoEventoDeshacible;
import com.mikedev.mutxamelcf.model.JugadorBasico;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugador;
import com.mikedev.mutxamelcf.model.PartidoLiveEvento;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.PartidoEnVivoService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@Service
public class PartidoEnVivoServiceImpl implements PartidoEnVivoService {

    private static final Logger logger = LoggerFactory.getLogger(PartidoEnVivoServiceImpl.class);

    /*
     * Los avisos del partido en directo caen dentro de la categoría de
     * preferencias "Resultados" (ver PreferenciasNotificacionService):
     * quien la tenga desactivada no debe recibirlos.
     */
    private static final String TIPO_PUSH = "RESULTADO";

    /*
     * Topic de FCM para quien sigue la retransmisión sin tener cuenta
     * en la app (suscripción anónima gestionada por la propia app). La
     * app se encarga de desuscribir a los usuarios con cuenta al
     * iniciar sesión, para que no reciban el aviso duplicado.
     */
    private static final String TOPIC_RESULTADOS = "resultados";

    /*
     * En EQUIPO, la fila del primer equipo tiene NOMBRE = "Primer Equipo"
     * (no "Mutxamel CF", que es el nombre del club, no de esa fila).
     */
    private static final String EQUIPO_PRIMER_EQUIPO = "Primer Equipo";

    /*
     * BE-02: si enviarGolFavor() tarda en responder (el push a todos los
     * usuarios podía superar el timeout de la app), el retransmisor podía
     * reintentar la misma acción y sumar el mismo gol dos veces. Se descarta
     * como reintento cualquier gol del mismo autor que llegue dentro de esta
     * ventana desde el anterior (no anulado): marcar dos veces el mismo
     * jugador en menos de esto es prácticamente imposible en fútbol.
     */
    private static final Duration VENTANA_DEDUPLICACION_GOL = Duration.ofSeconds(15);

    /** Tiempo durante el que se puede deshacer el último evento. */
    static final Duration VENTANA_DESHACER = Duration.ofMinutes(2);

    static final int AUTOR_TEXTO_MAXIMO = 60;
    static final String PROPIA_PUERTA = "p.p.";

    private final PartidoLiveEventoDao eventoDao;
    private final PartidoDao partidoDao;
    private final EquipoDao equipoDao;
    private final EquipoGestionDao equipoGestionDao;
    private final ConvocatoriaDao convocatoriaDao;
    private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
    private final PartidoEstadisticaJugadorDao estadisticaJugadorDao;
    private final NotificacionAppService notificacionAppService;
    private final UsuarioAppService usuarioAppService;
    private final FcmPushService fcmPushService;
    private final Clock clock;

    public PartidoEnVivoServiceImpl(
            PartidoLiveEventoDao eventoDao,
            PartidoDao partidoDao,
            EquipoDao equipoDao,
            EquipoGestionDao equipoGestionDao,
            ConvocatoriaDao convocatoriaDao,
            ConvocatoriaJugadorDao convocatoriaJugadorDao,
            PartidoEstadisticaJugadorDao estadisticaJugadorDao,
            NotificacionAppService notificacionAppService,
            UsuarioAppService usuarioAppService,
            FcmPushService fcmPushService,
            Clock clock) {

        this.eventoDao = eventoDao;
        this.partidoDao = partidoDao;
        this.equipoDao = equipoDao;
        this.equipoGestionDao = equipoGestionDao;
        this.convocatoriaDao = convocatoriaDao;
        this.convocatoriaJugadorDao = convocatoriaJugadorDao;
        this.estadisticaJugadorDao = estadisticaJugadorDao;
        this.notificacionAppService = notificacionAppService;
        this.usuarioAppService = usuarioAppService;
        this.fcmPushService = fcmPushService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Override
    public void enviarAlineacion(Long usuarioId, String onceInicial, String suplentes) {

        validarRolRetransmision(usuarioId);

        String mensaje = "Once inicial: " + onceInicial + "\n\nSuplentes: " + suplentes;

        difundir("📋 Alineación del Mutxamel CF", mensaje);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartidoDisponible> obtenerPartidosDisponibles(Long usuarioId) {

        validarRolRetransmision(usuarioId);

        Equipo primerEquipo = equipoDao.obtenerEquipoPorNombre(EQUIPO_PRIMER_EQUIPO);

        if (primerEquipo == null) {
            return List.of();
        }

        LocalDate hoy = LocalDate.now(clock);

        return partidoDao.obtenerPorEquipoYRangoFechas(primerEquipo.getId(), hoy, hoy).stream()
                .filter(partido -> !partido.isCancelado())
                .sorted(Comparator
                        .comparing(Partido::getHora, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Partido::getId))
                .map(partido -> new PartidoDisponible(
                        partido.getId(), partido.getRival(), partido.getHora(), partido.getCampo(),
                        partido.isLocal(), aLocalDate(partido.getDia())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoPartidoEnVivoResponse obtenerEstado(Long usuarioId, Long partidoId) {

        validarRolRetransmision(usuarioId);

        // Consultar el estado no exige que sea hoy: la pantalla puede seguir
        // abierta pasada la medianoche.
        Partido partido = validarPartido(partidoId, false);

        return construirEstado(partido, eventoDao.obtenerNoAnuladosPorPartido(partidoId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Convocado> obtenerConvocados(Long usuarioId, Long partidoId) {

        validarRolRetransmision(usuarioId);

        Partido partido = validarPartido(partidoId, false);

        return convocados(partido).stream()
                .map(jugador -> new Convocado(jugador.id(), jugador.dorsal(), jugador.nombreCompleto()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Eventos
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public EstadoPartidoEnVivoResponse enviarInicioPartido(Long usuarioId, Long partidoId) {
        return registrar(usuarioId, partidoId, PartidoLiveEvento.INICIO, null, null);
    }

    @Override
    @Transactional
    public EstadoPartidoEnVivoResponse enviarGolFavor(
            Long usuarioId, Long partidoId, Long jugadorId, String autorTexto) {
        return registrar(usuarioId, partidoId, PartidoLiveEvento.GOL_FAVOR, jugadorId, autorTexto);
    }

    @Override
    @Transactional
    public EstadoPartidoEnVivoResponse enviarGolContra(Long usuarioId, Long partidoId) {
        return registrar(usuarioId, partidoId, PartidoLiveEvento.GOL_CONTRA, null, null);
    }

    @Override
    @Transactional
    public EstadoPartidoEnVivoResponse enviarDescanso(Long usuarioId, Long partidoId) {
        return registrar(usuarioId, partidoId, PartidoLiveEvento.DESCANSO, null, null);
    }

    @Override
    @Transactional
    public EstadoPartidoEnVivoResponse enviarSegundaParte(Long usuarioId, Long partidoId) {
        return registrar(usuarioId, partidoId, PartidoLiveEvento.SEGUNDA_PARTE, null, null);
    }

    @Override
    @Transactional
    public EstadoPartidoEnVivoResponse enviarFinalPartido(Long usuarioId, Long partidoId) {
        return registrar(usuarioId, partidoId, PartidoLiveEvento.FINAL, null, null);
    }

    /*
     * Valida y registra un evento y lo difunde. Se bloquea la fila del partido
     * (FOR UPDATE) antes de leer los eventos, para que comprobar el orden y el
     * duplicado y crear el evento sea atómico también entre peticiones e
     * instancias distintas.
     */
    private EstadoPartidoEnVivoResponse registrar(
            Long usuarioId, Long partidoId, String tipo, Long jugadorId, String autorTexto) {

        validarRolRetransmision(usuarioId);

        bloquearPartido(partidoId);

        Partido partido = validarPartido(partidoId, true);

        List<PartidoLiveEvento> eventos = new ArrayList<>(eventoDao.obtenerNoAnuladosPorPartido(partidoId));

        validarOrden(tipo, fase(eventos));

        LocalDateTime ahora = ahora();

        PartidoLiveEvento evento = new PartidoLiveEvento();
        evento.setPartidoId(partidoId);
        evento.setTipo(tipo);
        evento.setUsuarioAppId(usuarioId);
        evento.setFecha(ahora);

        if (PartidoLiveEvento.GOL_FAVOR.equals(tipo)) {

            resolverAutor(evento, partido, jugadorId, autorTexto);

            if (esReintentoDuplicadoDeGolFavor(eventos, evento, ahora)) {
                // Mismo gol reintentado: no se suma dos veces ni se vuelve a difundir.
                return construirEstado(partido, eventos);
            }
        }

        if (PartidoLiveEvento.FINAL.equals(tipo)) {
            guardarResultado(partido, eventos, evento, usuarioId);
        }

        eventoDao.crear(evento);
        eventos.add(evento);

        EstadoPartidoEnVivoResponse estado = construirEstado(partido, eventos);

        difundirEvento(partido, evento, estado);

        // Privacidad: sin autor ni nombres en el log.
        logger.info("Evento de directo: partidoId={}, tipo={}, marcador={}-{}",
                partidoId, tipo, estado.golesFavor(), estado.golesContra());

        return estado;
    }

    private static void validarOrden(String tipo, String fase) {

        boolean valido = switch (tipo) {
            case PartidoLiveEvento.INICIO -> EstadoPartidoEnVivoResponse.SIN_EMPEZAR.equals(fase);
            case PartidoLiveEvento.GOL_FAVOR, PartidoLiveEvento.GOL_CONTRA ->
                EstadoPartidoEnVivoResponse.PRIMERA_PARTE.equals(fase)
                        || EstadoPartidoEnVivoResponse.SEGUNDA_PARTE.equals(fase);
            case PartidoLiveEvento.DESCANSO -> EstadoPartidoEnVivoResponse.PRIMERA_PARTE.equals(fase);
            case PartidoLiveEvento.SEGUNDA_PARTE -> EstadoPartidoEnVivoResponse.DESCANSO.equals(fase);
            case PartidoLiveEvento.FINAL -> !EstadoPartidoEnVivoResponse.SIN_EMPEZAR.equals(fase)
                    && !EstadoPartidoEnVivoResponse.FINAL.equals(fase);
            default -> false;
        };

        if (!valido) {
            throw new IllegalArgumentException(mensajeDeOrden(tipo, fase));
        }
    }

    private static String mensajeDeOrden(String tipo, String fase) {

        if (EstadoPartidoEnVivoResponse.FINAL.equals(fase)) {
            return "El partido ya ha terminado";
        }

        if (EstadoPartidoEnVivoResponse.SIN_EMPEZAR.equals(fase) && !PartidoLiveEvento.INICIO.equals(tipo)) {
            return "El partido todavía no ha empezado";
        }

        return switch (tipo) {
            case PartidoLiveEvento.INICIO -> "El partido ya ha empezado";
            case PartidoLiveEvento.GOL_FAVOR, PartidoLiveEvento.GOL_CONTRA ->
                "No se puede anotar un gol en este momento del partido";
            case PartidoLiveEvento.DESCANSO -> "El descanso solo puede llegar durante la primera parte";
            case PartidoLiveEvento.SEGUNDA_PARTE -> "La segunda parte solo puede empezar tras el descanso";
            default -> "Evento no válido en este momento del partido";
        };
    }

    /*
     * Autor del gol a favor: un jugador de la lista de convocados, o un texto
     * ("Otro" o "p.p."), nunca los dos ni ninguno.
     */
    private void resolverAutor(PartidoLiveEvento evento, Partido partido, Long jugadorId, String autorTexto) {

        String texto = autorTexto == null ? null : autorTexto.trim();
        boolean hayTexto = texto != null && !texto.isEmpty();

        if (jugadorId != null && hayTexto) {
            throw new IllegalArgumentException("Indica el jugador o un texto, no los dos");
        }

        if (jugadorId == null && !hayTexto) {
            throw new IllegalArgumentException("El autor del gol es obligatorio");
        }

        if (jugadorId != null) {

            boolean convocado = convocados(partido).stream().anyMatch(j -> j.id().equals(jugadorId));

            if (!convocado) {
                throw new IllegalArgumentException("El jugador no está entre los convocados del partido");
            }

            evento.setJugadorId(jugadorId);
            return;
        }

        if (texto.length() > AUTOR_TEXTO_MAXIMO) {
            throw new IllegalArgumentException(
                    "El nombre del autor del gol es demasiado largo (máximo " + AUTOR_TEXTO_MAXIMO + " caracteres)");
        }

        evento.setAutorTexto(PROPIA_PUERTA.equalsIgnoreCase(texto) ? PROPIA_PUERTA : texto);
    }

    private boolean esReintentoDuplicadoDeGolFavor(
            List<PartidoLiveEvento> eventos, PartidoLiveEvento nuevo, LocalDateTime ahora) {

        String clave = claveAutor(nuevo);

        return eventos.stream()
                .filter(e -> PartidoLiveEvento.GOL_FAVOR.equals(e.getTipo()))
                .reduce((primero, segundo) -> segundo)
                .filter(ultimo -> clave.equals(claveAutor(ultimo)))
                .filter(ultimo -> ahora.isBefore(ultimo.getFecha().plus(VENTANA_DEDUPLICACION_GOL)))
                .isPresent();
    }

    private static String claveAutor(PartidoLiveEvento evento) {
        return evento.getJugadorId() != null
                ? "J" + evento.getJugadorId()
                : "T" + (evento.getAutorTexto() == null ? "" : evento.getAutorTexto().trim().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // Final: guarda el resultado
    // ------------------------------------------------------------------

    /*
     * Escribe el resultado en PARTIDOS y los goles por jugador. Se anota en el
     * evento lo que había antes, para poder deshacerlo sin pisar nada.
     */
    private void guardarResultado(
            Partido partido, List<PartidoLiveEvento> eventos, PartidoLiveEvento evento, Long usuarioId) {

        evento.setResultadoAnterior(partido.getResultado());
        evento.setGolesFavorAnterior(partido.getGolesFavor());
        evento.setGolesContraAnterior(partido.getGolesContra());
        evento.setAvisoAnterior(partido.isAvisoResultadoEnviado());

        int golesFavor = contar(eventos, PartidoLiveEvento.GOL_FAVOR);
        int golesContra = contar(eventos, PartidoLiveEvento.GOL_CONTRA);

        partido.setGolesFavor(golesFavor);
        partido.setGolesContra(golesContra);
        // Misma convención que el resto de la app: "goles Mutxamel-goles rival".
        partido.setResultado(golesFavor + "-" + golesContra);
        partido.setUsuarioActualizoId(usuarioId);
        // Con la misma fecha que el evento: así, al deshacer, se sabe si el
        // entrenador ha tocado el partido después.
        partido.setFechaActualizacion(Timestamp.valueOf(evento.getFecha()));

        partidoDao.actualizar(partido);
        partidoDao.marcarAvisoResultadoEnviado(partido.getId());

        /*
         * Goles por jugador, solo si el partido no tiene ninguna estadística:
         * nunca se sobrescribe lo que haya metido el entrenador. Los goles de
         * "Otro" y "p.p." cuentan en el marcador pero no aquí. Solo los equipos que
         * admiten estadísticas de jugador (F11) las registran.
         */
        Map<Long, Integer> golesPorJugador = new LinkedHashMap<>();

        for (PartidoLiveEvento gol : eventos) {
            if (PartidoLiveEvento.GOL_FAVOR.equals(gol.getTipo()) && gol.getJugadorId() != null) {
                golesPorJugador.merge(gol.getJugadorId(), 1, Integer::sum);
            }
        }

        if (!golesPorJugador.isEmpty() && equipoGestionDao.equipoAdmiteEstadisticas(partido.getEquipoId())
                && estadisticaJugadorDao.obtenerPorPartido(partido.getId()).isEmpty()) {

            for (Map.Entry<Long, Integer> entrada : golesPorJugador.entrySet()) {

                PartidoEstadisticaJugador estadistica = new PartidoEstadisticaJugador();
                estadistica.setPartidoId(partido.getId());
                estadistica.setJugadorId(entrada.getKey());
                estadistica.setGoles(entrada.getValue());
                estadistica.setAsistencias(0);
                estadistica.setTarjetasAmarillas(0);
                estadistica.setTarjetaRoja(false);

                estadisticaJugadorDao.guardar(estadistica);
            }

            evento.setEstadisticasGuardadas(true);
        }
    }

    // ------------------------------------------------------------------
    // Deshacer
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public EstadoPartidoEnVivoResponse deshacer(Long usuarioId, Long partidoId, Long eventoId) {

        validarRolRetransmision(usuarioId);

        bloquearPartido(partidoId);

        Partido partido = validarPartido(partidoId, false);

        PartidoLiveEvento evento = eventoId == null ? null : eventoDao.obtenerPorId(eventoId);

        if (evento == null || !partidoId.equals(evento.getPartidoId())) {
            throw new IllegalArgumentException("El evento no existe en este partido");
        }

        if (evento.isAnulado()) {
            throw new IllegalArgumentException("El evento ya estaba anulado");
        }

        if (PartidoLiveEvento.INICIO.equals(evento.getTipo())) {
            throw new IllegalArgumentException("El inicio del partido no se puede deshacer");
        }

        List<PartidoLiveEvento> eventos = new ArrayList<>(eventoDao.obtenerNoAnuladosPorPartido(partidoId));

        PartidoLiveEvento ultimo = eventos.isEmpty() ? null : eventos.get(eventos.size() - 1);

        if (ultimo == null || !ultimo.getId().equals(eventoId)) {
            throw new IllegalArgumentException("Solo se puede deshacer el último evento");
        }

        LocalDateTime ahora = ahora();

        if (ahora.isAfter(evento.getFecha().plus(VENTANA_DESHACER))) {
            throw new IllegalArgumentException("Ya han pasado los 2 minutos para deshacer este evento");
        }

        eventoDao.anular(eventoId, ahora);
        eventos.remove(eventos.size() - 1);

        if (PartidoLiveEvento.FINAL.equals(evento.getTipo())) {
            revertirFinal(partido, evento, usuarioId, ahora);
        }

        EstadoPartidoEnVivoResponse estado = construirEstado(partido, eventos);

        if (PartidoLiveEvento.GOL_FAVOR.equals(evento.getTipo())
                || PartidoLiveEvento.GOL_CONTRA.equals(evento.getTipo())) {

            difundirTrasCommit("↩️ Gol anulado", "Gol anulado. " + textoMarcador(partido, estado));
        }

        logger.info("Evento de directo deshecho: partidoId={}, tipo={}", partidoId, evento.getTipo());

        return estado;
    }

    /*
     * Devuelve PARTIDOS a como estaba antes del final, salvo que el
     * entrenador ya lo haya modificado después (su FECHA_ACTUALIZACION ya no
     * es la del evento): en ese caso solo se anula el evento.
     */
    private void revertirFinal(Partido partido, PartidoLiveEvento evento, Long usuarioId, LocalDateTime ahora) {

        Partido actual = partidoDao.obtenerPorId(partido.getId());

        boolean intacto = actual != null
                && actual.getFechaActualizacion() != null
                && actual.getFechaActualizacion().toLocalDateTime().equals(evento.getFecha());

        if (!intacto) {
            logger.info("Final deshecho sin tocar el partido: ya lo había modificado otra persona. partidoId={}",
                    partido.getId());
            return;
        }

        actual.setGolesFavor(evento.getGolesFavorAnterior());
        actual.setGolesContra(evento.getGolesContraAnterior());
        actual.setResultado(evento.getResultadoAnterior());
        actual.setUsuarioActualizoId(usuarioId);
        actual.setFechaActualizacion(Timestamp.valueOf(ahora));

        partidoDao.actualizar(actual);
        partidoDao.actualizarAvisoResultadoEnviado(
                actual.getId(), Boolean.TRUE.equals(evento.getAvisoAnterior()));

        if (evento.isEstadisticasGuardadas()) {
            estadisticaJugadorDao.eliminarPorPartido(actual.getId());
        }
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    private EstadoPartidoEnVivoResponse construirEstado(Partido partido, List<PartidoLiveEvento> eventos) {

        Set<Long> jugadorIds = eventos.stream()
                .map(PartidoLiveEvento::getJugadorId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<Long, String> nombres = new HashMap<>();
        for (JugadorBasico jugador : eventoDao.obtenerJugadoresBasicos(jugadorIds)) {
            nombres.put(jugador.id(), jugador.nombreCompleto());
        }

        List<EventoEnVivo> respuesta = new ArrayList<>();
        List<String> goleadores = new ArrayList<>();

        for (PartidoLiveEvento evento : eventos) {

            String autor = autor(evento, nombres);

            respuesta.add(new EventoEnVivo(evento.getId(), evento.getTipo(), evento.getJugadorId(), autor,
                    evento.getFecha()));

            if (PartidoLiveEvento.GOL_FAVOR.equals(evento.getTipo()) && autor != null) {
                goleadores.add(autor);
            }
        }

        PartidoLiveEvento ultimo = eventos.isEmpty() ? null : eventos.get(eventos.size() - 1);

        UltimoEventoDeshacible deshacible = null;

        if (ultimo != null && !PartidoLiveEvento.INICIO.equals(ultimo.getTipo())) {

            LocalDateTime expira = ultimo.getFecha().plus(VENTANA_DESHACER);
            LocalDateTime ahora = ahora();

            if (!ahora.isAfter(expira)) {
                // Con el reloj del servidor: el del móvil puede ir adelantado o atrasado.
                int segundos = (int) Math.ceil(Duration.between(ahora, expira).toMillis() / 1000.0);
                deshacible = new UltimoEventoDeshacible(ultimo.getId(), expira, Math.max(0, segundos));
            }
        }

        return new EstadoPartidoEnVivoResponse(
                partido.getId(),
                nombreRival(partido),
                partido.isLocal(),
                fase(eventos),
                contar(eventos, PartidoLiveEvento.GOL_FAVOR),
                contar(eventos, PartidoLiveEvento.GOL_CONTRA),
                goleadores,
                respuesta,
                deshacible);
    }

    private static String autor(PartidoLiveEvento evento, Map<Long, String> nombres) {

        if (evento.getJugadorId() != null) {
            return nombres.get(evento.getJugadorId());
        }

        return evento.getAutorTexto();
    }

    /** La fase la marca el último evento que no es un gol. */
    static String fase(List<PartidoLiveEvento> eventos) {

        String fase = EstadoPartidoEnVivoResponse.SIN_EMPEZAR;

        for (PartidoLiveEvento evento : eventos) {
            switch (evento.getTipo()) {
                case PartidoLiveEvento.INICIO -> fase = EstadoPartidoEnVivoResponse.PRIMERA_PARTE;
                case PartidoLiveEvento.DESCANSO -> fase = EstadoPartidoEnVivoResponse.DESCANSO;
                case PartidoLiveEvento.SEGUNDA_PARTE -> fase = EstadoPartidoEnVivoResponse.SEGUNDA_PARTE;
                case PartidoLiveEvento.FINAL -> fase = EstadoPartidoEnVivoResponse.FINAL;
                default -> {
                    // los goles no cambian la fase
                }
            }
        }

        return fase;
    }

    private static int contar(List<PartidoLiveEvento> eventos, String tipo) {
        return (int) eventos.stream().filter(e -> tipo.equals(e.getTipo())).count();
    }

    // ------------------------------------------------------------------
    // Difusión
    // ------------------------------------------------------------------

    private void difundirEvento(Partido partido, PartidoLiveEvento evento, EstadoPartidoEnVivoResponse estado) {

        String marcador = textoMarcador(partido, estado);

        switch (evento.getTipo()) {
            case PartidoLiveEvento.INICIO ->
                difundirTrasCommit("⚽ ¡Comienza el partido!", "Mutxamel CF - " + nombreRival(partido));
            case PartidoLiveEvento.GOL_FAVOR -> {
                String autor = estado.eventos().stream()
                        .filter(e -> e.id() != null && e.id().equals(evento.getId()))
                        .map(EventoEnVivo::autor)
                        .findFirst()
                        .orElse(null);

                difundirTrasCommit("⚽ ¡GOOOL del Mutxamel CF!", textoAutor(autor) + "\n\n" + marcador);
            }
            case PartidoLiveEvento.GOL_CONTRA -> difundirTrasCommit("Gol en contra", marcador);
            case PartidoLiveEvento.DESCANSO -> difundirTrasCommit("⏸️ Descanso", marcador);
            case PartidoLiveEvento.SEGUNDA_PARTE ->
                difundirTrasCommit("▶️ ¡Comienza la segunda parte!", "Mutxamel CF - " + nombreRival(partido));
            case PartidoLiveEvento.FINAL -> {
                String goleadores = estado.goleadores().isEmpty()
                        ? "Sin goleadores"
                        : String.join(", ", estado.goleadores().stream().map(PartidoEnVivoServiceImpl::textoAutor)
                                .toList());

                difundirTrasCommit("🏁 Final del partido",
                        marcador + "\n\nGoleadores: " + goleadores + "\n\nHa finalizado el partido.");
            }
            default -> {
                // sin difusión
            }
        }
    }

    private static String textoAutor(String autor) {

        if (autor == null || autor.isBlank()) {
            return "Gol del Mutxamel CF";
        }

        return PROPIA_PUERTA.equals(autor) ? "Gol en propia puerta del rival" : autor;
    }

    private String textoMarcador(Partido partido, EstadoPartidoEnVivoResponse estado) {
        return "Mutxamel CF " + estado.golesFavor() + " - " + estado.golesContra() + " " + nombreRival(partido);
    }

    /*
     * Difunde cuando la transacción ya ha confirmado el evento (para no avisar
     * de algo que luego se revierte); sin transacción, al momento.
     */
    private void difundirTrasCommit(String titulo, String mensaje) {

        if (TransactionSynchronizationManager.isSynchronizationActive()) {

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    difundir(titulo, mensaje);
                }
            });

        } else {

            difundir(titulo, mensaje);
        }
    }

    /**
     * Envía el aviso por los dos canales: a los usuarios con cuenta
     * (por dispositivo, vía NotificacionAppService) y al topic de FCM
     * "resultados" (suscripción anónima sin cuenta). La app se encarga
     * de que un usuario con cuenta no reciba el aviso dos veces.
     */
    private void difundir(String titulo, String mensaje) {

        notificacionAppService.difundirATodos(TIPO_PUSH, titulo, mensaje, null);
        fcmPushService.enviarATopic(TOPIC_RESULTADOS, titulo, mensaje);
    }

    // ------------------------------------------------------------------
    // Validaciones y utilidades
    // ------------------------------------------------------------------

    private void validarRolRetransmision(Long usuarioId) {

        if (usuarioId == null || !usuarioAppService.tieneRol(usuarioId.intValue(), "RETRANSMISION")) {

            throw new SecurityException(
                    "No tienes permiso para enviar avisos del partido en directo");
        }
    }

    private void bloquearPartido(Long partidoId) {

        if (partidoId != null) {
            partidoDao.bloquearPorId(partidoId);
        }
    }

    /*
     * El partido debe existir y ser del Primer Equipo; para registrar eventos,
     * además, de hoy (hora de Madrid) y no estar cancelado.
     */
    private Partido validarPartido(Long partidoId, boolean paraRegistrar) {

        Partido partido = partidoId == null ? null : partidoDao.obtenerPorId(partidoId);

        if (partido == null) {
            throw new IllegalArgumentException("El partido no existe");
        }

        Equipo primerEquipo = equipoDao.obtenerEquipoPorNombre(EQUIPO_PRIMER_EQUIPO);

        if (primerEquipo == null || !primerEquipo.getId().equals(partido.getEquipoId())) {
            throw new IllegalArgumentException("El partido no es del Primer Equipo");
        }

        if (paraRegistrar) {

            if (partido.isCancelado()) {
                throw new IllegalArgumentException("El partido está cancelado");
            }

            if (partido.getDia() == null || !aLocalDate(partido.getDia()).equals(LocalDate.now(clock))) {
                throw new IllegalArgumentException("El partido no es de hoy");
            }
        }

        return partido;
    }

    private List<JugadorBasico> convocados(Partido partido) {

        Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(partido.getId());

        List<Long> ids = convocatoria != null
                ? convocatoriaJugadorDao.obtenerPorConvocatoria(convocatoria.getId()).stream()
                        .map(ConvocatoriaJugador::getJugadorId)
                        .toList()
                : equipoGestionDao.obtenerJugadoresPorEquipo(partido.getEquipoId());

        return eventoDao.obtenerJugadoresBasicos(ids).stream()
                .sorted(Comparator
                        .comparing(JugadorBasico::dorsal, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(JugadorBasico::nombreCompleto, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String nombreRival(Partido partido) {
        return partido.getRival() != null ? partido.getRival() : "el rival";
    }

    /** Hora de Madrid, a la precisión de TIMESTAMP(6) para que ida y vuelta a la base de datos coincidan. */
    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static LocalDate aLocalDate(Date dia) {

        if (dia instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        return dia.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
