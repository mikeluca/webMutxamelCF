package com.mikedev.mutxamelcf.serviceimpl;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.time.Clock;

import org.slf4j.Logger;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.dao.ResumenTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugador;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugadorRequest;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugadorResponse;
import com.mikedev.mutxamelcf.model.PartidoEstadisticasGuardarRequest;
import com.mikedev.mutxamelcf.model.PartidoEstadisticasResponse;
import com.mikedev.mutxamelcf.service.PartidoEstadisticaService;

@Service
public class PartidoEstadisticaServiceImpl
        implements PartidoEstadisticaService {

    private static final Logger logger = LoggerFactory.getLogger(PartidoEstadisticaServiceImpl.class);

    private final PartidoDao partidoDao;
    private final PartidoEstadisticaJugadorDao estadisticaJugadorDao;
    private final EquipoGestionDao equipoGestionDao;
    private final ConvocatoriaDao convocatoriaDao;
    private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
    private final TemporadaDao temporadaDao;
    private final ResumenTemporadaDao resumenTemporadaDao;
    private final ComunicacionService comunicacionService;
    private final ReglaSancionAmarillas reglaSancion;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public PartidoEstadisticaServiceImpl(
            PartidoDao partidoDao,
            PartidoEstadisticaJugadorDao estadisticaJugadorDao,
            EquipoGestionDao equipoGestionDao,
            ConvocatoriaDao convocatoriaDao,
            ConvocatoriaJugadorDao convocatoriaJugadorDao,
            TemporadaDao temporadaDao,
            ResumenTemporadaDao resumenTemporadaDao,
            ComunicacionService comunicacionService,
            ReglaSancionAmarillas reglaSancion,
            PlatformTransactionManager transactionManager,
            Clock clock) {

        this.transactionManager = transactionManager;
        this.clock = clock;
        this.temporadaDao = temporadaDao;
        this.resumenTemporadaDao = resumenTemporadaDao;
        this.comunicacionService = comunicacionService;
        this.reglaSancion = reglaSancion;
        this.partidoDao = partidoDao;
        this.estadisticaJugadorDao = estadisticaJugadorDao;
        this.equipoGestionDao = equipoGestionDao;
        this.convocatoriaDao = convocatoriaDao;
        this.convocatoriaJugadorDao = convocatoriaJugadorDao;
    }

    @Override
    @Transactional
    public PartidoEstadisticasResponse guardar(
            Long usuarioAppId,
            Long partidoId,
            PartidoEstadisticasGuardarRequest request) {

        if (usuarioAppId == null) {
            throw new SecurityException(
                    "Usuario no autenticado");
        }

        if (partidoId == null) {
            throw new IllegalArgumentException(
                    "El ID del partido es obligatorio");
        }

        validarRequest(request);

        Partido partido = partidoDao.obtenerPorId(partidoId);

        if (partido == null) {
            throw new IllegalArgumentException(
                    "El partido no existe");
        }

        /*
         * Mismo control de permisos que el resto de operaciones "App"
         * sobre partidos.
         */
        if (!equipoGestionDao.puedeGestionarEquipo(
                usuarioAppId,
                partido.getEquipoId())) {

            throw new SecurityException(
                    "El usuario no puede gestionar este partido");
        }

        /*
         * No se puede introducir el resultado de un partido que todavía
         * no se ha jugado.
         */
        LocalDateTime fechaHoraPartido = combinarFechaHora(
                partido.getDia(),
                partido.getHora());

        if (fechaHoraPartido != null
                && fechaHoraPartido.isAfter(LocalDateTime.now(clock))) {

            throw new IllegalArgumentException(
                    "No se puede introducir el resultado de un partido que todavía no se ha jugado");
        }

        /*
         * El roster a validar es el de la convocatoria del partido si
         * existe (es opcional); si no, el roster completo del equipo.
         */
        boolean admiteEstadisticas = equipoGestionDao.equipoAdmiteEstadisticas(partido.getEquipoId());

        if (admiteEstadisticas) {

            if (request.getJugadores() == null) {
                throw new IllegalArgumentException(
                        "Debe indicarse la estadística de los jugadores");
            }

            List<Long> roster = obtenerRoster(partido);

            validarEstadisticas(
                    roster,
                    request.getJugadores());

        } else if (hayEstadisticasDeJugador(request.getJugadores())) {

            throw new IllegalArgumentException(
                    "Este equipo (fútbol 8/5) solo registra el resultado del partido");
        }

        /*
         * RESULTADO (texto libre) se sigue calculando y sobreescribiendo
         * a partir de GOLES_FAVOR/GOLES_CONTRA, para que todas las
         * pantallas que ya lo leen (admin web, Flutter) sigan
         * funcionando sin cambios.
         */
        partido.setGolesFavor(request.getGolesFavor());
        partido.setGolesContra(request.getGolesContra());
        partido.setResultado(
                request.getGolesFavor() + "-" + request.getGolesContra());
        partido.setUsuarioActualizoId(usuarioAppId);
        partido.setFechaActualizacion(
                Timestamp.valueOf(LocalDateTime.now(clock)));

        partidoDao.actualizar(partido);

        /*
         * Sin estadísticas de jugador (no F11) solo se guarda el marcador: no se tocan las
         * que ya existieran, ni se calculan avisos de tarjetas.
         */
        if (!admiteEstadisticas) {
            return construirResponse(partido, false);
        }

        // Lo que había antes de guardar, para avisar solo de lo que cambia.
        Map<Long, Integer> amarillasAntes = amarillasAcumuladas(partido);

        Map<Long, Boolean> rojaAntes = new HashMap<>();

        for (PartidoEstadisticaJugador anterior : estadisticaJugadorDao.obtenerPorPartido(partidoId)) {
            rojaAntes.put(anterior.getJugadorId(), anterior.isTarjetaRoja());
        }

        /*
         * Reemplazo completo de las estadísticas por jugador, igual que
         * EntrenamientoServiceImpl.actualizar hace con las asistencias.
         */
        estadisticaJugadorDao.eliminarPorPartido(partidoId);

        for (PartidoEstadisticaJugadorRequest jugadorRequest : request.getJugadores()) {

            PartidoEstadisticaJugador estadistica = new PartidoEstadisticaJugador();

            estadistica.setPartidoId(partidoId);
            estadistica.setJugadorId(jugadorRequest.getJugadorId());
            estadistica.setGoles(jugadorRequest.getGoles());
            estadistica.setAsistencias(jugadorRequest.getAsistencias());
            estadistica.setTarjetasAmarillas(jugadorRequest.getTarjetasAmarillas());
            estadistica.setTarjetaRoja(jugadorRequest.isTarjetaRoja());

            estadisticaJugadorDao.guardar(estadistica);
        }

        avisarTarjetas(partido, request.getJugadores(), amarillasAntes, amarillasAcumuladas(partido), rojaAntes,
                usuarioAppId);

        return construirResponse(partido, true);
    }

    /*
     * Amarillas acumuladas por jugador del equipo en la temporada activa (partidos
     * no cancelados). Sin temporada activa, nadie acumula nada.
     */
    private Map<Long, Integer> amarillasAcumuladas(Partido partido) {

        Temporada temporada = temporadaDao.obtenerTemporadaActiva();

        Map<Long, Integer> amarillas = new HashMap<>();

        if (temporada == null) {
            return amarillas;
        }

        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = temporada.getFechaInicio() != null ? aLocalDate(temporada.getFechaInicio()) : LocalDate.of(1900, 1, 1);
        LocalDate hasta = temporada.getFechaFin() != null ? aLocalDate(temporada.getFechaFin()) : hoy.plusYears(1);

        for (EstadisticasJugadorEquipo jugador : resumenTemporadaDao
                .estadisticasJugadoresDelEquipo(partido.getEquipoId(), desde, hasta, hoy)) {

            amarillas.put(jugador.jugadorId(), jugador.amarillas());
        }

        return amarillas;
    }

    private static LocalDate aLocalDate(Date fecha) {

        if (fecha instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        return fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    /*
     * Un único aviso por jugador y guardado, al cuerpo técnico del equipo, si el
     * guardado hace cruzar un umbral (posible sanción, o una amarilla antes de ella)
     * o el jugador ve la roja en este partido (y no la tenía ya). Volver a guardar
     * sin nada nuevo no avisa. Si coinciden varias cosas, manda la más grave:
     * posible sanción por amarillas, roja, y por último "a una".
     *
     * Solo se avisa al llegar a un umbral MAYOR que el que ya había antes de este
     * guardado (se compara el acumulado de antes y de después; si "después" no
     * supera a "antes", no hay aviso), y la roja solo si en este partido pasa de 0
     * a 1. Limitación: sin histórico de avisos no se puede saber qué umbrales se
     * avisaron en guardados anteriores, así que bajar las amarillas en un guardado
     * y volver a subirlas en otro puede avisar otra vez.
     */
    private void avisarTarjetas(
            Partido partido,
            List<PartidoEstadisticaJugadorRequest> jugadores,
            Map<Long, Integer> antes,
            Map<Long, Integer> despues,
            Map<Long, Boolean> rojaAntes,
            Long usuarioAppId) {

        List<String> avisos = new ArrayList<>();

        for (PartidoEstadisticaJugadorRequest jugador : jugadores) {

            Long id = jugador.getJugadorId();

            int amarillasAntes = antes.getOrDefault(id, 0);
            int amarillasDespues = despues.getOrDefault(id, 0);

            int umbralSancion = reglaSancion.umbralSancionCruzado(amarillasAntes, amarillasDespues);
            boolean rojaNueva = jugador.isTarjetaRoja() && !rojaAntes.getOrDefault(id, false);
            boolean aUna = reglaSancion.cruzaAvisoPrevio(amarillasAntes, amarillasDespues);

            if (umbralSancion == 0 && !rojaNueva && !aUna) {
                continue;
            }

            String nombre = equipoGestionDao.obtenerNombreJugador(id);

            if (umbralSancion > 0) {
                avisos.add(nombre + " llega a " + umbralSancion + " amarillas: posible sanción."
                        + " Compruébalo en la FFCV.");
            } else if (rojaNueva) {
                avisos.add(nombre + " ha visto la roja en el partido contra " + partido.getRival()
                        + ": posible sanción.");
            } else {
                avisos.add(nombre + " acumula " + amarillasDespues + " amarillas: a una de posible sanción.");
            }
        }

        if (avisos.isEmpty()) {
            return;
        }

        List<Long> destinatarios = new ArrayList<>(
                equipoGestionDao.obtenerEntrenadoresPorEquipo(partido.getEquipoId()));

        // Sin ningún entrenador con cuenta, los coordinadores.
        if (destinatarios.isEmpty()) {
            destinatarios.addAll(equipoGestionDao.obtenerCoordinadores());
        }

        // Quien guarda ya lo sabe: no se avisa a sí mismo.
        destinatarios.remove(usuarioAppId);

        if (destinatarios.isEmpty()) {
            logger.warn("Aviso de tarjetas sin destinatarios: el equipo {} no tiene entrenadores"
                    + " (ni coordinadores distintos de quien guarda)", partido.getEquipoId());
            return;
        }

        // Una sola comunicación por guardado, con una línea por jugador.
        Comunicacion comunicacion = new Comunicacion();

        comunicacion.setTitulo("Tarjetas");
        comunicacion.setContenido(String.join("\n", avisos));

        enviarTrasElCommit(comunicacion, destinatarios, usuarioAppId);
    }

    /*
     * El aviso se calcula dentro de la transacción del guardado pero se ENVÍA
     * cuando esta ya se ha confirmado: un fallo al avisar nunca deshace el
     * guardado. Crear la comunicación escribe en la base de datos, y en
     * afterCommit la transacción ya está cerrada, así que se hace en una
     * transacción nueva (REQUIRES_NEW).
     */
    private void enviarTrasElCommit(Comunicacion comunicacion, List<Long> destinatarios, Long usuarioAppId) {

        Runnable envio = () -> {
            try {

                TransactionTemplate plantilla = new TransactionTemplate(transactionManager);
                plantilla.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                plantilla.executeWithoutResult(
                        estado -> comunicacionService.crearPrivada(comunicacion, destinatarios, usuarioAppId));

            } catch (Exception e) {

                logger.error("No se ha podido enviar el aviso de tarjetas a {} destinatarios: {}",
                        destinatarios.size(), e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
            return;
        }

        envio.run();
    }

    @Override
    @Transactional(readOnly = true)
    public PartidoEstadisticasResponse obtener(
            Long usuarioAppId,
            Long partidoId) {

        if (usuarioAppId == null) {
            throw new SecurityException(
                    "Usuario no autenticado");
        }

        if (partidoId == null) {
            throw new IllegalArgumentException(
                    "El ID del partido es obligatorio");
        }

        Partido partido = partidoDao.obtenerPorId(partidoId);

        if (partido == null) {
            throw new IllegalArgumentException(
                    "El partido no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(
                usuarioAppId,
                partido.getEquipoId())) {

            throw new SecurityException(
                    "El usuario no puede consultar este partido");
        }

        return construirResponse(partido, equipoGestionDao.equipoAdmiteEstadisticas(partido.getEquipoId()));
    }

    private List<Long> obtenerRoster(Partido partido) {

        Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(
                partido.getId());

        if (convocatoria != null) {

            List<ConvocatoriaJugador> convocados = convocatoriaJugadorDao.obtenerPorConvocatoria(
                    convocatoria.getId());

            List<Long> roster = new ArrayList<>();

            for (ConvocatoriaJugador convocado : convocados) {
                roster.add(convocado.getJugadorId());
            }

            return roster;
        }

        return equipoGestionDao.obtenerJugadoresPorEquipo(
                partido.getEquipoId());
    }

    // true si algún jugador trae un valor distinto de 0 (goles, asistencias, amarillas o roja).
    private static boolean hayEstadisticasDeJugador(List<PartidoEstadisticaJugadorRequest> jugadores) {

        if (jugadores == null) {
            return false;
        }

        for (PartidoEstadisticaJugadorRequest jugador : jugadores) {

            if (jugador == null) {
                continue;
            }

            if (valor(jugador.getGoles()) != 0 || valor(jugador.getAsistencias()) != 0
                    || valor(jugador.getTarjetasAmarillas()) != 0 || jugador.isTarjetaRoja()) {
                return true;
            }
        }

        return false;
    }

    private static int valor(Integer numero) {
        return numero == null ? 0 : numero;
    }

    private void validarRequest(
            PartidoEstadisticasGuardarRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "La petición es obligatoria");
        }

        if (request.getGolesFavor() == null
                || request.getGolesFavor() < 0) {

            throw new IllegalArgumentException(
                    "Los goles a favor son obligatorios y no pueden ser negativos");
        }

        if (request.getGolesContra() == null
                || request.getGolesContra() < 0) {

            throw new IllegalArgumentException(
                    "Los goles en contra son obligatorios y no pueden ser negativos");
        }

    }

    private void validarEstadisticas(
            List<Long> roster,
            List<PartidoEstadisticaJugadorRequest> jugadores) {

        Set<Long> rosterSet = new HashSet<>(roster);

        Set<Long> jugadoresRecibidos = new HashSet<>();

        for (PartidoEstadisticaJugadorRequest jugador : jugadores) {

            if (jugador == null || jugador.getJugadorId() == null) {
                throw new IllegalArgumentException(
                        "Existe una estadística inválida");
            }

            if (jugador.getGoles() == null || jugador.getGoles() < 0
                    || jugador.getAsistencias() == null || jugador.getAsistencias() < 0
                    || jugador.getTarjetasAmarillas() == null || jugador.getTarjetasAmarillas() < 0) {

                throw new IllegalArgumentException(
                        "Las estadísticas del jugador "
                                + jugador.getJugadorId()
                                + " no son válidas");
            }

            /*
             * Un jugador no puede aparecer dos veces.
             */
            if (!jugadoresRecibidos.add(jugador.getJugadorId())) {

                throw new IllegalArgumentException(
                        "El jugador "
                                + jugador.getJugadorId()
                                + " aparece más de una vez");
            }

            /*
             * No aceptamos un jugador que no pertenezca a la
             * convocatoria del partido (o al equipo, si no tiene
             * convocatoria).
             */
            if (!rosterSet.contains(jugador.getJugadorId())) {

                throw new SecurityException(
                        "El jugador "
                                + jugador.getJugadorId()
                                + " no forma parte de la convocatoria (o del equipo) de este partido");
            }
        }

        /*
         * Deben estar TODOS los jugadores de la convocatoria (o del
         * equipo, si no tiene convocatoria).
         */
        if (!jugadoresRecibidos.equals(rosterSet)) {

            throw new IllegalArgumentException(
                    "Debe indicar la estadística de todos los jugadores de la convocatoria "
                            + "(o de todo el equipo si el partido no tiene convocatoria)");
        }
    }

    private PartidoEstadisticasResponse construirResponse(
            Partido partido,
            boolean admiteEstadisticas) {

        List<PartidoEstadisticaJugador> estadisticas = estadisticaJugadorDao.obtenerPorPartido(
                partido.getId());

        List<PartidoEstadisticaJugadorResponse> jugadores = new ArrayList<>();

        for (PartidoEstadisticaJugador estadistica : estadisticas) {

            String nombreJugador = equipoGestionDao.obtenerNombreJugador(
                    estadistica.getJugadorId());

            jugadores.add(
                    new PartidoEstadisticaJugadorResponse(
                            estadistica.getJugadorId(),
                            nombreJugador,
                            estadistica.getGoles(),
                            estadistica.getAsistencias(),
                            estadistica.getTarjetasAmarillas(),
                            estadistica.isTarjetaRoja()));
        }

        return new PartidoEstadisticasResponse(
                partido.getId(),
                partido.getGolesFavor(),
                partido.getGolesContra(),
                partido.getResultado(),
                jugadores,
                admiteEstadisticas);
    }

    /*
     * Combina Partido.dia (fecha, sin hora) y Partido.hora ("HH:mm",
     * opcional) en un único LocalDateTime, para poder comparar con
     * "ahora" y detectar partidos que todavía no se han jugado. Sin
     * hora informada se asume medianoche (00:00), lo que en la práctica
     * solo bloquea el mismo día del partido si todavía no ha empezado.
     */
    private static LocalDateTime combinarFechaHora(
            Date dia,
            String hora) {

        if (dia == null) {
            return null;
        }

        LocalDate fecha = new java.sql.Date(dia.getTime()).toLocalDate();

        return LocalDateTime.of(fecha, parsearHora(hora));
    }

    /*
     * HORA es texto libre: acepta "H:mm", "HH:mm", "18.30" o "18:30h".
     * Si no se puede interpretar se asume medianoche, para no bloquear
     * el guardado del resultado por un formato de hora inesperado.
     */
    private static LocalTime parsearHora(String hora) {

        if (hora == null || hora.isBlank()) {
            return LocalTime.MIDNIGHT;
        }

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{1,2})\\s*[:.hH]\\s*(\\d{2})")
                .matcher(hora);

        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));

            if (h <= 23 && min <= 59) {
                return LocalTime.of(h, min);
            }
        }

        return LocalTime.MIDNIGHT;
    }
}
