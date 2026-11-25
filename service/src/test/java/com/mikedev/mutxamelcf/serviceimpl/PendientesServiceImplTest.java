package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.mikedev.mutxamelcf.model.PendientesResponse.Grupo;
import com.mikedev.mutxamelcf.model.ReporteModeracionResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioPendienteResponse;
import com.mikedev.mutxamelcf.service.CoordinacionService;
import com.mikedev.mutxamelcf.service.ImagenesRedesService;
import com.mikedev.mutxamelcf.service.ModeracionService;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Revision;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Revisor;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@ExtendWith(MockitoExtension.class)
class PendientesServiceImplTest {

    private static final Long USUARIO = 7L;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** Miércoles 25/11/2026, 18:00 (Madrid). */
    private static final LocalDateTime MIERCOLES = LocalDateTime.of(2026, 11, 25, 18, 0);
    private static final LocalDate HOY = MIERCOLES.toLocalDate();

    @Mock
    private UsuarioAppService usuarioAppService;
    @Mock
    private PerfilAppDao perfilAppDao;
    @Mock
    private EntrenamientoDao entrenamientoDao;
    @Mock
    private CoordinacionDao coordinacionDao;
    @Mock
    private CoordinacionService coordinacionService;
    @Mock
    private InvitacionDao invitacionDao;
    @Mock
    private SolicitudFamiliaService solicitudFamiliaService;
    @Mock
    private ModeracionService moderacionService;
    @Mock
    private ImagenesRedesService imagenesRedesService;
    @Mock
    private EquipoGestionDao equipoGestionDao;

    private final TextosAvisos textos = new TextosAvisos();

    @BeforeEach
    void setUp() {
        lenient().when(usuarioAppService.obtenerIdioma(anyInt())).thenReturn("es");
        lenient().when(perfilAppDao.obtenerEquiposPorUsuario(anyInt())).thenReturn(List.of());
        lenient().when(entrenamientoDao.obtenerListasSinRevisar(any(), any())).thenReturn(List.of());
        lenient().when(coordinacionDao.partidosEntre(any(), any())).thenReturn(List.of());
        lenient().when(invitacionDao.pendientes(any())).thenReturn(List.of());
        lenient().when(solicitudFamiliaService.pendientes(any())).thenReturn(List.of());
        lenient().when(moderacionService.listar(any(), any())).thenReturn(List.of());
        lenient().when(usuarioAppService.listarUsuariosPendientes(anyInt())).thenReturn(List.of());
        lenient().when(equipoGestionDao.obtenerRrss()).thenReturn(List.of(99L));
        lenient().when(coordinacionService.obtenerFinDeSemana(any(), any())).thenReturn(finDeSemana(List.of(), List.of()));
    }

    private PendientesServiceImpl servicio(LocalDateTime ahora) {
        return new PendientesServiceImpl(usuarioAppService, perfilAppDao, entrenamientoDao, coordinacionDao,
                coordinacionService, invitacionDao, solicitudFamiliaService, moderacionService, imagenesRedesService,
                equipoGestionDao, textos, Clock.fixed(ahora.atZone(MADRID).toInstant(), MADRID));
    }

    private PendientesResponse obtener(String... roles) {
        return obtener(MIERCOLES, roles);
    }

    private PendientesResponse obtener(LocalDateTime ahora, String... roles) {
        when(usuarioAppService.obtenerRoles(USUARIO.intValue())).thenReturn(java.util.Arrays.stream(roles).map(c -> {
            RolApp rol = new RolApp();
            rol.setCodigo(c);
            return rol;
        }).toList());

        return servicio(ahora).obtener(USUARIO);
    }

    private static Grupo grupo(PendientesResponse r, String codigo) {
        return r.grupos().stream().filter(g -> g.codigo().equals(codigo)).findFirst().orElse(null);
    }

    private static List<String> codigos(PendientesResponse r) {
        return r.grupos().stream().map(Grupo::codigo).toList();
    }

    private static Equipo equipo(long id, String nombre) {
        Equipo e = new Equipo();
        e.setId(id);
        e.setNombre(nombre);
        return e;
    }

    private static FinDeSemanaResponse finDeSemana(
            List<FinDeSemanaResponse.PartidoSinConvocatoria> sin, List<FinDeSemanaResponse.ResultadoPendiente> res) {
        return new FinDeSemanaResponse(LocalDate.of(2026, 11, 27), LocalDate.of(2026, 11, 29), sin, res, List.of(),
                List.of(), new FinDeSemanaResponse.Resumen(sin.size(), res.size(), 0, 0, null));
    }

    private static InvitacionDetalle invitacion(long id, EstadoInvitacion estado, long origen, LocalDateTime peticion) {
        return new InvitacionDetalle(id, 1L, 100L + id, "Jugador" + id, origen, "Origen", estado, 20L, "Destino",
                300L + id, HOY.plusDays(3), "11:00", "Rival", false, 5L, HOY.atStartOfDay(), peticion, false, null);
    }

    private static Revision revision(long id, String nombre) {
        return new Revision(id, nombre, "Pérez", "a@b.es", null, "MADRE", "PENDIENTE", null,
                HOY.minusDays(1).atTime(10, 0), null, List.of());
    }

    // ---- familia y jugador ---------------------------------------------------------------------------------------

    @Test
    void lasCuentasDeFamiliaOJugadorRecibenUnaListaVaciaSinConsultarNada() {
        assertThat(obtener("FAMILIAR", "JUGADOR").grupos()).isEmpty();

        verifyNoInteractions(entrenamientoDao, coordinacionDao, coordinacionService, invitacionDao,
                solicitudFamiliaService, moderacionService, imagenesRedesService);
    }

    @Test
    void unaCuentaSinRolesRecibeUnaListaVacia() {
        assertThat(obtener().grupos()).isEmpty();
    }

    @Test
    void unUsuarioNoValidoEsUnaSecurityException() {
        assertThatThrownBy(() -> servicio(MIERCOLES).obtener(null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> servicio(MIERCOLES).obtener(-1L)).isInstanceOf(SecurityException.class);
    }

    // ---- entrenador ----------------------------------------------------------------------------------------------

    @Test
    void entrenadorListasSinPasarSoloDeSusEquiposYYaEmpezadasYNoCanceladas() {
        when(perfilAppDao.obtenerEquiposPorUsuario(7)).thenReturn(List.of(equipo(10, "Alevín A")));
        when(entrenamientoDao.obtenerListasSinRevisar(HOY.minusDays(14), HOY)).thenReturn(List.of(
                new ListaSinRevisar(1L, 10L, 501L, HOY.minusDays(2), "18:00", "PROGRAMADA"),
                // Otro equipo.
                new ListaSinRevisar(2L, 11L, 502L, HOY.minusDays(2), "18:00", "PROGRAMADA"),
                // Cancelada.
                new ListaSinRevisar(3L, 10L, 503L, HOY.minusDays(1), "18:00", "CANCELADA"),
                // Hoy a las 19:00: todavía no ha empezado (son las 18:00).
                new ListaSinRevisar(4L, 10L, 504L, HOY, "19:00", "PROGRAMADA"),
                // Hoy a las 17:00: ya ha empezado.
                new ListaSinRevisar(5L, 10L, 505L, HOY, "17:00", "PROGRAMADA")));

        Grupo g = grupo(obtener("ENTRENADOR"), "LISTAS_SIN_PASAR");

        assertThat(g.titulo()).isEqualTo("Listas sin pasar");
        assertThat(g.cantidad()).isEqualTo(2);
        assertThat(g.elementos()).extracting(e -> e.id()).containsExactly(1L, 5L);
        assertThat(g.elementos()).extracting(e -> e.destino()).containsExactly("PASAR_LISTA:1", "PASAR_LISTA:5");
        assertThat(g.elementos().get(0).fecha()).isEqualTo(HOY.minusDays(2));
        assertThat(g.elementos().get(0).texto()).contains("Alevín A");
    }

    @Test
    void elContadorEsElTotalYLosElementosSeCortanEnCinco() {
        when(perfilAppDao.obtenerEquiposPorUsuario(7)).thenReturn(List.of(equipo(10, "Alevín A")));
        List<ListaSinRevisar> listas = new ArrayList<>();

        for (int i = 1; i <= 7; i++) {
            listas.add(new ListaSinRevisar((long) i, 10L, 500L + i, HOY.minusDays(i), "10:00", "PROGRAMADA"));
        }

        when(entrenamientoDao.obtenerListasSinRevisar(any(), any())).thenReturn(listas);

        Grupo g = grupo(obtener("ENTRENADOR"), "LISTAS_SIN_PASAR");

        assertThat(g.cantidad()).isEqualTo(7);
        assertThat(g.elementos()).hasSize(5);
    }

    @Test
    void entrenadorPartidosSinConvocatoriaEnSieteDiasYResultadosPendientesDeSusEquipos() {
        when(perfilAppDao.obtenerEquiposPorUsuario(7)).thenReturn(List.of(equipo(10, "Alevín A")));
        when(coordinacionDao.partidosEntre(HOY.minusDays(10), HOY.plusDays(7))).thenReturn(List.of(
                // Sin convocatoria, dentro de 3 días: sale.
                new PartidoCoordinacion(30L, 10L, "Alevín A", "Rival 30", HOY.plusDays(3), "11:00", true, false, null,
                        null, false),
                // Con convocatoria: no sale.
                new PartidoCoordinacion(31L, 10L, "Alevín A", "Rival 31", HOY.plusDays(4), "11:00", true, false, null,
                        null, true),
                // Cancelado: no sale.
                new PartidoCoordinacion(32L, 10L, "Alevín A", "Rival 32", HOY.plusDays(5), "11:00", true, true, null,
                        null, false),
                // De otro equipo: no sale.
                new PartidoCoordinacion(33L, 11L, "Benjamín", "Rival 33", HOY.plusDays(2), "11:00", true, false, null,
                        null, false),
                // Jugado hace 3 días sin resultado: resultado pendiente con 3 días de retraso.
                new PartidoCoordinacion(34L, 10L, "Alevín A", "Rival 34", HOY.minusDays(3), "11:00", true, false, null,
                        null, true),
                // Jugado con resultado: no sale.
                new PartidoCoordinacion(35L, 10L, "Alevín A", "Rival 35", HOY.minusDays(4), "11:00", true, false, 2,
                        "2 - 1", true),
                // Hoy a las 17:00: no ha terminado (termina a las 19:00), y ya empezó: ni una cosa ni la otra.
                new PartidoCoordinacion(36L, 10L, "Alevín A", "Rival 36", HOY, "17:00", true, false, null, null, false)));

        PendientesResponse r = obtener("ENTRENADOR");

        Grupo convocatorias = grupo(r, "CONVOCATORIAS_SIN_PUBLICAR");
        assertThat(convocatorias.cantidad()).isEqualTo(1);
        assertThat(convocatorias.elementos().get(0).id()).isEqualTo(30L);
        assertThat(convocatorias.elementos().get(0).destino()).isEqualTo("CONVOCATORIA_NUEVA:30");
        assertThat(convocatorias.elementos().get(0).equipoId()).isEqualTo(10L);
        assertThat(convocatorias.elementos().get(0).equipo()).isEqualTo("Alevín A");
        assertThat(convocatorias.elementos().get(0).rival()).isEqualTo("Rival 30");
        assertThat(convocatorias.elementos().get(0).hora()).isEqualTo("11:00");

        Grupo resultados = grupo(r, "RESULTADOS_PENDIENTES");
        assertThat(resultados.cantidad()).isEqualTo(1);
        assertThat(resultados.elementos().get(0).id()).isEqualTo(34L);
        assertThat(resultados.elementos().get(0).diasRetraso()).isEqualTo(3);
        assertThat(resultados.elementos().get(0).destino()).isEqualTo("RESULTADO:34");

        // Una sola consulta de partidos, no una por equipo, y sin pasar por el panel de coordinación.
        verify(coordinacionDao).partidosEntre(any(), any());
        verify(coordinacionService, never()).obtenerFinDeSemana(any(), any());
    }

    @Test
    void entrenadorSolicitudesDeFamiliaYJugadoresQueOtroEquipoPideLiberar() {
        when(perfilAppDao.obtenerEquiposPorUsuario(7)).thenReturn(List.of(equipo(10, "Alevín A")));
        when(solicitudFamiliaService.pendientes(Revisor.app(7L))).thenReturn(List.of(revision(1, "Ana"), revision(2, "Luis")));
        when(invitacionDao.pendientes(HOY)).thenReturn(List.of(
                // Suyo, pedido a su entrenador: sale.
                invitacion(1, EstadoInvitacion.PENDIENTE_ENTRENADOR, 10L, HOY.minusDays(1).atStartOfDay()),
                // De otro equipo: no.
                invitacion(2, EstadoInvitacion.PENDIENTE_ENTRENADOR, 11L, HOY.minusDays(1).atStartOfDay()),
                // Pendiente de coordinación: no es cosa del entrenador.
                invitacion(3, EstadoInvitacion.PENDIENTE_COORDINACION, 10L, null)));

        PendientesResponse r = obtener("ENTRENADOR");

        Grupo solicitudes = grupo(r, "SOLICITUDES_FAMILIA");
        assertThat(solicitudes.cantidad()).isEqualTo(2);
        assertThat(solicitudes.elementos()).extracting(e -> e.destino()).containsOnly("SOLICITUDES_FAMILIA");
        assertThat(solicitudes.elementos().get(0).texto()).isEqualTo("Ana Pérez");

        Grupo liberar = grupo(r, "JUGADORES_A_LIBERAR");
        assertThat(liberar.cantidad()).isEqualTo(1);
        assertThat(liberar.elementos().get(0).id()).isEqualTo(1L);
        assertThat(liberar.elementos().get(0).destino()).isEqualTo("INVITACIONES");

        // Un entrenador no ve los grupos de coordinación ni los de administración.
        assertThat(codigos(r)).doesNotContain("INVITACIONES_POR_APROBAR", "INVITACIONES_SIN_LIBERAR", "MODERACION",
                "USUARIOS_CADUCADAS", "IMAGENES_PREVIA", "IMAGENES_RESULTADOS");
    }

    @Test
    void losGruposConCeroNoSeDevuelven() {
        when(perfilAppDao.obtenerEquiposPorUsuario(7)).thenReturn(List.of(equipo(10, "Alevín A")));

        assertThat(obtener("ENTRENADOR").grupos()).isEmpty();
    }

    @Test
    void unEntrenadorSinEquiposNoRecibeNada() {
        assertThat(obtener("ENTRENADOR").grupos()).isEmpty();
        verify(entrenamientoDao, never()).obtenerListasSinRevisar(any(), any());
    }

    // ---- coordinación --------------------------------------------------------------------------------------------

    @Test
    void coordinacionVeLoMismoQueFinDeSemanaDeTodosLosEquiposConSuContador() {
        List<FinDeSemanaResponse.PartidoSinConvocatoria> sin = new ArrayList<>();

        for (int i = 1; i <= 7; i++) {
            sin.add(new FinDeSemanaResponse.PartidoSinConvocatoria(
                    10L + i, "Equipo " + i, 40L + i, "Rival", LocalDate.of(2026, 11, 28), "11:00", true));
        }

        when(coordinacionService.obtenerFinDeSemana(USUARIO, null)).thenReturn(finDeSemana(sin, List.of(
                new FinDeSemanaResponse.ResultadoPendiente(12L, "Equipo 2", 60L, "Rival", HOY.minusDays(4), "11:00", 4))));

        PendientesResponse r = obtener("COORDINADOR");

        Grupo convocatorias = grupo(r, "CONVOCATORIAS_SIN_PUBLICAR");
        assertThat(convocatorias.cantidad()).isEqualTo(7);
        assertThat(convocatorias.elementos()).hasSize(5);
        assertThat(convocatorias.elementos().get(0).destino()).isEqualTo("CONVOCATORIA_NUEVA:41");

        Grupo resultados = grupo(r, "RESULTADOS_PENDIENTES");
        assertThat(resultados.elementos().get(0).destino()).isEqualTo("RESULTADO:60");
        assertThat(resultados.elementos().get(0).diasRetraso()).isEqualTo(4);

        // No se consultan los partidos por su cuenta.
        verify(coordinacionDao, never()).partidosEntre(any(), any());
    }

    @Test
    void coordinacionInvitacionesPorAprobarYSinLiberarDeMasDeDoceHoras() {
        when(invitacionDao.pendientes(HOY)).thenReturn(List.of(
                invitacion(1, EstadoInvitacion.PENDIENTE_COORDINACION, 10L, null),
                invitacion(2, EstadoInvitacion.PENDIENTE_COORDINACION, 11L, null),
                // Pedido hace 13 horas: sin liberar.
                invitacion(3, EstadoInvitacion.PENDIENTE_ENTRENADOR, 10L, MIERCOLES.minusHours(13)),
                // Pedido hace 11 horas: todavía en plazo.
                invitacion(4, EstadoInvitacion.PENDIENTE_ENTRENADOR, 10L, MIERCOLES.minusHours(11))));
        when(solicitudFamiliaService.pendientes(Revisor.app(7L))).thenReturn(List.of(revision(1, "Ana")));

        PendientesResponse r = obtener("COORDINADOR");

        assertThat(grupo(r, "INVITACIONES_POR_APROBAR").cantidad()).isEqualTo(2);
        assertThat(grupo(r, "INVITACIONES_POR_APROBAR").elementos()).extracting(e -> e.destino())
                .containsOnly("INVITACIONES");

        Grupo sinLiberar = grupo(r, "INVITACIONES_SIN_LIBERAR");
        assertThat(sinLiberar.cantidad()).isEqualTo(1);
        assertThat(sinLiberar.elementos().get(0).id()).isEqualTo(3L);

        assertThat(grupo(r, "SOLICITUDES_FAMILIA").cantidad()).isEqualTo(1);
        assertThat(codigos(r)).doesNotContain("MODERACION", "USUARIOS_CADUCADAS");
        // Coordinación tampoco es RRSS: nada de imágenes.
        verifyNoInteractions(imagenesRedesService);
    }

    // ---- administración ------------------------------------------------------------------------------------------

    @Test
    void administracionModeracionYCuentasCaducadas() {
        List<ReporteModeracionResponse> reportes = new ArrayList<>();

        for (long i = 1; i <= 6; i++) {
            reportes.add(new ReporteModeracionResponse(i, 100 + i, HOY.minusDays(1).atTime(9, 0), "motivo",
                    null, new ReporteModeracionResponse.Autor(50L, "Autor " + i, true, false), "t", "c", null,
                    "PENDIENTE", null, null, null, 0));
        }

        when(moderacionService.listar(USUARIO, "PENDIENTE")).thenReturn(reportes);
        when(usuarioAppService.listarUsuariosPendientes(7)).thenReturn(List.of(
                cuentaPendiente(1, "caducada@x.es", true), cuentaPendiente(2, "vigente@x.es", false)));

        PendientesResponse r = obtener("ADMIN_APP");

        Grupo moderacion = grupo(r, "MODERACION");
        assertThat(moderacion.cantidad()).isEqualTo(6);
        assertThat(moderacion.elementos()).hasSize(5);
        assertThat(moderacion.elementos().get(0).destino()).isEqualTo("MODERACION");

        Grupo caducadas = grupo(r, "USUARIOS_CADUCADAS");
        assertThat(caducadas.cantidad()).isEqualTo(1);
        assertThat(caducadas.elementos().get(0).texto()).isEqualTo("caducada@x.es");
        assertThat(caducadas.elementos().get(0).destino()).isEqualTo("USUARIOS_CADUCADAS");
    }

    @Test
    void coordinadorNoVeModeracionNiCuentasCaducadas() {
        obtener("COORDINADOR");

        verify(moderacionService, never()).listar(any(), any());
        verify(usuarioAppService, never()).listarUsuariosPendientes(anyInt());
    }

    private static UsuarioPendienteResponse cuentaPendiente(int id, String email, boolean caducado) {
        return new UsuarioPendienteResponse(id, email, "N", "A", List.of("JUGADOR"), List.of(),
                Timestamp.valueOf(HOY.minusDays(9).atStartOfDay()), caducado, 0);
    }

    // ---- redes sociales ------------------------------------------------------------------------------------------

    private static final LocalDate VIERNES = LocalDate.of(2026, 11, 27);

    @Test
    void rrssLaPreviaDesdeElViernesALasDoceHastaElDomingo() {
        when(imagenesRedesService.hayGeneradas("PREVIA", VIERNES)).thenReturn(false);

        // Viernes 11:59: todavía no.
        assertThat(obtener(VIERNES.atTime(11, 59), "RRSS").grupos()).isEmpty();

        // Viernes 12:00, sábado y domingo: sí, con el viernes del fin de semana.
        for (LocalDateTime momento : List.of(VIERNES.atTime(12, 0), VIERNES.plusDays(1).atTime(10, 0),
                VIERNES.plusDays(2).atTime(23, 0))) {

            Grupo g = grupo(obtener(momento, "RRSS"), "IMAGENES_PREVIA");

            assertThat(g.cantidad()).isEqualTo(1);
            assertThat(g.elementos().get(0).destino()).isEqualTo("IMAGENES_PREVIA:2026-11-27");
            assertThat(g.elementos().get(0).fecha()).isEqualTo(VIERNES);
        }

        // Lunes por la mañana: ya no es la previa (y los resultados todavía no tocan hasta las 9:00).
        assertThat(obtener(VIERNES.plusDays(3).atTime(8, 0), "RRSS").grupos()).isEmpty();
    }

    @Test
    void rrssLosResultadosDesdeElLunesALasNueveHastaElMartes() {
        when(imagenesRedesService.hayGeneradas("RESULTADOS", VIERNES)).thenReturn(false);
        LocalDate lunes = VIERNES.plusDays(3);

        assertThat(obtener(lunes.atTime(8, 59), "RRSS").grupos()).isEmpty();

        for (LocalDateTime momento : List.of(lunes.atTime(9, 0), lunes.plusDays(1).atTime(20, 0))) {

            Grupo g = grupo(obtener(momento, "RRSS"), "IMAGENES_RESULTADOS");

            assertThat(g.cantidad()).isEqualTo(1);
            assertThat(g.elementos().get(0).destino()).isEqualTo("IMAGENES_RESULTADOS:2026-11-27");
        }

        // Miércoles: fuera de las dos ventanas.
        assertThat(obtener(lunes.plusDays(2).atTime(10, 0), "RRSS").grupos()).isEmpty();
    }

    @Test
    void rrssNoVeNadaSiLasImagenesYaEstanGeneradas() {
        when(imagenesRedesService.hayGeneradas("PREVIA", VIERNES)).thenReturn(true);

        assertThat(obtener(VIERNES.atTime(13, 0), "RRSS").grupos()).isEmpty();
    }

    @Test
    void adminSoloHaceDeRrssSiNoHayNadieConEseRol() {
        LocalDateTime viernes = VIERNES.atTime(13, 0);
        when(imagenesRedesService.hayGeneradas("PREVIA", VIERNES)).thenReturn(false);

        // Hay alguien con RRSS: el administrador no recibe el aviso.
        assertThat(codigos(obtener(viernes, "ADMIN_APP"))).doesNotContain("IMAGENES_PREVIA");

        // No hay nadie con RRSS: lo recibe.
        when(equipoGestionDao.obtenerRrss()).thenReturn(List.of());
        assertThat(codigos(obtener(viernes, "ADMIN_APP"))).contains("IMAGENES_PREVIA");
    }

    @Test
    void rrssSinOtrosRolesNoConsultaNadaDeCoordinacion() {
        when(imagenesRedesService.hayGeneradas(any(), any())).thenReturn(false);

        obtener(VIERNES.atTime(13, 0), "RRSS");

        verifyNoInteractions(coordinacionService, invitacionDao, solicitudFamiliaService, moderacionService);
        verify(equipoGestionDao, never()).obtenerRrss();
    }

    // ---- idioma --------------------------------------------------------------------------------------------------

    @Test
    void losTitulosVanEnElIdiomaDeLaCuenta() {
        when(perfilAppDao.obtenerEquiposPorUsuario(7)).thenReturn(List.of(equipo(10, "Alevín A")));
        when(entrenamientoDao.obtenerListasSinRevisar(any(), any())).thenReturn(List.of(
                new ListaSinRevisar(1L, 10L, 501L, HOY.minusDays(2), "18:00", "PROGRAMADA")));

        when(usuarioAppService.obtenerIdioma(7)).thenReturn("ca");
        assertThat(grupo(obtener("ENTRENADOR"), "LISTAS_SIN_PASAR").titulo()).isEqualTo("Llistes sense passar");

        when(usuarioAppService.obtenerIdioma(7)).thenReturn("en");
        assertThat(grupo(obtener("ENTRENADOR"), "LISTAS_SIN_PASAR").titulo()).isEqualTo("Attendance lists not taken");

        when(usuarioAppService.obtenerIdioma(7)).thenReturn("es");
        assertThat(grupo(obtener("ENTRENADOR"), "LISTAS_SIN_PASAR").titulo()).isEqualTo("Listas sin pasar");
    }

    @Test
    void lasSolicitudesSeRevisanConLaPropiaCuenta() {
        // Garantiza que la cuenta que revisa solicitudes es la propia (no otra).
        when(perfilAppDao.obtenerEquiposPorUsuario(7)).thenReturn(List.of(equipo(10, "Alevín A")));

        obtener("ENTRENADOR");

        verify(solicitudFamiliaService).pendientes(eq(Revisor.app(7L)));
    }
}
