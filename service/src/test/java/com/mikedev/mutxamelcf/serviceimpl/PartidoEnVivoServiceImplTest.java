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

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.dao.impl.PartidoLiveEventoDaoImpl;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugador;
import com.mikedev.mutxamelcf.service.FcmPushService;
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
    private RelojManual reloj;

    private PartidoDao partidoDao;
    private EquipoDao equipoDao;
    private EquipoGestionDao equipoGestionDao;
    private ConvocatoriaDao convocatoriaDao;
    private ConvocatoriaJugadorDao convocatoriaJugadorDao;
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

        EsquemaH2.crear(jdbc);
        jdbc.execute("""
                CREATE TABLE JUGADORES (
                    ID NUMBER PRIMARY KEY, DORSAL NUMBER, NOMBRE VARCHAR2(50), APELLIDOS VARCHAR2(50))""");
        jdbc.update("INSERT INTO JUGADORES VALUES (10, 9, 'Pablo', 'Garcia')");
        jdbc.update("INSERT INTO JUGADORES VALUES (11, 1, 'Luis', 'Lopez')");
        jdbc.update("INSERT INTO JUGADORES VALUES (12, NULL, 'Sin Dorsal', 'Perez')");
        jdbc.update("INSERT INTO JUGADORES VALUES (13, 5, 'Fuera', 'Convocatoria')");

        reloj = new RelojManual(HOY.atTime(11, 0));

        partidoDao = mock(PartidoDao.class);
        equipoDao = mock(EquipoDao.class);
        equipoGestionDao = mock(EquipoGestionDao.class);
        when(equipoGestionDao.equipoAdmiteEstadisticas(PRIMER_EQUIPO)).thenReturn(true);
        convocatoriaDao = mock(ConvocatoriaDao.class);
        convocatoriaJugadorDao = mock(ConvocatoriaJugadorDao.class);
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

        service = new PartidoEnVivoServiceImpl(
                eventoDao, partidoDao, equipoDao, equipoGestionDao, convocatoriaDao,
                convocatoriaJugadorDao, estadisticaDao, notificacionAppService, usuarioAppService, fcmPushService,
                reloj);
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

        verify(notificacionAppService, never()).difundirATodos(any(), any(), any(), any());
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

        verify(notificacionAppService, never()).difundirATodos(any(), any(), any(), any());
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
        // Y solo se difundió una vez.
        verify(notificacionAppService, times(1)).difundirATodos(eq("RESULTADO"), eq("⚽ ¡GOOOL del Mutxamel CF!"),
                any(), isNull());
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

        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificacionAppService).difundirATodos(eq("RESULTADO"), eq("↩️ Gol anulado"), mensaje.capture(),
                isNull());
        verify(fcmPushService).enviarATopic(eq("resultados"), eq("↩️ Gol anulado"), eq(mensaje.getValue()));

        assertThat(mensaje.getValue()).isEqualTo("Gol anulado. Mutxamel CF 0 - 1 Pego C.F.");
    }

    @Test
    void deshacerUnDescansoNoDifundeNadaYVuelveALaPrimeraParte() {
        empezarPartido();
        EstadoPartidoEnVivoResponse descanso = service.enviarDescanso(USUARIO, PARTIDO);

        EstadoPartidoEnVivoResponse estado = service.deshacer(USUARIO, PARTIDO,
                descanso.ultimoEventoDeshacible().id());

        assertThat(estado.fase()).isEqualTo(EstadoPartidoEnVivoResponse.PRIMERA_PARTE);
        verify(notificacionAppService, never()).difundirATodos(any(), eq("↩️ Gol anulado"), any(), any());
    }

    // ---- difusión ------------------------------------------------------------------------

    @Test
    void cadaEventoSeDifundeComoAntesConElMarcador() {
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);

        verify(notificacionAppService).difundirATodos(eq("RESULTADO"), eq("⚽ ¡Comienza el partido!"),
                eq("Mutxamel CF - Pego C.F."), isNull());
        verify(notificacionAppService).difundirATodos(eq("RESULTADO"), eq("⚽ ¡GOOOL del Mutxamel CF!"),
                eq("Pablo Garcia\n\nMutxamel CF 1 - 0 Pego C.F."), isNull());
        verify(fcmPushService).enviarATopic(eq("resultados"), eq("⚽ ¡GOOOL del Mutxamel CF!"),
                eq("Pablo Garcia\n\nMutxamel CF 1 - 0 Pego C.F."));
    }

    @Test
    void elFinalIncluyeElMarcadorYLosGoleadores() {
        empezarPartido();
        service.enviarGolFavor(USUARIO, PARTIDO, 10L, null);
        reloj.avanzar(Duration.ofMinutes(1));
        service.enviarGolFavor(USUARIO, PARTIDO, null, "p.p.");

        service.enviarFinalPartido(USUARIO, PARTIDO);

        verify(notificacionAppService).difundirATodos(eq("RESULTADO"), eq("🏁 Final del partido"),
                eq("Mutxamel CF 2 - 0 Pego C.F.\n\nGoleadores: Pablo Garcia, Gol en propia puerta del rival"
                        + "\n\nHa finalizado el partido."),
                isNull());
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
        PartidoEnVivoServiceImpl otro = new PartidoEnVivoServiceImpl(
                eventoDao, partidoDao, equipoDao, equipoGestionDao, convocatoriaDao,
                convocatoriaJugadorDao, estadisticaDao, notificacionAppService, usuarioAppService, fcmPushService,
                tardeEnMadrid);

        assertThatThrownBy(() -> otro.enviarInicioPartido(USUARIO, PARTIDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hoy");
        assertThat(ZoneOffset.UTC).isNotNull();
    }

    @Test
    void laAlineacionSeSigueDifundiendoSinPartido() {
        service.enviarAlineacion(USUARIO, "1, 2, 3", "4, 5");

        verify(notificacionAppService).difundirATodos(eq("RESULTADO"), eq("📋 Alineación del Mutxamel CF"),
                eq("Once inicial: 1, 2, 3\n\nSuplentes: 4, 5"), isNull());
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

}
