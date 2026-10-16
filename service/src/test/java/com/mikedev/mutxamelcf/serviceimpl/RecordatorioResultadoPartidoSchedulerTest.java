package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.annotation.Scheduled;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordatorioResultadoPartidoSchedulerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    // Sábado 17-10-2026, 12:00 en Madrid.
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Clock RELOJ = Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID);

    private static final Long EQUIPO_ID = 10L;
    private static final Long PARTIDO_ID = 20L;

    @Mock
    private PartidoDao partidoDao;

    @Mock
    private EquipoGestionDao equipoGestionDao;

    @Mock
    private ComunicacionService comunicacionService;

    private RecordatorioResultadoPartidoScheduler scheduler;

    /** Estado simulado de la tabla PARTIDOS para el DAO falso. */
    private final List<Partido> tabla = new ArrayList<>();
    private final List<LocalDateTime> inicios = new ArrayList<>();

    @BeforeEach
    void setUp() {
        scheduler = new RecordatorioResultadoPartidoScheduler(partidoDao, equipoGestionDao, comunicacionService, RELOJ);

        when(equipoGestionDao.obtenerNombreEquipo(EQUIPO_ID)).thenReturn("Cadete A");
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(100L));
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(555L));
        when(equipoGestionDao.obtenerAdministradoresApp()).thenReturn(List.of());

        /*
         * DAO falso con la semántica del real (ventana de inicio, no
         * cancelado, aviso = 0, sin resultado) para probar el job de
         * extremo a extremo con el reloj fijo. Las condiciones SQL de
         * verdad se comprueban en PartidoDaoImpl*Test.
         */
        when(partidoDao.obtenerPendientesDeAvisoResultado(any(), any())).thenAnswer(inv -> {
            LocalDateTime desde = inv.getArgument(0);
            LocalDateTime hasta = inv.getArgument(1);
            List<Partido> resultado = new ArrayList<>();
            for (int i = 0; i < tabla.size(); i++) {
                Partido p = tabla.get(i);
                LocalDateTime inicio = inicios.get(i);
                if (!p.isCancelado() && !p.isAvisoResultadoEnviado() && p.getGolesFavor() == null
                        && (p.getResultado() == null || p.getResultado().replace("-", "").isBlank())
                        && !inicio.isBefore(desde) && !inicio.isAfter(hasta)) {
                    resultado.add(p);
                }
            }
            return resultado;
        });

        when(partidoDao.marcarAvisosAntiguosComoEnviados(any())).thenAnswer(inv -> {
            LocalDateTime limite = inv.getArgument(0);
            int n = 0;
            for (int i = 0; i < tabla.size(); i++) {
                Partido p = tabla.get(i);
                if (!p.isAvisoResultadoEnviado() && inicios.get(i).isBefore(limite)) {
                    p.setAvisoResultadoEnviado(true);
                    n++;
                }
            }
            return n;
        });

        when(partidoDao.obtenerPorId(anyLong())).thenAnswer(inv -> tabla.stream()
                .filter(p -> p.getId().equals(inv.getArgument(0))).findFirst().orElse(null));

        org.mockito.Mockito.doAnswer(inv -> {
            tabla.stream().filter(p -> p.getId().equals(inv.getArgument(0)))
                    .forEach(p -> p.setAvisoResultadoEnviado(true));
            return null;
        }).when(partidoDao).marcarAvisoResultadoEnviado(anyLong());
    }

    private Partido anadir(Long id, LocalDateTime inicio, Long usuarioActualizoId) {
        Partido partido = new Partido();
        partido.setId(id);
        partido.setEquipoId(EQUIPO_ID);
        partido.setRival("Rival CF");
        partido.setDia(Date.valueOf(inicio.toLocalDate()));
        partido.setUsuarioActualizoId(usuarioActualizoId);
        tabla.add(partido);
        inicios.add(inicio);
        return partido;
    }

    private static java.time.Duration h(long horas, long minutos) {
        return java.time.Duration.ofMinutes(horas * 60 + minutos);
    }

    private Partido anadirIniciadoHace(java.time.Duration duracion) {
        return anadir(PARTIDO_ID, AHORA.minus(duracion), null);
    }

    // ---- ventana de 2 h ------------------------------------------------------------

    @Test
    void unPartidoQueEmpezoHace2h05SinResultadoSeAvisaUnaVez() {
        anadirIniciadoHace(h(2, 5));

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        ArgumentCaptor<Comunicacion> captor = ArgumentCaptor.forClass(Comunicacion.class);
        verify(comunicacionService, times(1)).crearPrivada(captor.capture(), eq(List.of(100L)), eq(555L));
        verify(partidoDao).marcarAvisoResultadoEnviado(PARTIDO_ID);
        assertThat(captor.getValue().getTitulo()).isEqualTo("Resultado pendiente");
    }

    @Test
    void unPartidoQueEmpezoHace1h50TodaviaNoSeAvisa() {
        anadirIniciadoHace(h(1, 50));

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
        verify(partidoDao, never()).marcarAvisoResultadoEnviado(anyLong());
    }

    @Test
    void laConsultaPideLaVentanaDeTresDiasAHaceDosHorasConElRelojDeMadrid() {
        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(partidoDao).obtenerPendientesDeAvisoResultado(AHORA.minusDays(3), AHORA.minusHours(2));
        verify(partidoDao).marcarAvisosAntiguosComoEnviados(AHORA.minusDays(3));
    }

    @Test
    void usaLaHoraDeMadridAunqueLaZonaDelServidorSeaOtra() {
        // 12:00 en Madrid = 10:00 UTC (horario de verano en octubre).
        Clock relojUtc = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), MADRID);
        RecordatorioResultadoPartidoScheduler otro = new RecordatorioResultadoPartidoScheduler(
                partidoDao, equipoGestionDao, comunicacionService, relojUtc);

        otro.enviarRecordatoriosDeResultadoPendiente();

        verify(partidoDao).obtenerPendientesDeAvisoResultado(AHORA.minusDays(3), AHORA.minusHours(2));
    }

    // ---- resultado ya puesto ------------------------------------------------------------

    @Test
    void siElResultadoSeRellenaEntreLaConsultaYElEnvioNoSeAvisa() {
        Partido enLista = anadirIniciadoHace(h(2, 5));

        // La consulta devuelve el partido, pero al releerlo ya tiene resultado.
        Partido releido = new Partido();
        releido.setId(PARTIDO_ID);
        releido.setEquipoId(EQUIPO_ID);
        releido.setResultado("2-1");
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(releido);

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        assertThat(enLista).isNotNull();
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void siSeRellenaSoloElResultadoDeTextoTampocoSeAvisa() {
        anadirIniciadoHace(h(2, 5));
        Partido releido = new Partido();
        releido.setId(PARTIDO_ID);
        releido.setEquipoId(EQUIPO_ID);
        releido.setResultado(" 3 - 0 ");
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(releido);

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void siSeRellenanLosGolesTampocoSeAvisa() {
        anadirIniciadoHace(h(2, 5));
        Partido releido = new Partido();
        releido.setId(PARTIDO_ID);
        releido.setEquipoId(EQUIPO_ID);
        releido.setGolesFavor(2);
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(releido);

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void unResultadoDeSoloGuionesCuentaComoVacioYSiSeAvisa() {
        anadirIniciadoHace(h(2, 5)).setResultado(" - ");

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void conResultado2a1YGolesNulosNoSeAvisa() {
        anadirIniciadoHace(h(2, 5)).setResultado("2-1");

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void unPartidoCanceladoNoSeAvisa() {
        Partido partido = anadirIniciadoHace(h(2, 5));
        partido.setCancelado(true);

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    // ---- antigüedad ---------------------------------------------------------------------------

    @Test
    void unPartidoDeHace5DiasSeMarcaEnviadoSinMandarMensaje() {
        Partido antiguo = anadirIniciadoHace(java.time.Duration.ofDays(5));

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        assertThat(antiguo.isAvisoResultadoEnviado()).isTrue();
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
        verify(partidoDao, never()).marcarAvisoResultadoEnviado(anyLong());
    }

    @Test
    void unPartidoDeHace2Dias23HorasTodaviaSeAvisa() {
        anadirIniciadoHace(java.time.Duration.ofHours(71));

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService).crearPrivada(any(), any(), anyLong());
    }

    // ---- autor ----------------------------------------------------------------------------------

    @Test
    void siElQueActualizoElPartidoEsElEntrenadorDestinatarioElAutorEsUnCoordinador() {
        anadir(PARTIDO_ID, AHORA.minus(h(2, 5)), 100L);

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService).crearPrivada(any(), eq(List.of(100L)), eq(555L));
    }

    @Test
    void elAutorNuncaEsUnCoordinadorQueTambienEsDestinatario() {
        anadirIniciadoHace(h(2, 5));
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(100L, 556L));

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService).crearPrivada(any(), eq(List.of(100L)), eq(556L));
    }

    @Test
    void sinCoordinadoresUtilesElAutorEsUnAdminApp() {
        anadirIniciadoHace(h(2, 5));
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of());
        when(equipoGestionDao.obtenerAdministradoresApp()).thenReturn(List.of(777L));

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService).crearPrivada(any(), any(), eq(777L));
    }

    @Test
    void comoUltimoRecursoElAutorEsQuienActualizoElPartido() {
        anadir(PARTIDO_ID, AHORA.minus(h(2, 5)), 99L);
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of());

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService).crearPrivada(any(), any(), eq(99L));
    }

    @Test
    void sinNingunAutorPosibleSeMarcaEnviadoSinNotificar() {
        anadirIniciadoHace(h(2, 5));
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of());

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
        verify(partidoDao).marcarAvisoResultadoEnviado(PARTIDO_ID);
    }

    @Test
    void sinEntrenadoresSeMarcaEnviadoSinNotificar() {
        anadirIniciadoHace(h(2, 5));
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of());

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
        verify(partidoDao).marcarAvisoResultadoEnviado(PARTIDO_ID);
    }

    // ---- texto y repetición ---------------------------------------------------------------------

    @Test
    void elTextoIncluyeEquipoRivalFechaYElOrdenDelMarcador() {
        anadirIniciadoHace(h(2, 5));

        scheduler.enviarRecordatoriosDeResultadoPendiente();

        ArgumentCaptor<Comunicacion> captor = ArgumentCaptor.forClass(Comunicacion.class);
        verify(comunicacionService).crearPrivada(captor.capture(), any(), anyLong());
        assertThat(captor.getValue().getContenido()).isEqualTo(
                "Introduce el resultado del partido Cadete A - Rival CF del día 17/10/2026"
                        + " (goles del Mutxamel - goles del rival).");
    }

    @Test
    void unaSegundaEjecucionNoReenvia() {
        anadirIniciadoHace(h(2, 5));

        scheduler.enviarRecordatoriosDeResultadoPendiente();
        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, times(1)).crearPrivada(any(), any(), anyLong());
        verify(partidoDao, times(1)).marcarAvisoResultadoEnviado(PARTIDO_ID);
    }

    @Test
    void noHaceNadaSiNoHayPartidosPendientes() {
        scheduler.enviarRecordatoriosDeResultadoPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
        verify(partidoDao, never()).marcarAvisoResultadoEnviado(anyLong());
    }

    // ---- programación ---------------------------------------------------------------------------------

    @Test
    void estaProgramadoCadaQuinceMinutosEnLaZonaDeMadrid() throws Exception {
        Scheduled scheduled = RecordatorioResultadoPartidoScheduler.class
                .getMethod("enviarRecordatoriosDeResultadoPendiente").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 */15 * * * *");
        assertThat(scheduled.zone()).isEqualTo("Europe/Madrid");
    }

    @Test
    void elJobMensualDeSesionesTambienUsaLaZonaDeMadrid() throws Exception {
        Scheduled scheduled = GeneracionSesionesEntrenamientoScheduler.class
                .getMethod("generarSesionesMensualmente").getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Europe/Madrid");
    }
}
