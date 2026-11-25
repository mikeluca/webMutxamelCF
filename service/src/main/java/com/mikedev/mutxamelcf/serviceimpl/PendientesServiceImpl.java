package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.CoordinacionDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.InvitacionDao;
import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EstadoInvitacion;
import com.mikedev.mutxamelcf.model.FinDeSemanaResponse;
import com.mikedev.mutxamelcf.model.InvitacionDetalle;
import com.mikedev.mutxamelcf.model.ListaSinRevisar;
import com.mikedev.mutxamelcf.model.PartidoCoordinacion;
import com.mikedev.mutxamelcf.model.PendientesResponse;
import com.mikedev.mutxamelcf.model.PendientesResponse.Elemento;
import com.mikedev.mutxamelcf.model.PendientesResponse.Grupo;
import com.mikedev.mutxamelcf.model.ReporteModeracionResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.SesionEntrenamiento;
import com.mikedev.mutxamelcf.model.UsuarioPendienteResponse;
import com.mikedev.mutxamelcf.service.CoordinacionService;
import com.mikedev.mutxamelcf.service.ImagenesRedesService;
import com.mikedev.mutxamelcf.service.ModeracionService;
import com.mikedev.mutxamelcf.service.PendientesService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Revisor;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * La pantalla "Pendientes". No duplica lógica: Fin de semana ({@link CoordinacionService}), solicitudes de familia
 * ({@link SolicitudFamiliaService}, que ya acota lo que ve cada revisor), moderación, cuentas pendientes e imágenes para
 * redes. Lo demás son consultas únicas sobre todos los equipos (la lista de entrenamientos sin revisar, los partidos, las
 * invitaciones) que se filtran en memoria por los equipos del entrenador: ninguna consulta por equipo ni por elemento.
 */
@Service
public class PendientesServiceImpl implements PendientesService {

    /** Entrenamientos sin revisar: los de los últimos 14 días. */
    static final int DIAS_LISTAS = 14;

    /** Partidos sin convocatoria: los de los próximos 7 días. */
    static final int DIAS_CONVOCATORIAS = 7;

    /** Una invitación pendiente de su entrenador se considera sin liberar a las 12 horas. */
    static final int HORAS_SIN_LIBERAR = 12;

    private static final LocalTime HORA_POR_DEFECTO = LocalTime.of(0, 0);
    private static final LocalTime VIERNES_PREVIA = LocalTime.of(12, 0);
    private static final LocalTime LUNES_RESULTADOS = LocalTime.of(9, 0);

    private final UsuarioAppService usuarioAppService;
    private final PerfilAppDao perfilAppDao;
    private final EntrenamientoDao entrenamientoDao;
    private final CoordinacionDao coordinacionDao;
    private final CoordinacionService coordinacionService;
    private final InvitacionDao invitacionDao;
    private final SolicitudFamiliaService solicitudFamiliaService;
    private final ModeracionService moderacionService;
    private final ImagenesRedesService imagenesRedesService;
    private final EquipoGestionDao equipoGestionDao;
    private final TextosAvisos textos;
    private final Clock clock;

    public PendientesServiceImpl(
            UsuarioAppService usuarioAppService,
            PerfilAppDao perfilAppDao,
            EntrenamientoDao entrenamientoDao,
            CoordinacionDao coordinacionDao,
            CoordinacionService coordinacionService,
            InvitacionDao invitacionDao,
            SolicitudFamiliaService solicitudFamiliaService,
            ModeracionService moderacionService,
            ImagenesRedesService imagenesRedesService,
            EquipoGestionDao equipoGestionDao,
            TextosAvisos textos,
            Clock clock) {

        this.usuarioAppService = usuarioAppService;
        this.perfilAppDao = perfilAppDao;
        this.entrenamientoDao = entrenamientoDao;
        this.coordinacionDao = coordinacionDao;
        this.coordinacionService = coordinacionService;
        this.invitacionDao = invitacionDao;
        this.solicitudFamiliaService = solicitudFamiliaService;
        this.moderacionService = moderacionService;
        this.imagenesRedesService = imagenesRedesService;
        this.equipoGestionDao = equipoGestionDao;
        this.textos = textos;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PendientesResponse obtener(Long usuarioAppId) {

        if (usuarioAppId == null || usuarioAppId <= 0) {
            throw new SecurityException("Usuario no válido");
        }

        int usuario = usuarioAppId.intValue();

        Set<String> roles = usuarioAppService.obtenerRoles(usuario).stream()
                .map(RolApp::getCodigo)
                .collect(Collectors.toSet());

        boolean entrenador = roles.contains("ENTRENADOR");
        boolean admin = roles.contains("ADMIN_APP");
        boolean coordinacion = admin || roles.contains("COORDINADOR");
        boolean rrss = roles.contains("RRSS");

        // Familia y jugador (sin nada más): ni una consulta.
        if (!entrenador && !coordinacion && !rrss) {
            return new PendientesResponse(List.of());
        }

        Contexto c = new Contexto(usuarioAppId, usuarioAppService.obtenerIdioma(usuario),
                LocalDateTime.now(clock), new ArrayList<>());

        Map<Long, String> misEquipos = entrenador
                ? perfilAppDao.obtenerEquiposPorUsuario(usuario).stream()
                        .collect(Collectors.toMap(Equipo::getId, Equipo::getNombre, (a, b) -> a, HashMap::new))
                : Map.of();

        // Las invitaciones pendientes: una sola consulta para coordinación y para el entrenador.
        List<InvitacionDetalle> invitaciones = coordinacion || !misEquipos.isEmpty()
                ? invitacionDao.pendientes(c.hoy())
                : List.of();

        if (!misEquipos.isEmpty()) {
            listasSinPasar(c, misEquipos);
        }

        if (coordinacion) {
            finDeSemana(c);
        } else if (!misEquipos.isEmpty()) {
            partidosDelEntrenador(c, misEquipos);
        }

        if (coordinacion || !misEquipos.isEmpty()) {
            solicitudesDeFamilia(c);
        }

        if (!misEquipos.isEmpty()) {
            jugadoresALiberar(c, invitaciones, misEquipos.keySet());
        }

        if (coordinacion) {
            invitacionesDeCoordinacion(c, invitaciones);
        }

        if (admin) {
            moderacion(c);
            cuentasCaducadas(c);
        }

        if (rrss || (admin && equipoGestionDao.obtenerRrss().isEmpty())) {
            imagenesParaRedes(c);
        }

        return new PendientesResponse(c.grupos);
    }

    // ---- entrenador ----------------------------------------------------------------------------------------------

    /** Entrenamientos de los últimos 14 días que ya han empezado, sin cancelar, con la lista sin revisar. */
    private void listasSinPasar(Contexto c, Map<Long, String> misEquipos) {

        List<Elemento> elementos = new ArrayList<>();

        for (ListaSinRevisar lista : entrenamientoDao.obtenerListasSinRevisar(c.hoy().minusDays(DIAS_LISTAS), c.hoy())) {

            if (!misEquipos.containsKey(lista.equipoId())
                    || SesionEntrenamiento.ESTADO_CANCELADA.equals(lista.estadoSesion())) {
                continue;
            }

            LocalTime hora = HoraTexto.leer(lista.hora());

            if (lista.fecha().atTime(hora != null ? hora : HORA_POR_DEFECTO).isAfter(c.ahora)) {
                continue;
            }

            elementos.add(new Elemento(lista.sesionId(),
                    textos.texto(c.idioma, "pendientes.listas.item",
                            "equipo", misEquipos.get(lista.equipoId()),
                            "fecha", textos.fecha(c.idioma, lista.fecha()),
                            "hora", hora != null ? textos.hora(hora) : ""),
                    lista.fecha(), "PASAR_LISTA:" + lista.sesionId(), null));
        }

        c.anadir(LISTAS_SIN_PASAR, "pendientes.listas.titulo", elementos);
    }

    /** Partidos de los equipos del entrenador: próximos 7 días sin convocatoria y resultados pendientes. */
    private void partidosDelEntrenador(Contexto c, Map<Long, String> misEquipos) {

        // Una sola consulta cubre los dos plazos (10 días atrás para los resultados, 7 adelante para las convocatorias).
        List<PartidoCoordinacion> partidos = coordinacionDao.partidosEntre(
                c.hoy().minusDays(CoordinacionServiceImpl.DIAS_RESULTADOS_PENDIENTES), c.hoy().plusDays(DIAS_CONVOCATORIAS))
                .stream()
                .filter(p -> misEquipos.containsKey(p.equipoId()) && !p.cancelado())
                .toList();

        List<Elemento> sinConvocatoria = new ArrayList<>();
        List<Elemento> sinResultado = new ArrayList<>();

        for (PartidoCoordinacion p : partidos) {

            boolean empezado = !CriterioResultadoPartido.inicio(p.dia(), p.hora()).isAfter(c.ahora);

            if (!p.conConvocatoria() && !empezado) {
                sinConvocatoria.add(elementoConvocatoria(c, p.equipo(), p.partidoId(), p.rival(), p.dia(), p.hora()));
            }

            if (!p.dia().isAfter(c.hoy())
                    && !CriterioResultadoPartido.tieneResultado(p.golesFavor(), p.resultado())
                    && CriterioResultadoPartido.haTerminado(p.dia(), p.hora(), c.ahora)) {
                sinResultado.add(elementoResultado(c, p.equipo(), p.partidoId(), p.rival(), p.dia(), p.hora(),
                        (int) ChronoUnit.DAYS.between(p.dia(), c.hoy())));
            }
        }

        c.anadir(CONVOCATORIAS_SIN_PUBLICAR, "pendientes.convocatorias.titulo", sinConvocatoria);
        c.anadir(RESULTADOS_PENDIENTES, "pendientes.resultados.titulo", sinResultado);
    }

    private void jugadoresALiberar(Contexto c, List<InvitacionDetalle> invitaciones, Set<Long> misEquipos) {

        List<Elemento> elementos = invitaciones.stream()
                .filter(i -> i.estado() == EstadoInvitacion.PENDIENTE_ENTRENADOR && !i.cancelado()
                        && misEquipos.contains(i.equipoOrigenId()))
                .map(i -> new Elemento(i.id(),
                        textos.texto(c.idioma, "pendientes.liberar.item",
                                "jugador", i.jugador(),
                                "equipoDestino", i.equipoDestino(),
                                "fecha", textos.fecha(c.idioma, i.dia())),
                        i.dia(), "INVITACIONES", null))
                .toList();

        c.anadir(JUGADORES_A_LIBERAR, "pendientes.liberar.titulo", elementos);
    }

    // ---- coordinación --------------------------------------------------------------------------------------------

    /** Lo mismo que el panel Fin de semana: todos los equipos, con su contador. */
    private void finDeSemana(Contexto c) {

        FinDeSemanaResponse panel = coordinacionService.obtenerFinDeSemana(c.usuarioId, null);

        c.anadir(CONVOCATORIAS_SIN_PUBLICAR, "pendientes.convocatorias.titulo", panel.sinConvocatoria().stream()
                .map(p -> elementoConvocatoria(c, p.equipo(), p.partidoId(), p.rival(), p.dia(), p.hora()))
                .toList());

        c.anadir(RESULTADOS_PENDIENTES, "pendientes.resultados.titulo", panel.resultadosPendientes().stream()
                .map(p -> elementoResultado(c, p.equipo(), p.partidoId(), p.rival(), p.dia(), p.hora(),
                        p.diasDeRetraso()))
                .toList());
    }

    private void invitacionesDeCoordinacion(Contexto c, List<InvitacionDetalle> invitaciones) {

        LocalDateTime limite = c.ahora.minusHours(HORAS_SIN_LIBERAR);

        c.anadir(INVITACIONES_POR_APROBAR, "pendientes.invitaciones.titulo", invitaciones.stream()
                .filter(i -> i.estado() == EstadoInvitacion.PENDIENTE_COORDINACION)
                .map(i -> new Elemento(i.id(),
                        textos.texto(c.idioma, "pendientes.invitaciones.item",
                                "jugador", i.jugador(),
                                "equipoDestino", i.equipoDestino(),
                                "fecha", textos.fecha(c.idioma, i.dia())),
                        i.dia(), "INVITACIONES", null))
                .toList());

        c.anadir(INVITACIONES_SIN_LIBERAR, "pendientes.invitacionesSinLiberar.titulo", invitaciones.stream()
                .filter(i -> i.estado() == EstadoInvitacion.PENDIENTE_ENTRENADOR
                        && i.fechaPeticionEntrenador() != null
                        && i.fechaPeticionEntrenador().isBefore(limite))
                .map(i -> new Elemento(i.id(),
                        textos.texto(c.idioma, "pendientes.invitacionesSinLiberar.item",
                                "jugador", i.jugador(),
                                "equipoOrigen", i.equipoOrigen(),
                                "equipoDestino", i.equipoDestino(),
                                "fecha", textos.fecha(c.idioma, i.dia())),
                        i.dia(), "INVITACIONES", null))
                .toList());
    }

    /** Un entrenador, solo las de sus equipos; coordinación y administración, todas (lo decide el servicio). */
    private void solicitudesDeFamilia(Contexto c) {

        c.anadir(SOLICITUDES_FAMILIA, "pendientes.solicitudes.titulo",
                solicitudFamiliaService.pendientes(Revisor.app(c.usuarioId)).stream()
                        .map(s -> new Elemento(s.id(),
                                textos.texto(c.idioma, "pendientes.solicitudes.item", "nombre",
                                        (s.nombre() + " " + (s.apellidos() == null ? "" : s.apellidos())).trim()),
                                s.fechaSolicitud() == null ? null : s.fechaSolicitud().toLocalDate(),
                                "SOLICITUDES_FAMILIA", null))
                        .toList());
    }

    // ---- administración ------------------------------------------------------------------------------------------

    private void moderacion(Contexto c) {

        List<ReporteModeracionResponse> reportes = moderacionService.listar(c.usuarioId, "PENDIENTE");

        c.anadir(MODERACION, "pendientes.moderacion.titulo", reportes.stream()
                .map(r -> new Elemento(r.id(),
                        textos.texto(c.idioma, "pendientes.moderacion.item", "autor",
                                r.autor() == null || r.autor().nombre() == null ? "" : r.autor().nombre()),
                        r.fechaReporte() == null ? null : r.fechaReporte().toLocalDate(), "MODERACION", null))
                .toList());
    }

    private void cuentasCaducadas(Contexto c) {

        List<UsuarioPendienteResponse> pendientes = usuarioAppService.listarUsuariosPendientes(c.usuarioId.intValue());

        c.anadir(USUARIOS_CADUCADAS, "pendientes.cuentasCaducadas.titulo", pendientes.stream()
                .filter(UsuarioPendienteResponse::tokenCaducado)
                .map(u -> new Elemento((long) u.id(),
                        textos.texto(c.idioma, "pendientes.cuentasCaducadas.item", "email", u.email()),
                        u.fechaAlta() == null ? null : u.fechaAlta().toLocalDateTime().toLocalDate(),
                        "USUARIOS_CADUCADAS", null))
                .toList());
    }

    // ---- redes sociales ------------------------------------------------------------------------------------------

    /** Las mismas ventanas que los recordatorios: la previa de viernes 12:00 a domingo; los resultados, de lunes 9:00 a martes. */
    private void imagenesParaRedes(Contexto c) {

        LocalDate hoy = c.hoy();
        DayOfWeek dia = hoy.getDayOfWeek();
        LocalTime hora = c.ahora.toLocalTime();

        boolean ventanaPrevia = dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY
                || (dia == DayOfWeek.FRIDAY && !hora.isBefore(VIERNES_PREVIA));

        if (ventanaPrevia) {

            LocalDate viernes = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));

            if (!imagenesRedesService.hayGeneradas(ImagenesRedesService.TIPO_PREVIA, viernes)) {
                c.anadir(IMAGENES_PREVIA, "pendientes.imagenesPrevia.titulo", List.of(
                        new Elemento(null,
                                textos.texto(c.idioma, "pendientes.imagenesPrevia.item",
                                        "fecha", textos.fecha(c.idioma, viernes)),
                                viernes, "IMAGENES_PREVIA:" + viernes, null)));
            }
        }

        boolean ventanaResultados = dia == DayOfWeek.TUESDAY
                || (dia == DayOfWeek.MONDAY && !hora.isBefore(LUNES_RESULTADOS));

        if (ventanaResultados) {

            LocalDate viernes = hoy.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY));

            if (!imagenesRedesService.hayGeneradas(ImagenesRedesService.TIPO_RESULTADOS, viernes)) {
                c.anadir(IMAGENES_RESULTADOS, "pendientes.imagenesResultados.titulo", List.of(
                        new Elemento(null,
                                textos.texto(c.idioma, "pendientes.imagenesResultados.item",
                                        "fecha", textos.fecha(c.idioma, viernes)),
                                viernes, "IMAGENES_RESULTADOS:" + viernes, null)));
            }
        }
    }

    // ---- común ---------------------------------------------------------------------------------------------------

    private Elemento elementoConvocatoria(
            Contexto c, String equipo, Long partidoId, String rival, LocalDate dia, String hora) {

        return new Elemento(partidoId,
                textos.texto(c.idioma, "pendientes.convocatorias.item",
                        "equipo", nvl(equipo), "rival", nvl(rival),
                        "fecha", textos.fecha(c.idioma, dia), "hora", nvl(hora)),
                dia, "CONVOCATORIA_NUEVA:" + partidoId, null);
    }

    private Elemento elementoResultado(
            Contexto c, String equipo, Long partidoId, String rival, LocalDate dia, String hora, int diasRetraso) {

        return new Elemento(partidoId,
                textos.texto(c.idioma, "pendientes.resultados.item",
                        "equipo", nvl(equipo), "rival", nvl(rival), "fecha", textos.fecha(c.idioma, dia)),
                dia, "RESULTADO:" + partidoId, diasRetraso);
    }

    private static String nvl(String texto) {
        return texto == null ? "" : texto;
    }

    /** Lo que comparten los grupos de una misma petición. */
    private final class Contexto {

        private final Long usuarioId;
        private final String idioma;
        private final LocalDateTime ahora;
        private final List<Grupo> grupos;

        Contexto(Long usuarioId, String idioma, LocalDateTime ahora, List<Grupo> grupos) {
            this.usuarioId = usuarioId;
            this.idioma = idioma;
            this.ahora = ahora;
            this.grupos = grupos;
        }

        LocalDate hoy() {
            return ahora.toLocalDate();
        }

        /** El grupo con el total y los primeros 5 elementos; los grupos sin nada no se añaden. */
        void anadir(String codigo, String claveTitulo, List<Elemento> elementos) {

            if (elementos.isEmpty()) {
                return;
            }

            grupos.add(new Grupo(codigo, textos.texto(idioma, claveTitulo), elementos.size(),
                    List.copyOf(elementos.subList(0, Math.min(elementos.size(), PendientesResponse.MAXIMO_ELEMENTOS)))));
        }
    }
}
