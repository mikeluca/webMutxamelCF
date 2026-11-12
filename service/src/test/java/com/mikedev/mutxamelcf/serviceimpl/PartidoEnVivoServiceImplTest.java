package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.DescansoEquipoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.dao.impl.PartidoAlineacionDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.PartidoLiveEventoDaoImpl;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EstadoInvitacion;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.TextoDifusion;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugador;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.InvitadosService;
import com.mikedev.mutxamelcf.service.PartidoService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * Retransmisión por partido: eventos reales sobre H2 (modo Oracle), con un reloj
 * que se puede adelantar para probar la deduplicación y el plazo de deshacer.
 */
class PartidoEnVivoServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final Long USUARIO = 7L;
    private static final Long PARTIDO = 30L;
    private static final Long PRIMER_EQUIPO = 21L;

    private static final LocalDate HOY = LocalDate.of(2026, 10, 24);

    /** Reloj que se adelanta a mano. */
    private static final class RelojManual extends Clock {
        private Instant ahora;

        RelojManual(LocalDateTime inicio) {
            this.ahora = inicio.atZone(MADRID).toInstant();
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return MADRID;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private PartidoLiveEventoDaoImpl eventoDao;
    private PartidoAlineacionDaoImpl alineacionDao;
    private final TemporadaDao temporadaDao = mock(TemporadaDao.class);
    private final com.mikedev.mutxamelcf.dao.ResumenTemporadaDao resumenTemporadaDao =
            mock(com.mikedev.mutxamelcf.dao.ResumenTemporadaDao.class);
    private final com.mikedev.mutxamelcf.service.ComunicacionService comunicacionService =
            mock(com.mikedev.mutxamelcf.service.ComunicacionService.class);
    private RelojManual reloj;

    private PartidoDao partidoDao;
    private EquipoDao equipoDao;
    private EquipoGestionDao equipoGestionDao;
    private ConvocatoriaDao convocatoriaDao;
    private ConvocatoriaJugadorDao convocatoriaJugadorDao;
    private JustificacionFaltaPartidoDao justificacionDao;
    private PartidoEstadisticaJugadorDao estadisticaDao;
    private NotificacionAppService notificacionAppService;
    private UsuarioAppService usuarioAppService;
    private FcmPushService fcmPushService;

    private Partido partido;
    private PartidoEnVivoServiceImpl service;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        eventoDao = spy(new PartidoLiveEventoDaoImpl(jdbc));
        alineacionDao = new PartidoAlineacionDaoImpl(jdbc);

        EsquemaH2.crear(jdbc);
        jdbc.execute("""
                CREATE TABLE JUGADORES (
                    ID NUMBER PRIMARY KEY, DORSAL NUMBER, NOMBRE VARCHAR2(50), APELLIDOS VARCHAR2(50),
                    NOMBRE_RETRANSMISION VARCHAR2(40 CHAR))""");
        jdbc.execute("CREATE TABLE PARTIDOS (ID NUMBER PRIMARY KEY, FORMACION VARCHAR2(10 CHAR))");
        jdbc.update("INSERT INTO PARTIDOS (ID) VALUES (30)");
        jdbc.update("INSERT INTO JUGADORES (ID, DORSAL, NOMBRE, APELLIDOS) VALUES (10, 9, 'Pablo', 'Garcia')");
        jdbc.update("INSERT INTO JUGADORES (ID, DORSAL, NOMBRE, APELLIDOS) VALUES (11, 1, 'Luis', 'Lopez')");
        jdbc.update("INSERT INTO JUGADORES (ID, DORSAL, NOMBRE, APELLIDOS) VALUES (12, NULL, 'Sin Dorsal', 'Perez')");
        jdbc.update("INSERT INTO JUGADORES (ID, DORSAL, NOMBRE, APELLIDOS) VALUES (13, 5, 'Fuera', 'Convocatoria')");

        reloj = new RelojManual(HOY.atTime(11, 0));

        partidoDao = mock(PartidoDao.class);
        equipoDao = mock(EquipoDao.class);
        equipoGestionDao = mock(EquipoGestionDao.class);
        when(equipoGestionDao.equipoAdmiteEstadisticas(PRIMER_EQUIPO)).thenReturn(true);
        convocatoriaDao = mock(ConvocatoriaDao.class);
        convocatoriaJugadorDao = mock(ConvocatoriaJugadorDao.class);
        justificacionDao = mock(JustificacionFaltaPartidoDao.class);
        estadisticaDao = mock(PartidoEstadisticaJugadorDao.class);
        notificacionAppService = mock(NotificacionAppService.class);
        usuarioAppService = mock(UsuarioAppService.class);
        fcmPushService = mock(FcmPushService.class);

        when(usuarioAppService.tieneRol(USUARIO.intValue(), "RETRANSMISION")).thenReturn(true);

        Equipo primerEquipo = new Equipo();
        primerEquipo.setId(PRIMER_EQUIPO);
        primerEquipo.setNombre("Primer Equipo");
        when(equipoDao.obtenerEquipoPorNombre("Primer Equipo")).thenReturn(primerEquipo);

        partido = partido(PARTIDO, "Pego C.F.", HOY, "11:00");
        when(partidoDao.obtenerPorId(PARTIDO)).thenReturn(partido);

        // Convocatoria con tres jugadores (el 13 no está convocado).
        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setId(200L);
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(convocatoria);
        when(convocatoriaJugadorDao.obtenerPorConvocatoria(200L)).thenReturn(List.of(
                new ConvocatoriaJugador(1L, 200L, 10L),
                new ConvocatoriaJugador(2L, 200L, 11L),
                new ConvocatoriaJugador(3L, 200L, 12L)));

        when(estadisticaDao.obtenerPorPartido(PARTIDO)).thenReturn(List.of());

        service = nuevoServicio(reloj);
    }

    private PartidoEnVivoServiceImpl nuevoServicio(Clock clock) {
        return new PartidoEnVivoServiceImpl(
                eventoDao, partidoDao, equipoDao, equipoGestionDao, convocatoriaDao,
                convocatoriaJugadorDao, justificacionDao, estadisticaDao, alineacionDao,
                new AvisoTarjetas(temporadaDao, resumenTemporadaDao, equipoGestionDao, comunicacionService,
                        new ReglaSancionAmarillas(5, 1), mock(PlatformTransactionManager.class), clock,
                        AvisosTest.TEXTOS, AvisosTest.idiomas()),
                notificacionAppService, usuarioAppService, fcmPushService, clock, AvisosTest.TEXTOS);
    }

    /** Las difusiones a todos hechas hasta ahora (en orden), como las verían las cuentas del idioma. */
    @SuppressWarnings("unchecked")
    private List<TextoDifusion> todasLasDifusiones(String idioma) {
        ArgumentCaptor<java.util.function.Function<String, TextoDifusion>> captor = ArgumentCaptor
                .forClass(java.util.function.Function.class);
        verify(notificacionAppService, org.mockito.Mockito.atLeast(0)).difundirATodos(eq("RESULTADO"), captor.capture(),
                isNull());
        return captor.getAllValues().stream().map(f -> f.apply(idioma)).toList();
    }

    private TextoDifusion difusion(String idioma, int posicion) {
        List<TextoDifusion> todas = todasLasDifusiones(idioma);
        return todas.get(posicion < 0 ? todas.size() + posicion : posicion);
    }

    private static Partido partido(Long id, String rival, LocalDate dia, String hora) {
        Partido p = new Partido();
        p.setId(id);
        p.setEquipoId(PRIMER_EQUIPO);
        p.setRival(rival);
        p.setDia(Date.valueOf(dia));
        p.setHora(hora);
        return p;
    }

    private EstadoPartidoEnVivoResponse empezarPartido() {
        return service.enviarInicioPartido(USUARIO, PARTIDO);
    }

    // ---- partido y rol ---------------------------------------------------------------

    @Test
    void sinElRolRetransmisionNoSeHaceNada() {
        when(usuarioAppService.tieneRol(USUARIO.intValue(), "RETRANSMISION")).thenReturn(false);

        assertThatThrownBy(() -> service.enviarInicioPartido(USUARIO, PARTIDO))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.obtenerPartidosDisponibles(USUARIO))
                .isInstanceOf(SecurityException.class);

        verify(notificacionAppService, never()).difundirATodos(any(), any(), any());
    }

    @Test
    void unPartidoQueNoExisteEsError() {
        assertThatThrownBy(() -> service.enviarInicioPartido(USUARIO, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
        assertThatThrownBy(() -> service.enviarInicioPartido(USUARIO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unPartidoDeOtroEquipoEsError() {
        partido.setEquipoId(99L);

        assertThatThrownBy(() -> service.enviarInicioPartido(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Primer Equipo");
    }

    @Test
    void unPartidoCanceladoEsError() {
        partido.setCancelado(true);

        assertThatThrownBy(() -> service.enviarInicioPartido(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void unPartidoQueNoEsDeHoyEsError() {
        partido.setDia(Date.valueOf(HOY.plusDays(1)));
        assertThatThrownBy(() -> service.enviarInicioPartido(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hoy");

        partido.setDia(Date.valueOf(HOY.minusDays(1)));
        assertThatThrownBy(() -> service.enviarInicioPartido(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinPartidoValidoNingunEventoSeDifunde() {
        partido.setCancelado(true);

        assertThatThrownBy(() -> service.enviarGolContra(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class);

        verify(notificacionAppService, never()).difundirATodos(any(), any(), any());
        verify(fcmPushService, never()).enviarATopic(any(), any(), any());
    }

    // ---- orden de los eventos ------------------------------------------------------

    @Test
    void noHayGolAntesDelInicio() {
        assertThatThrownBy(() -> service.enviarGolContra(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no ha empezado");

        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, 10L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noSePuedeEmpezarDosVecesNiSaltarseElOrden() {
        empezarPartido();

        assertThatThrownBy(this::empezarPartido).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enviarSegundaParte(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class);

        service.enviarDescanso(USUARIO, PARTIDO);

        assertThatThrownBy(() -> service.enviarDescanso(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class);
        // En el descanso no hay goles.
        assertThatThrownBy(() -> service.enviarGolContra(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class);

        service.enviarSegundaParte(USUARIO, PARTIDO);
        service.enviarGolContra(USUARIO, PARTIDO);
    }

    @Test
    void despuesDelFinalNoSePuedeNada() {
        empezarPartido();
        service.enviarFinalPartido(USUARIO, PARTIDO);

        assertThatThrownBy(() -> service.enviarGolContra(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminado");
        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, 10L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enviarFinalPartido(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enviarDescanso(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noSeAcabaUnPartidoQueNoHaEmpezado() {
        assertThatThrownBy(() -> service.enviarFinalPartido(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lasFasesSiguenElOrdenDelPartido() {
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).fase())
                .isEqualTo(EstadoPartidoEnVivoResponse.SIN_EMPEZAR);

        assertThat(empezarPartido().fase()).isEqualTo(EstadoPartidoEnVivoResponse.PRIMERA_PARTE);
        assertThat(service.enviarGolContra(USUARIO, PARTIDO).fase())
                .isEqualTo(EstadoPartidoEnVivoResponse.PRIMERA_PARTE);
        assertThat(service.enviarDescanso(USUARIO, PARTIDO).fase()).isEqualTo(EstadoPartidoEnVivoResponse.DESCANSO);
        assertThat(service.enviarSegundaParte(USUARIO, PARTIDO).fase())
                .isEqualTo(EstadoPartidoEnVivoResponse.SEGUNDA_PARTE);
        assertThat(service.enviarFinalPartido(USUARIO, PARTIDO).fase()).isEqualTo(EstadoPartidoEnVivoResponse.FINAL);
    }

    // ---- goles y autor -----------------------------------------------------------------

    @Test
    void elMarcadorYLosGoleadoresSalenDeLosEventos() {
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(5));
        service.enviarGolContra(USUARIO, PARTIDO);
        reloj.avanzar(Duration.ofMinutes(5));
        EstadoPartidoEnVivoResponse estado = service.enviarGolFavor(USUARIO, PARTIDO, null, "p.p.");

        assertThat(estado.golesFavor()).isEqualTo(2);
        assertThat(estado.golesContra()).isEqualTo(1);
        assertThat(estado.goleadores()).containsExactly("Pablo Garcia", "p.p.");
        assertThat(estado.rival()).isEqualTo("Pego C.F.");
        assertThat(estado.eventos()).extracting(e -> e.tipo())
                .containsExactly("INICIO", "GOL_FAVOR", "GOL_CONTRA", "GOL_FAVOR");
        assertThat(estado.eventos().get(1).jugadorId()).isEqualTo(10L);
    }

    @Test
    void elGolPorJugadorIdSoloAceptaConvocados() {
        empezarPartido();

        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, 13L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("convocados");
        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, 999L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elAutorEsUnJugadorOUnTextoPeroNuncaLosDosNiNinguno() {
        empezarPartido();

        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, null, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, 10L, "Otro"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elTextoDelAutorTieneComoMaximo60CaracteresYPropiaPuertaSeNormaliza() {
        empezarPartido();

        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, null, "x".repeat(61)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60");

        service.enviarGolFavor(USUARIO, PARTIDO, null, "x".repeat(60));
        reloj.avanzar(Duration.ofMinutes(1));
        EstadoPartidoEnVivoResponse estado = service.enviarGolFavor(USUARIO, PARTIDO, null, " P.P. ");

        assertThat(estado.goleadores()).containsExactly("x".repeat(60), "p.p.");
    }

    @Test
    void sinConvocatoriaLosAutoresSonLaPlantillaDelPrimerEquipo() {
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(null);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(PRIMER_EQUIPO)).thenReturn(List.of(10L, 13L));

        empezarPartido();

        // El 13 (fuera de la convocatoria) vale porque no hay convocatoria.
        assertThat(service.enviarGolFavor(USUARIO, PARTIDO, 13L, null).goleadores())
                .containsExactly("Fuera Convocatoria");
        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, 11L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losConvocadosVanOrdenadosPorDorsalYLosSinDorsalAlFinal() {
        assertThat(service.obtenerConvocados(USUARIO, PARTIDO))
                .extracting(c -> c.dorsal() + ":" + c.nombre())
                .containsExactly("1:Luis Lopez", "9:Pablo Garcia", "null:Sin Dorsal Perez");
    }

    // ---- deduplicación -----------------------------------------------------------------

    @Test
    void elMismoGolReintentadoEnMenosDe15SegundosNoSeSumaDosVeces() {
        empezarPartido();

        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofSeconds(10));
        EstadoPartidoEnVivoResponse estado = service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);

        assertThat(estado.golesFavor()).isEqualTo(1);
        // Y solo se difundió una vez (inicio del partido + un gol).
        assertThat(todasLasDifusiones("es")).extracting(TextoDifusion::titulo)
                .containsExactly("⚽ ¡Empieza el partido!", "⚽ ¡GOOOL del Mutxamel CF!");
    }

    @Test
    void pasadosLos15SegundosOUnAutorDistintoElGolSiCuenta() {
        empezarPartido();

        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofSeconds(5));
        assertThat(service.enviarGolFavor(USUARIO, PARTIDO, 11L, null).golesFavor()).isEqualTo(2);

        reloj.avanzar(Duration.ofSeconds(16));
        assertThat(service.enviarGolFavor(USUARIO, PARTIDO, 11L, null).golesFavor()).isEqualTo(3);
    }

    @Test
    void tras_deshacerUnGolSePuedeVolverAAnotarAlMomento() {
        empezarPartido();

        EstadoPartidoEnVivoResponse estado = service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        service.deshacer(USUARIO, PARTIDO, estado.ultimoEventoDeshacible().id());

        reloj.avanzar(Duration.ofSeconds(3));

        assertThat(service.enviarGolFavor(USUARIO, PARTIDO, 10L, null).golesFavor()).isEqualTo(1);
    }

    // ---- deshacer ----------------------------------------------------------------------

    @Test
    void elUltimoEventoSePuedeDeshacerDuranteDosMinutos() {
        empezarPartido();
        EstadoPartidoEnVivoResponse conGol = service.enviarGolContra(USUARIO, PARTIDO);

        assertThat(conGol.ultimoEventoDeshacible()).isNotNull();
        assertThat(conGol.ultimoEventoDeshacible().expira())
                .isEqualTo(conGol.eventos().get(1).fecha().plusMinutes(2));

        reloj.avanzar(Duration.ofMinutes(2));

        EstadoPartidoEnVivoResponse deshecho = service.deshacer(USUARIO, PARTIDO,
                conGol.ultimoEventoDeshacible().id());

        assertThat(deshecho.golesContra()).isZero();
        assertThat(deshecho.eventos()).extracting(e -> e.tipo()).containsExactly("INICIO");
    }

    @Test
    void pasadosLosDosMinutosYaNoSePuedeDeshacerNiSeOfrece() {
        empezarPartido();
        EstadoPartidoEnVivoResponse conGol = service.enviarGolContra(USUARIO, PARTIDO);
        Long id = conGol.ultimoEventoDeshacible().id();

        reloj.avanzar(Duration.ofMinutes(2).plusSeconds(1));

        assertThatThrownBy(() -> service.deshacer(USUARIO, PARTIDO, id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 minutos");

        assertThat(service.obtenerEstado(USUARIO, PARTIDO).ultimoEventoDeshacible()).isNull();
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).golesContra()).isEqualTo(1);
    }

    @Test
    void soloSePuedeDeshacerElUltimoEvento() {
        empezarPartido();
        EstadoPartidoEnVivoResponse primero = service.enviarGolContra(USUARIO, PARTIDO);
        Long idPrimero = primero.ultimoEventoDeshacible().id();

        reloj.avanzar(Duration.ofSeconds(30));
        EstadoPartidoEnVivoResponse segundo = service.enviarGolContra(USUARIO, PARTIDO);

        assertThatThrownBy(() -> service.deshacer(USUARIO, PARTIDO, idPrimero))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("último");

        // Deshaciendo el último, el anterior pasa a ser el último.
        service.deshacer(USUARIO, PARTIDO, segundo.ultimoEventoDeshacible().id());
        assertThat(service.deshacer(USUARIO, PARTIDO, idPrimero).golesContra()).isZero();
    }

    @Test
    void elInicioNoSePuedeDeshacerNiSeOfrece() {
        EstadoPartidoEnVivoResponse estado = empezarPartido();

        assertThat(estado.ultimoEventoDeshacible()).isNull();

        assertThatThrownBy(() -> service.deshacer(USUARIO, PARTIDO, estado.eventos().get(0).id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inicio");
    }

    @Test
    void unEventoAnuladoNoSePuedeAnularDosVecesNiUnoDeOtroPartido() {
        empezarPartido();
        Long id = service.enviarGolContra(USUARIO, PARTIDO).ultimoEventoDeshacible().id();

        service.deshacer(USUARIO, PARTIDO, id);

        assertThatThrownBy(() -> service.deshacer(USUARIO, PARTIDO, id))
                .isInstanceOf(IllegalArgumentException.class);

        // Un evento del partido 30 no se puede deshacer desde el partido 31.
        Long idDelPrimero = service.enviarGolContra(USUARIO, PARTIDO).ultimoEventoDeshacible().id();

        Partido otro = partido(31L, "Elx", HOY, "18:00");
        when(partidoDao.obtenerPorId(31L)).thenReturn(otro);
        service.enviarInicioPartido(USUARIO, 31L);

        assertThatThrownBy(() -> service.deshacer(USUARIO, 31L, idDelPrimero))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deshacerUnGolDifundeLaCorreccionConElMarcadorNuevo() {
        empezarPartido();
        service.enviarGolContra(USUARIO, PARTIDO);
        reloj.avanzar(Duration.ofSeconds(20));
        EstadoPartidoEnVivoResponse conGol = service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);

        service.deshacer(USUARIO, PARTIDO, conGol.ultimoEventoDeshacible().id());

        TextoDifusion anulado = todasLasDifusiones("es").stream()
                .filter(t -> t.titulo().equals("↩️ Gol anulado")).findFirst().orElseThrow();

        assertThat(anulado.mensaje()).isEqualTo("Se anula el último gol. Mutxamel CF 0 - 1 Pego C.F.");
        verify(fcmPushService).enviarATopic("resultados_es", "↩️ Gol anulado", anulado.mensaje());
        verify(fcmPushService).enviarATopic("resultados_ca", "↩️ Gol anul·lat",
                "S'anul·la l'últim gol. Mutxamel CF 0 - 1 Pego C.F.");
        verify(fcmPushService).enviarATopic("resultados_en", "↩️ Goal disallowed",
                "The last goal has been disallowed. Mutxamel CF 0 - 1 Pego C.F.");
        verify(fcmPushService).enviarATopic("resultados", "↩️ Gol anulado", anulado.mensaje());
    }

    @Test
    void deshacerUnDescansoNoDifundeNadaYVuelveALaPrimeraParte() {
        empezarPartido();
        EstadoPartidoEnVivoResponse descanso = service.enviarDescanso(USUARIO, PARTIDO);

        EstadoPartidoEnVivoResponse estado = service.deshacer(USUARIO, PARTIDO,
                descanso.ultimoEventoDeshacible().id());

        assertThat(estado.fase()).isEqualTo(EstadoPartidoEnVivoResponse.PRIMERA_PARTE);
        assertThat(todasLasDifusiones("es")).extracting(TextoDifusion::titulo).doesNotContain("↩️ Gol anulado");
    }

    // ---- difusión ------------------------------------------------------------------------

    @Test
    void cadaEventoSeDifundeComoAntesConElMarcador() {
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);

        assertThat(difusion("es", 0)).isEqualTo(
                new TextoDifusion("⚽ ¡Empieza el partido!", "Mutxamel CF - Pego C.F."));
        assertThat(difusion("es", 1)).isEqualTo(new TextoDifusion("⚽ ¡GOOOL del Mutxamel CF!",
                "Pablo Garcia (1')\n\nMutxamel CF 1 - 0 Pego C.F."));

        // El mismo gol, en un topic por idioma (el marcador no cambia) y, en castellano, en el antiguo.
        String marcador = "Pablo Garcia (1')\n\nMutxamel CF 1 - 0 Pego C.F.";
        verify(fcmPushService).enviarATopic("resultados_es", "⚽ ¡GOOOL del Mutxamel CF!", marcador);
        verify(fcmPushService).enviarATopic("resultados_ca", "⚽ GOOOL del Mutxamel CF!", marcador);
        verify(fcmPushService).enviarATopic("resultados_en", "⚽ GOAL for Mutxamel CF!", marcador);
        verify(fcmPushService).enviarATopic("resultados", "⚽ ¡GOOOL del Mutxamel CF!", marcador);
    }

    @Test
    void elFinalIncluyeElMarcadorYLosGoleadores() {
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, null, "p.p.");

        service.enviarFinalPartido(USUARIO, PARTIDO);

        assertThat(difusion("es", -1)).isEqualTo(new TextoDifusion("🏁 Final del partido",
                "Mutxamel CF 2 - 0 Pego C.F.\n\nGoleadores: Pablo Garcia, Gol en propia puerta del rival"));
        assertThat(difusion("ca", -1).mensaje()).isEqualTo(
                "Mutxamel CF 2 - 0 Pego C.F.\n\nGolejadors: Pablo Garcia, Gol en pròpia porta del rival");
    }

    // ---- final: guarda el resultado -------------------------------------------------------

    @Test
    void elFinalGuardaElResultadoEnElPartidoYMarcaElAviso() {
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, 11L, null);
        service.enviarGolContra(USUARIO, PARTIDO);

        service.enviarFinalPartido(USUARIO, PARTIDO);

        ArgumentCaptor<Partido> guardado = ArgumentCaptor.forClass(Partido.class);
        verify(partidoDao).actualizar(guardado.capture());

        assertThat(guardado.getValue().getGolesFavor()).isEqualTo(2);
        assertThat(guardado.getValue().getGolesContra()).isEqualTo(1);
        // Misma convención de siempre: goles Mutxamel - goles rival.
        assertThat(guardado.getValue().getResultado()).isEqualTo("2-1");
        assertThat(guardado.getValue().getUsuarioActualizoId()).isEqualTo(USUARIO);
        verify(partidoDao).marcarAvisoResultadoEnviado(PARTIDO);
    }

    @Test
    void elFinalGuardaLosGolesPorJugadorSiNoHabiaEstadisticas() {
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, 11L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, null, "p.p.");
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, null, "Un amigo");

        service.enviarFinalPartido(USUARIO, PARTIDO);

        ArgumentCaptor<PartidoEstadisticaJugador> guardadas = ArgumentCaptor.forClass(PartidoEstadisticaJugador.class);
        verify(estadisticaDao, times(2)).guardar(guardadas.capture());

        // Pablo 2 goles y Luis 1; "p.p." y "Un amigo" cuentan en el marcador pero no aquí.
        assertThat(guardadas.getAllValues())
                .extracting(PartidoEstadisticaJugador::getJugadorId, PartidoEstadisticaJugador::getGoles)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, 2),
                        org.assertj.core.groups.Tuple.tuple(11L, 1));
        assertThat(guardadas.getAllValues()).allMatch(e -> e.getAsistencias() == 0
                && e.getTarjetasAmarillas() == 0 && !e.isTarjetaRoja() && PARTIDO.equals(e.getPartidoId()));

        ArgumentCaptor<Partido> partidoGuardado = ArgumentCaptor.forClass(Partido.class);
        verify(partidoDao).actualizar(partidoGuardado.capture());
        assertThat(partidoGuardado.getValue().getGolesFavor()).isEqualTo(5);
    }

    @Test
    void elFinalDeUnEquipoQueNoAdmiteEstadisticasGuardaElMarcadorPeroNoLosGolesPorJugador() {
        when(equipoGestionDao.equipoAdmiteEstadisticas(PRIMER_EQUIPO)).thenReturn(false);

        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, 11L, null);

        service.enviarFinalPartido(USUARIO, PARTIDO);

        verify(estadisticaDao, never()).guardar(any());
        ArgumentCaptor<Partido> partidoGuardado = ArgumentCaptor.forClass(Partido.class);
        verify(partidoDao).actualizar(partidoGuardado.capture());
        assertThat(partidoGuardado.getValue().getGolesFavor()).isEqualTo(2);
    }

    @Test
    void elFinalNuncaSobrescribeLasEstadisticasQueYaHabia() {
        when(estadisticaDao.obtenerPorPartido(PARTIDO)).thenReturn(List.of(new PartidoEstadisticaJugador()));

        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);

        service.enviarFinalPartido(USUARIO, PARTIDO);

        verify(estadisticaDao, never()).guardar(any());
        verify(estadisticaDao, never()).eliminarPorPartido(anyLong());
        // El resultado del partido sí se guarda.
        verify(partidoDao).actualizar(any(Partido.class));
    }

    // ---- deshacer el final -------------------------------------------------------------------

    private EstadoPartidoEnVivoResponse partidoConUnGolHastaElFinal() {
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(10));
        return service.enviarFinalPartido(USUARIO, PARTIDO);
    }

    @Test
    void deshacerElFinalDevuelveElPartidoAComoEstabaYBorraLasEstadisticasQueCreo() {
        // Antes del final el partido ya tenía un resultado puesto a mano.
        partido.setGolesFavor(9);
        partido.setGolesContra(9);
        partido.setResultado("9-9");
        partido.setAvisoResultadoEnviado(false);

        EstadoPartidoEnVivoResponse fin = partidoConUnGolHastaElFinal();

        assertThat(partido.getResultado()).isEqualTo("1-0");

        reloj.avanzar(Duration.ofSeconds(90));
        EstadoPartidoEnVivoResponse estado = service.deshacer(USUARIO, PARTIDO, fin.ultimoEventoDeshacible().id());

        assertThat(estado.fase()).isEqualTo(EstadoPartidoEnVivoResponse.SEGUNDA_PARTE.equals(estado.fase())
                ? estado.fase()
                : EstadoPartidoEnVivoResponse.PRIMERA_PARTE);
        assertThat(estado.eventos()).extracting(e -> e.tipo()).doesNotContain("FINAL");

        assertThat(partido.getGolesFavor()).isEqualTo(9);
        assertThat(partido.getGolesContra()).isEqualTo(9);
        assertThat(partido.getResultado()).isEqualTo("9-9");
        verify(partidoDao).actualizarAvisoResultadoEnviado(PARTIDO, false);
        verify(estadisticaDao).eliminarPorPartido(PARTIDO);
    }

    @Test
    void deshacerElFinalDeUnPartidoSinResultadoPrevioLoDejaSinResultado() {
        EstadoPartidoEnVivoResponse fin = partidoConUnGolHastaElFinal();

        service.deshacer(USUARIO, PARTIDO, fin.ultimoEventoDeshacible().id());

        assertThat(partido.getGolesFavor()).isNull();
        assertThat(partido.getGolesContra()).isNull();
        assertThat(partido.getResultado()).isNull();
        verify(partidoDao).actualizarAvisoResultadoEnviado(PARTIDO, false);
    }

    @Test
    void deshacerElFinalNoTocaElPartidoSiElEntrenadorYaLoModifico() {
        EstadoPartidoEnVivoResponse fin = partidoConUnGolHastaElFinal();

        // El entrenador corrige el resultado desde la app después del final.
        partido.setGolesFavor(2);
        partido.setResultado("2-0");
        partido.setFechaActualizacion(Timestamp.valueOf(HOY.atTime(13, 0)));

        org.mockito.Mockito.clearInvocations(partidoDao);

        EstadoPartidoEnVivoResponse estado = service.deshacer(USUARIO, PARTIDO, fin.ultimoEventoDeshacible().id());

        // El evento sí se anula...
        assertThat(estado.eventos()).extracting(e -> e.tipo()).doesNotContain("FINAL");
        // ...pero lo del entrenador se queda como está.
        verify(partidoDao, never()).actualizar(any(Partido.class));
        verify(partidoDao, never()).actualizarAvisoResultadoEnviado(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(estadisticaDao, never()).eliminarPorPartido(anyLong());
        assertThat(partido.getResultado()).isEqualTo("2-0");
    }

    /*
     * El final del directo y su deshacer dependen de que FECHA_ACTUALIZACION del partido
     * sea la del evento FINAL (si no, "lo ha modificado otra persona"). Una importación de
     * la FFCV escribe FECHA_IMPORTACION y no la toca, así que no rompe el deshacer. Con el
     * DAO real sobre una tabla PARTIDOS de verdad.
     */
    @Test
    void unaImportacionDeLaFfcvNoRompeElFinalNiSuDeshacer() {
        jdbc.execute("DROP TABLE PARTIDOS");
        jdbc.execute("""
                CREATE TABLE PARTIDOS (
                    ID NUMBER PRIMARY KEY, EQUIPO_ID NUMBER, RIVAL VARCHAR2(50), DIA DATE, HORA VARCHAR2(20),
                    CAMPO VARCHAR2(50), RESULTADO VARCHAR2(20), TIPO VARCHAR2(20), USUARIO_ACTUALIZO_ID NUMBER,
                    FECHA_ACTUALIZACION TIMESTAMP, CANCELADO NUMBER(1) DEFAULT 0, GOLES_FAVOR NUMBER,
                    GOLES_CONTRA NUMBER, AVISO_RESULTADO_ENVIADO NUMBER(1) DEFAULT 0, ORIGEN VARCHAR2(10),
                    JORNADA NUMBER, ES_LOCAL NUMBER(1) DEFAULT 1, FECHA_IMPORTACION TIMESTAMP)""");
        jdbc.update("INSERT INTO PARTIDOS (ID, EQUIPO_ID, RIVAL, DIA, HORA, ORIGEN) VALUES (?, ?, 'Pego C.F.', ?,"
                + " '11:00', 'FFCV')", PARTIDO, PRIMER_EQUIPO, Date.valueOf(HOY));

        com.mikedev.mutxamelcf.dao.impl.PartidoDaoImpl real =
                new com.mikedev.mutxamelcf.dao.impl.PartidoDaoImpl(jdbc, reloj);

        when(partidoDao.obtenerPorId(PARTIDO)).thenAnswer(i -> real.obtenerPorId(PARTIDO));
        doAnswer(i -> {
            real.actualizar(i.getArgument(0));
            return null;
        }).when(partidoDao).actualizar(any(Partido.class));

        EstadoPartidoEnVivoResponse fin = partidoConUnGolHastaElFinal();

        assertThat(real.obtenerPorId(PARTIDO).getResultado()).isEqualTo("1-0");

        // Llega la importación semanal: datos del partido FFCV y FECHA_IMPORTACION.
        Partido delCsv = real.obtenerPorId(PARTIDO);
        delCsv.setHora("12:30");
        real.actualizarDatosFfcv(delCsv);
        real.marcarFechaImportacion(List.of(PARTIDO));

        assertThat(jdbc.queryForObject("SELECT FECHA_IMPORTACION FROM PARTIDOS WHERE ID = ?",
                java.sql.Timestamp.class, PARTIDO)).isNotNull();

        reloj.avanzar(Duration.ofSeconds(60));

        EstadoPartidoEnVivoResponse estado = service.deshacer(USUARIO, PARTIDO, fin.ultimoEventoDeshacible().id());

        // El deshacer reconoce el partido como intacto y lo devuelve a como estaba.
        assertThat(estado.eventos()).extracting(e -> e.tipo()).doesNotContain("FINAL");
        assertThat(real.obtenerPorId(PARTIDO).getResultado()).isNull();
        assertThat(real.obtenerPorId(PARTIDO).getGolesFavor()).isNull();
    }

    @Test
    void deshacerElFinalNoBorraEstadisticasQueElFinalNoCreo() {
        when(estadisticaDao.obtenerPorPartido(PARTIDO)).thenReturn(List.of(new PartidoEstadisticaJugador()));

        EstadoPartidoEnVivoResponse fin = partidoConUnGolHastaElFinal();

        service.deshacer(USUARIO, PARTIDO, fin.ultimoEventoDeshacible().id());

        verify(estadisticaDao, never()).eliminarPorPartido(anyLong());
    }

    @Test
    void deshacerElFinalFueraDeLosDosMinutosNoEstaPermitido() {
        EstadoPartidoEnVivoResponse fin = partidoConUnGolHastaElFinal();

        reloj.avanzar(Duration.ofMinutes(3));

        assertThatThrownBy(() -> service.deshacer(USUARIO, PARTIDO, fin.ultimoEventoDeshacible().id()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(partido.getResultado()).isEqualTo("1-0");
    }

    // ---- partidos disponibles -------------------------------------------------------------------

    @Test
    void losPartidosDisponiblesSonLosDeHoyDelPrimerEquipoSinCancelados() {
        Partido tarde = partido(32L, "Villena", HOY, "19:00");
        Partido cancelado = partido(33L, "Altea", HOY, "12:00");
        cancelado.setCancelado(true);
        Partido manana = partido(34L, "Elx", HOY, "09:30");
        manana.setLocal(false);

        when(partidoDao.obtenerPorEquipoYRangoFechas(PRIMER_EQUIPO, HOY, HOY))
                .thenReturn(List.of(tarde, cancelado, manana));

        assertThat(service.obtenerPartidosDisponibles(USUARIO))
                .extracting(p -> p.rival() + "@" + p.hora() + ":" + p.local())
                .containsExactly("Elx@09:30:false", "Villena@19:00:true");
    }

    @Test
    void sinPartidosHoyLaListaEstaVacia() {
        when(partidoDao.obtenerPorEquipoYRangoFechas(PRIMER_EQUIPO, HOY, HOY)).thenReturn(List.of());

        assertThat(service.obtenerPartidosDisponibles(USUARIO)).isEmpty();
    }

    @Test
    void sinEquipoPrimerEquipoLaListaEstaVacia() {
        when(equipoDao.obtenerEquipoPorNombre("Primer Equipo")).thenReturn(null);

        assertThat(service.obtenerPartidosDisponibles(USUARIO)).isEmpty();
    }

    @Test
    void elReloj_seInterpretaEnMadridNoEnUtc() {
        // 00:30 del día siguiente en Madrid es todavía el día anterior en UTC.
        Clock tardeEnMadrid = Clock.fixed(
                HOY.plusDays(1).atTime(0, 30).atZone(MADRID).toInstant(), MADRID);
        PartidoEnVivoServiceImpl otro = nuevoServicio(tardeEnMadrid);

        assertThatThrownBy(() -> otro.enviarInicioPartido(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hoy");
        assertThat(ZoneOffset.UTC).isNotNull();
    }

    @Test
    void laAlineacionSeSigueDifundiendoSinPartido() {
        service.enviarAlineacion(USUARIO, "1, 2, 3", "4, 5");

        assertThat(difusion("es", 0)).isEqualTo(new TextoDifusion("📋 Alineación del Mutxamel CF",
                "Once inicial: 1, 2, 3\n\nSuplentes: 4, 5"));
    }

    // ---- bloqueo del partido y segundos restantes ------------------------------------------

    @Test
    void registrarYDeshacerBloqueanElPartidoAntesDeLeerLosEventos() {
        empezarPartido();

        var orden = inOrder(partidoDao, eventoDao);
        orden.verify(partidoDao).bloquearPorId(PARTIDO);
        orden.verify(eventoDao).obtenerNoAnuladosPorPartido(PARTIDO);

        EstadoPartidoEnVivoResponse conGol = service.enviarGolContra(USUARIO, PARTIDO);
        service.deshacer(USUARIO, PARTIDO, conGol.ultimoEventoDeshacible().id());

        verify(partidoDao, times(3)).bloquearPorId(PARTIDO);

        // deshacer: el bloqueo va antes de leer el evento y la lista de no anulados
        var ordenDeshacer = inOrder(partidoDao, eventoDao);
        ordenDeshacer.verify(partidoDao, org.mockito.Mockito.atLeastOnce()).bloquearPorId(PARTIDO);
        ordenDeshacer.verify(eventoDao).obtenerPorId(conGol.ultimoEventoDeshacible().id());
    }

    @Test
    void consultarNoBloqueaElPartido() {
        service.obtenerEstado(USUARIO, PARTIDO);
        service.obtenerConvocados(USUARIO, PARTIDO);

        verify(partidoDao, never()).bloquearPorId(any());
    }

    @Test
    void dosFinalesSeguidosSoloDejanUnoYElSegundoSeRechaza() {
        empezarPartido();

        service.enviarFinalPartido(USUARIO, PARTIDO);

        assertThatThrownBy(() -> service.enviarFinalPartido(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminado");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM PARTIDO_LIVE_EVENTOS WHERE TIPO = 'FINAL' AND ANULADO = 0", Integer.class))
                .isEqualTo(1);
    }

    /*
     * Dos peticiones a la vez con transacciones reales: la primera se queda
     * "pensando" justo después de leer los eventos. Sin el bloqueo de la fila
     * del partido, la segunda lee los mismos eventos (sin FINAL) y crea otro.
     */
    @Test
    void dosFinalesSimultaneosEnTransaccionesDistintasSoloDejanUno() throws Exception {
        jdbc.execute("DROP TABLE PARTIDOS");
        jdbc.execute("CREATE TABLE PARTIDOS (ID NUMBER PRIMARY KEY)");
        jdbc.update("INSERT INTO PARTIDOS VALUES (?)", PARTIDO);

        com.mikedev.mutxamelcf.dao.impl.PartidoDaoImpl partidoReal =
                new com.mikedev.mutxamelcf.dao.impl.PartidoDaoImpl(jdbc);
        doAnswer(invocacion -> {
            partidoReal.bloquearPorId(invocacion.getArgument(0));
            return null;
        }).when(partidoDao).bloquearPorId(any());

        empezarPartido();

        var primeraLeyendo = new java.util.concurrent.CountDownLatch(1);
        var primera = new java.util.concurrent.atomic.AtomicBoolean(true);

        doAnswer(invocacion -> {
            Object eventos = invocacion.callRealMethod();
            if (primera.getAndSet(false)) {
                primeraLeyendo.countDown();
                Thread.sleep(500);
            }
            return eventos;
        }).when(eventoDao).obtenerNoAnuladosPorPartido(PARTIDO);

        var transacciones = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));

        var hilos = java.util.concurrent.Executors.newFixedThreadPool(2);

        try {
            var uno = hilos.submit(() -> transacciones.execute(
                    estado -> service.enviarFinalPartido(USUARIO, PARTIDO)));

            assertThat(primeraLeyendo.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            var dos = hilos.submit(() -> transacciones.execute(
                    estado -> service.enviarFinalPartido(USUARIO, PARTIDO)));

            int correctos = 0;
            int rechazados = 0;

            for (var resultado : List.of(uno, dos)) {
                try {
                    resultado.get(10, java.util.concurrent.TimeUnit.SECONDS);
                    correctos++;
                } catch (java.util.concurrent.ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
                    rechazados++;
                }
            }

            assertThat(correctos).isEqualTo(1);
            assertThat(rechazados).isEqualTo(1);

        } finally {
            hilos.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM PARTIDO_LIVE_EVENTOS WHERE TIPO = 'FINAL' AND ANULADO = 0", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void elEstadoTraeLosSegundosRestantesParaDeshacerCalculadosEnElServidor() {
        empezarPartido();
        EstadoPartidoEnVivoResponse conGol = service.enviarGolContra(USUARIO, PARTIDO);

        assertThat(conGol.ultimoEventoDeshacible().segundosRestantesDeshacer()).isEqualTo(120);

        reloj.avanzar(Duration.ofSeconds(45).plusMillis(400));

        // 74,6 s -> se redondea hacia arriba: nunca se enseña 0 con el botón aún activo.
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).ultimoEventoDeshacible().segundosRestantesDeshacer())
                .isEqualTo(75);

        reloj.avanzar(Duration.ofSeconds(74).plusMillis(600));

        assertThat(service.obtenerEstado(USUARIO, PARTIDO).ultimoEventoDeshacible().segundosRestantesDeshacer())
                .isZero();

        reloj.avanzar(Duration.ofSeconds(1));

        assertThat(service.obtenerEstado(USUARIO, PARTIDO).ultimoEventoDeshacible()).isNull();
    }


    // ---- minuto del gol -----------------------------------------------------------------

    private static Integer minutoDelUltimoGol(EstadoPartidoEnVivoResponse estado) {
        List<EstadoPartidoEnVivoResponse.EventoEnVivo> eventos = estado.eventos();
        return eventos.get(eventos.size() - 1).minuto();
    }

    private void equipoConModalidad(String modalidad) {
        Equipo equipo = new Equipo();
        equipo.setId(PRIMER_EQUIPO);
        equipo.setModalidad(modalidad);
        when(equipoDao.obtenerEquipoPorId(PRIMER_EQUIPO)).thenReturn(equipo);
    }

    @Test
    void sinMinutoEnLaPrimeraParteSeCalculaDesdeElInicioMasUno() {
        empezarPartido();

        reloj.avanzar(Duration.ofMinutes(22).plusSeconds(30));
        EstadoPartidoEnVivoResponse estado = service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);

        assertThat(minutoDelUltimoGol(estado)).isEqualTo(23);
    }

    @Test
    void justoAlEmpezarElMinutoEsElUno() {
        empezarPartido();

        assertThat(minutoDelUltimoGol(service.enviarGolContra(USUARIO, PARTIDO))).isEqualTo(1);
    }

    @Test
    void enLaSegundaParteSeSumanLos45MinutosDeLaPrimera() {
        empezarPartido();
        reloj.avanzar(Duration.ofMinutes(47));
        service.enviarDescanso(USUARIO, PARTIDO);
        reloj.avanzar(Duration.ofMinutes(15));
        service.enviarSegundaParte(USUARIO, PARTIDO);

        reloj.avanzar(Duration.ofMinutes(10).plusSeconds(5));

        assertThat(minutoDelUltimoGol(service.enviarGolFavor(USUARIO, PARTIDO, 10L, null))).isEqualTo(56);
    }

    @Test
    void enF8LaParteDura25MinutosYElMinutoSeCalculaConEsaDuracion() {
        equipoConModalidad("F8");

        empezarPartido();
        reloj.avanzar(Duration.ofMinutes(26));
        service.enviarDescanso(USUARIO, PARTIDO);
        service.enviarSegundaParte(USUARIO, PARTIDO);
        reloj.avanzar(Duration.ofMinutes(3));

        assertThat(minutoDelUltimoGol(service.enviarGolContra(USUARIO, PARTIDO))).isEqualTo(25 + 3 + 1);
    }

    @Test
    void siPasaDeLaDuracionDeLaParteSeGuardaTalCualSinRecortar() {
        empezarPartido();
        reloj.avanzar(Duration.ofMinutes(50));

        assertThat(minutoDelUltimoGol(service.enviarGolContra(USUARIO, PARTIDO))).isEqualTo(51);
    }

    @Test
    void unMinutoManualTienePrioridadSobreElCalculado() {
        empezarPartido();
        reloj.avanzar(Duration.ofMinutes(30));

        assertThat(minutoDelUltimoGol(service.enviarGolFavor(USUARIO, PARTIDO, 10L, null, 7))).isEqualTo(7);
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(minutoDelUltimoGol(service.enviarGolContra(USUARIO, PARTIDO, 0))).isEqualTo(0);
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(minutoDelUltimoGol(service.enviarGolContra(USUARIO, PARTIDO, 150))).isEqualTo(150);
    }

    @Test
    void unMinutoManualFueraDeRangoEsError() {
        empezarPartido();

        assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, 10L, null, 151))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enviarGolContra(USUARIO, PARTIDO, -1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.obtenerEstado(USUARIO, PARTIDO).golesFavor()).isZero();
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).golesContra()).isZero();
    }

    @Test
    void elEstadoDaElMinutoActualSoloMientrasSeJuega() {
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).minutoActual()).isNull();

        empezarPartido();
        reloj.avanzar(Duration.ofMinutes(9));
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).minutoActual()).isEqualTo(10);

        service.enviarDescanso(USUARIO, PARTIDO);
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).minutoActual()).isNull();

        service.enviarSegundaParte(USUARIO, PARTIDO);
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).minutoActual()).isEqualTo(46);

        service.enviarFinalPartido(USUARIO, PARTIDO);
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).minutoActual()).isNull();
    }

    @Test
    void elPushDelGolIncluyeElMinuto() {
        empezarPartido();
        reloj.avanzar(Duration.ofMinutes(22));

        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);

        assertThat(difusion("es", -1).titulo()).isEqualTo("⚽ ¡GOOOL del Mutxamel CF!");
        assertThat(difusion("es", -1).mensaje()).isEqualTo("Pablo Garcia (23')\n\nMutxamel CF 1 - 0 Pego C.F.");
        assertThat(difusion("en", -1).mensaje()).isEqualTo("Pablo Garcia (23')\n\nMutxamel CF 1 - 0 Pego C.F.");
    }

    @Test
    void elPushDelGolEnContraIncluyeElMinutoEnElCuerpo() {
        empezarPartido();
        reloj.avanzar(Duration.ofMinutes(9));

        service.enviarGolContra(USUARIO, PARTIDO);

        assertThat(difusion("es", -1)).isEqualTo(
                new TextoDifusion("Gol del rival", "Minuto 10'\n\nMutxamel CF 0 - 1 Pego C.F."));
        assertThat(difusion("ca", -1).titulo()).isEqualTo("Gol del rival");
        assertThat(difusion("en", -1).titulo()).isEqualTo("Opponent goal");
    }

    // ---- corregir el minuto -------------------------------------------------------------

    @Test
    void corregirElMinutoDeCualquierGolSinPushNiLimiteDeTiempo() {
        empezarPartido();
        EstadoPartidoEnVivoResponse primero = service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        Long idPrimero = primero.ultimoEventoDeshacible().id();
        reloj.avanzar(Duration.ofMinutes(20));
        service.enviarGolContra(USUARIO, PARTIDO);
        org.mockito.Mockito.clearInvocations(notificacionAppService, fcmPushService);

        // No es el último gol y han pasado más de 2 minutos.
        EstadoPartidoEnVivoResponse estado = service.corregirMinuto(USUARIO, PARTIDO, idPrimero, 12);

        assertThat(estado.eventos()).filteredOn(e -> e.id().equals(idPrimero)).singleElement()
                .satisfies(e -> assertThat(e.minuto()).isEqualTo(12));
        verify(notificacionAppService, never()).difundirATodos(any(), any(), any());
        verify(fcmPushService, never()).enviarATopic(any(), any(), any());
    }

    @Test
    void corregirElMinutoFueraDeRangoOSinMinutoEsError() {
        empezarPartido();
        Long id = service.enviarGolFavor(USUARIO, PARTIDO, 10L, null).ultimoEventoDeshacible().id();

        assertThatThrownBy(() -> service.corregirMinuto(USUARIO, PARTIDO, id, 151))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.corregirMinuto(USUARIO, PARTIDO, id, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.corregirMinuto(USUARIO, PARTIDO, id, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(minutoDelUltimoGol(service.obtenerEstado(USUARIO, PARTIDO))).isEqualTo(1);
    }

    @Test
    void noSePuedeCorregirElMinutoDeUnGolAnulado() {
        empezarPartido();
        Long id = service.enviarGolFavor(USUARIO, PARTIDO, 10L, null).ultimoEventoDeshacible().id();
        service.deshacer(USUARIO, PARTIDO, id);

        assertThatThrownBy(() -> service.corregirMinuto(USUARIO, PARTIDO, id, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anulado");
    }

    @Test
    void soloSeCorrigeElMinutoDeUnGol() {
        EstadoPartidoEnVivoResponse estado = empezarPartido();
        Long idInicio = estado.eventos().get(0).id();

        assertThatThrownBy(() -> service.corregirMinuto(USUARIO, PARTIDO, idInicio, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.corregirMinuto(USUARIO, PARTIDO, 9999L, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void corregirElMinutoExigeElRolYQueElPartidoSeaDeHoy() {
        empezarPartido();
        Long id = service.enviarGolFavor(USUARIO, PARTIDO, 10L, null).ultimoEventoDeshacible().id();

        when(usuarioAppService.tieneRol(USUARIO.intValue(), "RETRANSMISION")).thenReturn(false);
        assertThatThrownBy(() -> service.corregirMinuto(USUARIO, PARTIDO, id, 5))
                .isInstanceOf(SecurityException.class);

        when(usuarioAppService.tieneRol(USUARIO.intValue(), "RETRANSMISION")).thenReturn(true);
        reloj.avanzar(Duration.ofDays(1));
        assertThatThrownBy(() -> service.corregirMinuto(USUARIO, PARTIDO, id, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hoy");
    }

    // ---- nombre de retransmisión ----------------------------------------------------------

    private void nombreDeRetransmision(long jugadorId, String nombre) {
        jdbc.update("UPDATE JUGADORES SET NOMBRE_RETRANSMISION = ? WHERE ID = ?", nombre, jugadorId);
    }

    @Test
    void elPushDelGolUsaElNombreDeRetransmisionSiLoTieneYSiNoNombreYApellidos() {
        nombreDeRetransmision(10L, "Pablito");
        empezarPartido();

        EstadoPartidoEnVivoResponse estado = service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, 11L, null);

        assertThat(difusion("es", 1).mensaje()).isEqualTo("Pablito (1')\n\nMutxamel CF 1 - 0 Pego C.F.");
        assertThat(difusion("es", 2).mensaje()).startsWith("Luis Lopez (2')");
        verify(fcmPushService).enviarATopic("resultados_ca", "⚽ GOOOL del Mutxamel CF!",
                "Pablito (1')\n\nMutxamel CF 1 - 0 Pego C.F.");
        // Quien retransmite sigue viendo nombre y apellidos para reconocer al jugador.
        assertThat(estado.goleadores()).containsExactly("Pablo Garcia");
        assertThat(estado.eventos().get(1).autor()).isEqualTo("Pablo Garcia");
    }

    @Test
    void unNombreDeRetransmisionEnBlancoCuentaComoSinNombre() {
        nombreDeRetransmision(10L, "   ");
        empezarPartido();

        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);

        assertThat(difusion("es", 1).mensaje()).startsWith("Pablo Garcia (1')");
    }

    @Test
    void losGoleadoresDelFinalUsanElNombreDeRetransmisionYElAutorLibreNoCambia() {
        nombreDeRetransmision(10L, "Pablito");
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, 11L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, null, "p.p.");
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, null, "Otro");

        service.enviarFinalPartido(USUARIO, PARTIDO);

        assertThat(difusion("es", -1).mensaje()).isEqualTo("Mutxamel CF 4 - 0 Pego C.F.\n\nGoleadores: Pablito, "
                + "Luis Lopez, Gol en propia puerta del rival, Otro");
    }

    @Test
    void losConvocadosDevuelvenElNombreCompletoYElDeRetransmision() {
        nombreDeRetransmision(10L, "Pablito");

        assertThat(service.obtenerConvocados(USUARIO, PARTIDO))
                .extracting(c -> c.jugadorId() + ":" + c.nombreCompleto() + ":" + c.nombreRetransmision()
                        + ":" + c.nombre())
                .containsExactly(
                        "11:Luis Lopez:Luis Lopez:Luis Lopez",
                        "10:Pablo Garcia:Pablito:Pablo Garcia",
                        "12:Sin Dorsal Perez:Sin Dorsal Perez:Sin Dorsal Perez");
    }

    // ---- alineación eligiendo jugadores ------------------------------------------------------

    @Test
    void laAlineacionSaleConLosNombresDeRetransmisionEnElOrdenElegido() {
        nombreDeRetransmision(10L, "Pablito");

        service.enviarAlineacion(USUARIO, PARTIDO, List.of(12L, 10L), List.of(11L));

        assertThat(difusion("es", 0)).isEqualTo(new TextoDifusion("📋 Alineación del Mutxamel CF",
                "Once inicial: Sin Dorsal Perez, Pablito\n\nSuplentes: Luis Lopez"));
        assertThat(difusion("ca", 0).mensaje()).isEqualTo(
                "Onze inicial: Sin Dorsal Perez, Pablito\n\nSuplents: Luis Lopez");
        assertThat(difusion("en", 0).mensaje()).isEqualTo(
                "Starting eleven: Sin Dorsal Perez, Pablito\n\nSubstitutes: Luis Lopez");
        verify(fcmPushService).enviarATopic("resultados", "📋 Alineación del Mutxamel CF",
                "Once inicial: Sin Dorsal Perez, Pablito\n\nSuplentes: Luis Lopez");
    }

    @Test
    void laAlineacionSinNombreDeRetransmisionUsaNombreYApellidos() {
        service.enviarAlineacion(USUARIO, PARTIDO, List.of(11L, 10L), List.of(12L));

        assertThat(difusion("es", 0).mensaje())
                .isEqualTo("Once inicial: Luis Lopez, Pablo Garcia\n\nSuplentes: Sin Dorsal Perez");
    }

    @Test
    void laAlineacionSinSuplentesUsaElTextoSinSuplentes() {
        service.enviarAlineacion(USUARIO, PARTIDO, List.of(10L, 11L), List.of());
        service.enviarAlineacion(USUARIO, PARTIDO, List.of(10L, 11L), null);

        assertThat(difusion("es", 0).mensaje()).isEqualTo("Once inicial: Pablo Garcia, Luis Lopez");
        assertThat(difusion("ca", 0).mensaje()).isEqualTo("Onze inicial: Pablo Garcia, Luis Lopez");
        assertThat(difusion("en", 0).mensaje()).isEqualTo("Starting eleven: Pablo Garcia, Luis Lopez");
        assertThat(difusion("es", 1).mensaje()).isEqualTo("Once inicial: Pablo Garcia, Luis Lopez");
    }

    private void rechazada(String mensaje, List<Long> titulares, List<Long> suplentes) {
        assertThatThrownBy(() -> service.enviarAlineacion(USUARIO, PARTIDO, titulares, suplentes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(mensaje);
    }

    @Test
    void laAlineacionRechazaTitularesVaciosRepetidosYJugadoresQueNoSonDelPartido() {
        rechazada("al menos un titular", List.of(), List.of(10L));
        rechazada("al menos un titular", null, null);
        rechazada("dos veces", List.of(10L, 11L, 10L), List.of());
        rechazada("dos veces", List.of(10L, 11L), List.of(11L));
        rechazada("dos veces", List.of(10L), List.of(12L, 12L));
        rechazada("no válido", java.util.Arrays.asList(10L, null), List.of());
        // 13 existe pero no está convocado; 999 no existe.
        rechazada("no está entre los jugadores", List.of(10L, 13L), List.of());
        rechazada("no está entre los jugadores", List.of(10L), List.of(999L));

        verify(notificacionAppService, never()).difundirATodos(any(), any(), any());
        verify(fcmPushService, never()).enviarATopic(any(), any(), any());
    }

    @Test
    void laAlineacionExigeElRolYUnPartidoDelPrimerEquipoDeHoy() {
        assertThatThrownBy(() -> service.enviarAlineacion(USUARIO, 999L, List.of(10L), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");

        when(usuarioAppService.tieneRol(USUARIO.intValue(), "RETRANSMISION")).thenReturn(false);

        assertThatThrownBy(() -> service.enviarAlineacion(USUARIO, PARTIDO, List.of(10L), List.of()))
                .isInstanceOf(SecurityException.class);
    }

    private void plantillaDeDoce() {
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(null);
        List<Long> ids = new java.util.ArrayList<>();
        for (long id = 100; id < 112; id++) {
            jdbc.update("INSERT INTO JUGADORES (ID, DORSAL, NOMBRE, APELLIDOS) VALUES (?, ?, 'Jugador', ?)",
                    id, id - 99, "N" + id);
            ids.add(id);
        }
        when(equipoGestionDao.obtenerJugadoresPorEquipo(PRIMER_EQUIPO)).thenReturn(ids);
    }

    private static List<Long> desde(long primero, int cuantos) {
        List<Long> ids = new java.util.ArrayList<>();
        for (long id = primero; id < primero + cuantos; id++) {
            ids.add(id);
        }
        return ids;
    }

    @Test
    void laAlineacionNoAdmiteMasDeOnceTitularesEnF11() {
        plantillaDeDoce();

        service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 11), List.of(111L));
        rechazada("máximo 11", desde(100, 12), List.of());
    }

    @Test
    void laAlineacionAplicaElMaximoDeTitularesDeLaModalidad() {
        plantillaDeDoce();

        equipoConModalidad("F8");
        service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 8), List.of());
        rechazada("máximo 8", desde(100, 9), List.of());

        equipoConModalidad("F5");
        service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 5), List.of());
        rechazada("máximo 5", desde(100, 6), List.of());
    }

    // ---- lista elegible --------------------------------------------------------------------

    private ConvocatoriaJugador invitado(long id, long jugadorId, EstadoInvitacion estado) {
        ConvocatoriaJugador invitado = new ConvocatoriaJugador(id, 200L, jugadorId);
        invitado.setEquipoOrigenId(5L);
        invitado.setEstadoInvitacion(estado);
        return invitado;
    }

    private JustificacionFaltaPartido justificacion(long jugadorId) {
        JustificacionFaltaPartido justificacion = new JustificacionFaltaPartido();
        justificacion.setPartidoId(PARTIDO);
        justificacion.setJugadorId(jugadorId);
        return justificacion;
    }

    @Test
    void laListaElegibleSoloTieneLosConvocadosEfectivos() {
        for (long id = 20; id <= 24; id++) {
            jdbc.update("INSERT INTO JUGADORES (ID, DORSAL, NOMBRE, APELLIDOS) VALUES (?, ?, ?, 'Invitado')",
                    id, id, "J" + id);
        }
        when(convocatoriaJugadorDao.obtenerPorConvocatoria(200L)).thenReturn(List.of(
                new ConvocatoriaJugador(1L, 200L, 10L),
                new ConvocatoriaJugador(2L, 200L, 11L),
                invitado(3L, 20L, EstadoInvitacion.AUTORIZADO),
                invitado(4L, 21L, EstadoInvitacion.PENDIENTE_COORDINACION),
                invitado(5L, 22L, EstadoInvitacion.PENDIENTE_ENTRENADOR),
                invitado(6L, 23L, EstadoInvitacion.RECHAZADO),
                invitado(7L, 24L, EstadoInvitacion.AUTORIZADO)));
        // El 11 (del equipo) tiene ausencia justificada y el 24 (invitado autorizado) lo ha desestimado la familia.
        when(justificacionDao.obtenerPorPartido(PARTIDO)).thenReturn(List.of(justificacion(11L), justificacion(24L)));

        assertThat(service.obtenerConvocados(USUARIO, PARTIDO))
                .extracting(c -> c.jugadorId() + ":" + c.origen())
                .containsExactly("10:CONVOCATORIA", "20:CONVOCATORIA");

        // Los que no juegan tampoco pueden marcar ni estar en la alineación.
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 20L, null);
        for (long fuera : new long[] { 11L, 21L, 22L, 23L, 24L }) {
            assertThatThrownBy(() -> service.enviarGolFavor(USUARIO, PARTIDO, fuera, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("convocados");
            rechazada("no está entre los jugadores", List.of(10L, fuera), List.of());
        }
        service.enviarAlineacion(USUARIO, PARTIDO, List.of(20L, 10L), List.of());
    }

    @Test
    void sinConvocatoriaLaListaEsLaPlantillaActivaYElOrigenLoDice() {
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(null);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(PRIMER_EQUIPO)).thenReturn(List.of(10L, 13L));

        assertThat(service.obtenerConvocados(USUARIO, PARTIDO))
                .extracting(c -> c.jugadorId() + ":" + c.origen())
                .containsExactly("13:PLANTILLA", "10:PLANTILLA");
        verify(justificacionDao, never()).obtenerPorPartido(any());
    }

    // ---- portada con eventos reales y deshacer ------------------------------------------------

    @Test
    void laPortadaCalculadaConEventosRealesSeAjustaAlDeshacerElFinal() {
        nombreDeRetransmision(10L, "Pablito");
        PartidoService portada = new PartidoServiceImpl(
                partidoDao, equipoGestionDao, equipoDao, convocatoriaDao, convocatoriaJugadorDao,
                mock(DescansoEquipoDao.class), mock(JustificacionFaltaPartidoDao.class), mock(TemporadaDao.class),
                mock(InvitadosService.class), eventoDao, AvisosTest.TEXTOS, reloj);
        when(partidoDao.obtenerMasRelevantePorEquipoNombre("Primer Equipo", "Primer Equipo")).thenReturn(partido);

        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolContra(USUARIO, PARTIDO);

        ResultadoDTO enJuego = portada.obtenerResultadoPrimerEquipo("es");
        assertThat(enJuego.isEnJuego()).isTrue();
        assertThat(enJuego.getFase()).isEqualTo("PRIMERA_PARTE");
        assertThat(enJuego.getGolesFavor()).isEqualTo(2);
        assertThat(enJuego.getGolesContra()).isEqualTo(1);
        assertThat(enJuego.getGoleadores()).isEmpty();

        EstadoPartidoEnVivoResponse fin = service.enviarFinalPartido(USUARIO, PARTIDO);

        ResultadoDTO finalizado = portada.obtenerResultadoPrimerEquipo("es");
        assertThat(finalizado.isEnJuego()).isFalse();
        assertThat(finalizado.getFase()).isEqualTo("FINALIZADO");
        assertThat(finalizado.getGoleadores()).containsExactly("Pablito (2)");

        service.deshacer(USUARIO, PARTIDO, fin.eventos().get(fin.eventos().size() - 1).id());

        ResultadoDTO deshecho = portada.obtenerResultadoPrimerEquipo("es");
        assertThat(deshecho.isEnJuego()).isTrue();
        assertThat(deshecho.getFase()).isEqualTo("PRIMERA_PARTE");
        assertThat(deshecho.getGolesFavor()).isEqualTo(2);
        assertThat(deshecho.getGoleadores()).isEmpty();
    }

    // ---- ficha del partido: formación, cambios y tarjetas ----------------------------------------

    private int pushsHechos() {
        return todasLasDifusiones("es").size();
    }

    private EstadoPartidoEnVivoResponse conAlineacionDeOnce(String formacion) {
        plantillaDeDoce();
        return service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 11), List.of(111L), formacion, false);
    }

    /** Titulares 100-108 (nueve) y suplentes 109, 110 y 111, sin formación. */
    private void alineacionConTresSuplentes() {
        plantillaDeDoce();
        service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 9), List.of(109L, 110L, 111L), null, false);
    }

    private static void rechazado(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String mensaje) {
        assertThatThrownBy(accion).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(mensaje);
    }

    @Test
    void laAlineacionConFormacionGuardaTitularesSuplentesOrdenYFormacion() {
        EstadoPartidoEnVivoResponse estado = conAlineacionDeOnce("4-4-2");

        assertThat(estado.alineacion().formacion()).isEqualTo("4-4-2");
        assertThat(estado.alineacion().titulares()).isEqualTo(desde(100, 11));
        assertThat(estado.alineacion().suplentes()).containsExactly(111L);
        assertThat(estado.alineacion().enElCampo()).containsExactlyInAnyOrderElementsOf(desde(100, 11));
        assertThat(estado.alineacion().suplentesDisponibles()).containsExactly(111L);

        assertThat(alineacionDao.obtenerFormacion(PARTIDO)).isEqualTo("4-4-2");
        assertThat(alineacionDao.obtener(PARTIDO)).extracting(a -> a.jugadorId() + ":" + a.titular() + ":" + a.orden())
                .startsWith("100:true:1", "101:true:2").contains("110:true:11", "111:false:1");

        // GET del estado (y de los eventos siguientes) devuelve la alineación guardada.
        assertThat(service.obtenerEstado(USUARIO, PARTIDO).alineacion().formacion()).isEqualTo("4-4-2");
    }

    @Test
    void elNumeroDeTitularesTieneQueCuadrarConLaFormacionYLaFormacionSerDeLaLista() {
        plantillaDeDoce();

        rechazado(() -> service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 10), List.of(), "4-4-2", false),
                "necesita 11 titulares");
        rechazado(() -> service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 11), List.of(), "9-1-1", false),
                "no está permitida");

        for (String formacion : List.of("4-3-3", "4-2-3-1", "4-5-1", "4-1-4-1", "3-5-2", "3-4-3", "5-3-2", "5-4-1")) {
            service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 11), List.of(), formacion, false);
        }

        // Las de F11 no valen en F8, que tiene las suyas.
        equipoConModalidad("F8");
        rechazado(() -> service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 8), List.of(), "4-4-2", false),
                "no está permitida");
        service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 8), List.of(), "3-3-1", false);

        // Sin formación vale cualquier número de titulares (hasta el máximo) y se guarda sin formación.
        service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 7), List.of(), null, false);
        assertThat(alineacionDao.obtenerFormacion(PARTIDO)).isNull();
    }

    @Test
    void laPosicionEnElCampoSigueElOrdenDeEleccion() {
        // Portero, y después las líneas de atrás adelante, cada una de izquierda a derecha.
        assertThat(Formaciones.posicion("4-2-3-1", 1)).containsExactly(1, 1);
        assertThat(Formaciones.posicion("4-2-3-1", 2)).containsExactly(2, 1);
        assertThat(Formaciones.posicion("4-2-3-1", 5)).containsExactly(2, 4);
        assertThat(Formaciones.posicion("4-2-3-1", 6)).containsExactly(3, 1);
        assertThat(Formaciones.posicion("4-2-3-1", 7)).containsExactly(3, 2);
        assertThat(Formaciones.posicion("4-2-3-1", 8)).containsExactly(4, 1);
        assertThat(Formaciones.posicion("4-2-3-1", 10)).containsExactly(4, 3);
        assertThat(Formaciones.posicion("4-2-3-1", 11)).containsExactly(5, 1);
        assertThat(Formaciones.titulares("4-2-3-1")).isEqualTo(11);
        assertThat(Formaciones.titulares("3-3-1")).isEqualTo(8);
    }

    @Test
    void reenviarLaAlineacionLaReemplazaYSoloAvisaConAvisarTrue() {
        plantillaDeDoce();

        // Primera vez, avisando: un push.
        service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 11), List.of(111L), "4-4-2", true);
        assertThat(pushsHechos()).isEqualTo(1);

        // Reenvío sin avisar (el valor que manda la app por defecto después): se reemplaza sin push.
        List<Long> otros = new java.util.ArrayList<>(desde(101, 10));
        otros.add(0, 111L);
        service.enviarAlineacion(USUARIO, PARTIDO, otros, List.of(100L), "4-3-3", false);
        assertThat(pushsHechos()).isEqualTo(1);

        assertThat(alineacionDao.obtenerFormacion(PARTIDO)).isEqualTo("4-3-3");
        assertThat(alineacionDao.obtener(PARTIDO)).hasSize(12);
        assertThat(alineacionDao.obtener(PARTIDO).get(0).jugadorId()).isEqualTo(111L);
        assertThat(alineacionDao.obtener(PARTIDO)).filteredOn(a -> !a.titular()).extracting(a -> a.jugadorId())
                .containsExactly(100L);

        // Reenvío avisando otra vez: segundo push.
        service.enviarAlineacion(USUARIO, PARTIDO, otros, List.of(100L), "4-3-3", true);
        assertThat(pushsHechos()).isEqualTo(2);
    }

    @Test
    void laAlineacionAntiguaDeCuatroArgumentosSigueAvisando() {
        plantillaDeDoce();

        service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 11), List.of(111L));

        assertThat(pushsHechos()).isEqualTo(1);
        assertThat(alineacionDao.obtenerFormacion(PARTIDO)).isNull();
    }

    @Test
    void conCambiosRegistradosNoSePuedeCambiarLaAlineacion() {
        alineacionConTresSuplentes();
        empezarPartido();
        service.enviarCambio(USUARIO, PARTIDO, 100L, 109L, 30);

        rechazado(() -> service.enviarAlineacion(USUARIO, PARTIDO, desde(100, 9), List.of(109L), null, false),
                "ya hay cambios");
    }

    @Test
    void unCambioValidoMueveLosJugadoresDelCampoYNoAvisaPorPush() {
        alineacionConTresSuplentes();
        empezarPartido();
        int pushs = pushsHechos();

        EstadoPartidoEnVivoResponse estado = service.enviarCambio(USUARIO, PARTIDO, 100L, 109L, 60);

        assertThat(pushsHechos()).isEqualTo(pushs);
        assertThat(estado.alineacion().enElCampo()).contains(109L).doesNotContain(100L).hasSize(9);
        assertThat(estado.alineacion().suplentesDisponibles()).containsExactly(110L, 111L);

        EstadoPartidoEnVivoResponse.EventoEnVivo cambio = estado.eventos().get(estado.eventos().size() - 1);
        assertThat(cambio.tipo()).isEqualTo("CAMBIO");
        assertThat(cambio.jugadorId()).isEqualTo(100L);
        assertThat(cambio.jugadorEntraId()).isEqualTo(109L);
        assertThat(cambio.autor()).isEqualTo("Jugador N100");
        assertThat(cambio.jugadorEntra()).isEqualTo("Jugador N109");
        assertThat(cambio.minuto()).isEqualTo(60);

        // El que entró puede salir después.
        service.enviarCambio(USUARIO, PARTIDO, 109L, 110L, 70);
    }

    @Test
    void losCambiosConAlineacionSoloAdmitenAQuienEstaEnElCampoYUnSuplenteQueNoHaEntrado() {
        alineacionConTresSuplentes();
        empezarPartido();

        // Sale uno del banquillo / entra un titular / el mismo / nulos
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 109L, 110L, 30), "no está en el campo");
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 100L, 101L, 30), "suplente que no haya entrado");
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 100L, 100L, 30), "distintos");
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, null, 109L, 30), "quién sale");
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 100L, 109L, 151), "entre 0 y 150");

        service.enviarCambio(USUARIO, PARTIDO, 100L, 109L, 30);

        // El sustituido ya no está en el campo y el suplente que entró ya no es suplente disponible.
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 100L, 110L, 31), "no está en el campo");
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 101L, 109L, 31), "suplente que no haya entrado");
        // Y uno expulsado tampoco puede salir.
        service.enviarTarjeta(USUARIO, PARTIDO, 102L, "ROJA", 40);
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 102L, 110L, 41), "expulsado");
    }

    @Test
    void sinAlineacionElCambioAdmiteDosJugadoresDistintosDeLaListaElegible() {
        empezarPartido();

        service.enviarCambio(USUARIO, PARTIDO, 10L, 11L, 50);

        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 10L, 13L, 51), "no está entre los jugadores");
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 13L, 10L, 51), "no está entre los jugadores");
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 10L, 10L, 51), "distintos");
    }

    @Test
    void elCambioValeEnPrimeraSegundaParteYDescansoPeroNoAntesNiDespues() {
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 10L, 11L, 5), "todavía no ha empezado");

        empezarPartido();
        service.enviarCambio(USUARIO, PARTIDO, 10L, 11L, 20);

        service.enviarDescanso(USUARIO, PARTIDO);
        // Con el minuto del descanso; sin minuto, el de la duración de la parte.
        EstadoPartidoEnVivoResponse enDescanso = service.enviarCambio(USUARIO, PARTIDO, 12L, 10L, 45);
        assertThat(enDescanso.eventos().get(enDescanso.eventos().size() - 1).minuto()).isEqualTo(45);
        EstadoPartidoEnVivoResponse sinMinuto = service.enviarCambio(USUARIO, PARTIDO, 11L, 12L, null);
        assertThat(sinMinuto.eventos().get(sinMinuto.eventos().size() - 1).minuto()).isEqualTo(45);

        service.enviarSegundaParte(USUARIO, PARTIDO);
        service.enviarCambio(USUARIO, PARTIDO, 10L, 11L, 60);

        service.enviarFinalPartido(USUARIO, PARTIDO);
        rechazado(() -> service.enviarCambio(USUARIO, PARTIDO, 12L, 10L, 80), "terminado");
    }

    @Test
    void unCambioReintentadoAlPocoNoSeRegistraDosVeces() {
        empezarPartido();

        service.enviarCambio(USUARIO, PARTIDO, 10L, 11L, 50);
        EstadoPartidoEnVivoResponse estado = service.enviarCambio(USUARIO, PARTIDO, 10L, 11L, 50);

        assertThat(estado.eventos()).filteredOn(e -> e.tipo().equals("CAMBIO")).hasSize(1);
    }

    @Test
    void lasTarjetasSeRegistranConSuMinutoSinPushYLaSegundaAmarillaEsExpulsion() {
        empezarPartido();
        int pushs = pushsHechos();

        EstadoPartidoEnVivoResponse primera = service.enviarTarjeta(USUARIO, PARTIDO, 10L, "amarilla", 20);
        EstadoPartidoEnVivoResponse.EventoEnVivo amarilla = primera.eventos().get(primera.eventos().size() - 1);
        assertThat(amarilla.tipo()).isEqualTo("TARJETA_AMARILLA");
        assertThat(amarilla.autor()).isEqualTo("Pablo Garcia");
        assertThat(amarilla.minuto()).isEqualTo(20);
        assertThat(amarilla.dobleAmarilla()).isFalse();

        reloj.avanzar(Duration.ofMinutes(5));
        EstadoPartidoEnVivoResponse segunda = service.enviarTarjeta(USUARIO, PARTIDO, 10L, "AMARILLA", 70);
        EstadoPartidoEnVivoResponse.EventoEnVivo doble = segunda.eventos().get(segunda.eventos().size() - 1);
        assertThat(doble.tipo()).isEqualTo("TARJETA_AMARILLA");
        assertThat(doble.dobleAmarilla()).isTrue();

        // Expulsado: no admite más tarjetas.
        rechazado(() -> service.enviarTarjeta(USUARIO, PARTIDO, 10L, "ROJA", 71), "ya ha sido expulsado");

        // Roja directa a otro.
        EstadoPartidoEnVivoResponse roja = service.enviarTarjeta(USUARIO, PARTIDO, 11L, "ROJA", 75);
        assertThat(roja.eventos().get(roja.eventos().size() - 1).tipo()).isEqualTo("TARJETA_ROJA");
        assertThat(roja.eventos().get(roja.eventos().size() - 1).dobleAmarilla()).isFalse();

        assertThat(pushsHechos()).isEqualTo(pushs);
    }

    @Test
    void lasTarjetasValidanElJugadorElColorYElMomento() {
        rechazado(() -> service.enviarTarjeta(USUARIO, PARTIDO, 10L, "AMARILLA", 5), "todavía no ha empezado");

        empezarPartido();

        rechazado(() -> service.enviarTarjeta(USUARIO, PARTIDO, 13L, "AMARILLA", 5), "no está entre los jugadores");
        rechazado(() -> service.enviarTarjeta(USUARIO, PARTIDO, null, "AMARILLA", 5), "Indica el jugador");
        rechazado(() -> service.enviarTarjeta(USUARIO, PARTIDO, 10L, "AZUL", 5), "AMARILLA o ROJA");
        rechazado(() -> service.enviarTarjeta(USUARIO, PARTIDO, 10L, null, 5), "AMARILLA o ROJA");
        rechazado(() -> service.enviarTarjeta(USUARIO, PARTIDO, 10L, "AMARILLA", -1), "entre 0 y 150");

        when(usuarioAppService.tieneRol(USUARIO.intValue(), "RETRANSMISION")).thenReturn(false);
        assertThatThrownBy(() -> service.enviarTarjeta(USUARIO, PARTIDO, 10L, "AMARILLA", 5))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.enviarTarjeta(USUARIO, PARTIDO, 10L, "AZUL", 5))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void unaAmarillaReintentadaAlPocoNoEsUnaDobleAmarilla() {
        empezarPartido();

        service.enviarTarjeta(USUARIO, PARTIDO, 10L, "AMARILLA", 20);
        EstadoPartidoEnVivoResponse estado = service.enviarTarjeta(USUARIO, PARTIDO, 10L, "AMARILLA", 20);

        assertThat(estado.eventos()).filteredOn(e -> e.tipo().equals("TARJETA_AMARILLA")).hasSize(1);
    }

    @Test
    void sePuedeCorregirElMinutoDeUnCambioYDeUnaTarjetaPeroNoDelInicio() {
        empezarPartido();
        service.enviarCambio(USUARIO, PARTIDO, 10L, 11L, 50);
        service.enviarTarjeta(USUARIO, PARTIDO, 12L, "AMARILLA", 51);

        EstadoPartidoEnVivoResponse estado = service.obtenerEstado(USUARIO, PARTIDO);
        Long cambioId = estado.eventos().get(1).id();
        Long tarjetaId = estado.eventos().get(2).id();
        Long inicioId = estado.eventos().get(0).id();

        EstadoPartidoEnVivoResponse corregido = service.corregirMinuto(USUARIO, PARTIDO, cambioId, 52);
        assertThat(corregido.eventos().get(1).minuto()).isEqualTo(52);
        corregido = service.corregirMinuto(USUARIO, PARTIDO, tarjetaId, 53);
        assertThat(corregido.eventos().get(2).minuto()).isEqualTo(53);

        rechazado(() -> service.corregirMinuto(USUARIO, PARTIDO, inicioId, 1), "gol, un cambio o una tarjeta");
    }

    @Test
    void sePuedeDeshacerUnCambioYUnaTarjetaYLaSegundaAmarillaDejaDeSerExpulsion() {
        alineacionConTresSuplentes();
        empezarPartido();

        EstadoPartidoEnVivoResponse conCambio = service.enviarCambio(USUARIO, PARTIDO, 100L, 109L, 30);
        Long cambioId = conCambio.eventos().get(conCambio.eventos().size() - 1).id();

        EstadoPartidoEnVivoResponse sinCambio = service.deshacer(USUARIO, PARTIDO, cambioId);
        assertThat(sinCambio.alineacion().enElCampo()).contains(100L).doesNotContain(109L);
        assertThat(sinCambio.alineacion().suplentesDisponibles()).containsExactly(109L, 110L, 111L);

        service.enviarTarjeta(USUARIO, PARTIDO, 101L, "AMARILLA", 10);
        reloj.avanzar(Duration.ofMinutes(10));
        EstadoPartidoEnVivoResponse conDoble = service.enviarTarjeta(USUARIO, PARTIDO, 101L, "AMARILLA", 40);
        assertThat(conDoble.alineacion().expulsados()).containsExactly(101L);
        assertThat(conDoble.alineacion().enElCampo()).doesNotContain(101L);

        Long dobleId = conDoble.eventos().get(conDoble.eventos().size() - 1).id();
        EstadoPartidoEnVivoResponse sinDoble = service.deshacer(USUARIO, PARTIDO, dobleId);
        assertThat(sinDoble.alineacion().expulsados()).isEmpty();
        assertThat(sinDoble.alineacion().enElCampo()).contains(101L);
        assertThat(sinDoble.eventos()).filteredOn(e -> e.tipo().equals("TARJETA_AMARILLA")).hasSize(1)
                .allMatch(e -> !e.dobleAmarilla());
    }

    @Test
    void noSeDeshaceUnCambioSiElQueEntroYaHaSalidoOTieneUnEventoPosterior() {
        alineacionConTresSuplentes();
        empezarPartido();

        EstadoPartidoEnVivoResponse primero = service.enviarCambio(USUARIO, PARTIDO, 100L, 109L, 30);
        Long primerCambio = primero.eventos().get(primero.eventos().size() - 1).id();

        // El que entró (109) sale después.
        service.enviarCambio(USUARIO, PARTIDO, 109L, 110L, 40);
        rechazado(() -> service.deshacer(USUARIO, PARTIDO, primerCambio), "ya ha salido del campo");

        // Y un cambio cuyo suplente tiene una tarjeta posterior tampoco.
        EstadoPartidoEnVivoResponse segundo = service.enviarCambio(USUARIO, PARTIDO, 101L, 111L, 50);
        Long segundoCambio = segundo.eventos().get(segundo.eventos().size() - 1).id();
        service.enviarTarjeta(USUARIO, PARTIDO, 111L, "AMARILLA", 55);
        rechazado(() -> service.deshacer(USUARIO, PARTIDO, segundoCambio), "evento posterior");
    }

    @Test
    void alFinalSeGuardanLasTarjetasEnLasEstadisticasYSeAvisaComoAlGuardarEstadisticas() {
        plantillaDeDoce();
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(PRIMER_EQUIPO)).thenReturn(List.of(50L));
        when(equipoGestionDao.obtenerNombreJugador(102L)).thenReturn("Jugador N102");
        when(equipoGestionDao.obtenerNombreJugador(101L)).thenReturn("Jugador N101");

        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 100L, null, 10);
        // 101: doble amarilla (2 amarillas + expulsión); 102: roja directa; 103: una amarilla; 104: solo gol.
        service.enviarTarjeta(USUARIO, PARTIDO, 101L, "AMARILLA", 20);
        reloj.avanzar(Duration.ofMinutes(5));
        service.enviarTarjeta(USUARIO, PARTIDO, 101L, "AMARILLA", 30);
        service.enviarTarjeta(USUARIO, PARTIDO, 102L, "ROJA", 40);
        service.enviarTarjeta(USUARIO, PARTIDO, 103L, "AMARILLA", 50);
        reloj.avanzar(Duration.ofMinutes(5));
        service.enviarGolFavor(USUARIO, PARTIDO, 104L, null, 60);
        service.enviarGolContra(USUARIO, PARTIDO, 70);

        EstadoPartidoEnVivoResponse fin = service.enviarFinalPartido(USUARIO, PARTIDO);

        ArgumentCaptor<PartidoEstadisticaJugador> guardadas = ArgumentCaptor.forClass(PartidoEstadisticaJugador.class);
        verify(estadisticaDao, times(5)).guardar(guardadas.capture());

        assertThat(guardadas.getAllValues())
                .extracting(PartidoEstadisticaJugador::getJugadorId, PartidoEstadisticaJugador::getGoles,
                        PartidoEstadisticaJugador::getTarjetasAmarillas, PartidoEstadisticaJugador::isTarjetaRoja)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(100L, 1, 0, false),
                        org.assertj.core.groups.Tuple.tuple(101L, 0, 2, true),
                        org.assertj.core.groups.Tuple.tuple(102L, 0, 0, true),
                        org.assertj.core.groups.Tuple.tuple(103L, 0, 1, false),
                        org.assertj.core.groups.Tuple.tuple(104L, 1, 0, false));

        // Mismo aviso que al guardar las estadísticas: la roja (y la doble amarilla) llegan al entrenador.
        ArgumentCaptor<com.mikedev.mutxamelcf.model.Comunicacion> aviso = ArgumentCaptor
                .forClass(com.mikedev.mutxamelcf.model.Comunicacion.class);
        verify(comunicacionService).crearPrivada(aviso.capture(), eq(List.of(50L)), eq(USUARIO));
        assertThat(aviso.getValue().getContenido()).contains("Jugador N101", "Jugador N102", "roja");

        // Deshacer el final borra esas estadísticas, como con los goles.
        service.deshacer(USUARIO, PARTIDO, fin.eventos().get(fin.eventos().size() - 1).id());
        verify(estadisticaDao).eliminarPorPartido(PARTIDO);
    }

    @Test
    void siElPartidoYaTeniaEstadisticasElFinalNoGuardaLasTarjetasNiAvisa() {
        plantillaDeDoce();
        when(estadisticaDao.obtenerPorPartido(PARTIDO)).thenReturn(List.of(new PartidoEstadisticaJugador()));

        empezarPartido();
        service.enviarTarjeta(USUARIO, PARTIDO, 102L, "ROJA", 40);
        service.enviarFinalPartido(USUARIO, PARTIDO);

        verify(estadisticaDao, never()).guardar(any());
        verify(comunicacionService, never()).crearPrivada(any(), any(), any());
    }
}
