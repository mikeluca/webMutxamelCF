package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.dao.impl.ConvocatoriaJugadorDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.JustificacionFaltaPartidoDaoImpl;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartidoResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;

/**
 * Justificar la ausencia a un partido: reglas, plazo, avisos al cuerpo técnico
 * y efecto en la convocatoria. DAO reales de justificaciones y de
 * convocatoria-jugador sobre H2 (esquema de {@link EsquemaH2}) y un reloj que
 * se adelanta a mano.
 */
class JustificacionFaltaPartidoServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final Long USUARIO = 7L;
    private static final Long ENTRENADOR = 55L;
    private static final Long EQUIPO = 10L;
    private static final Long PARTIDO = 30L;
    private static final Long CONVOCATORIA = 200L;
    private static final Long JUGADOR = 100L;

    /** Miércoles 21/10/2026 10:00; el partido es el sábado 24/10 a las 11:00. */
    private static final LocalDateTime MIERCOLES = LocalDateTime.of(2026, 10, 21, 10, 0);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 24);

    private static final String MOTIVO_SECRETO = "Tengo una cita médica privada";

    private static final class RelojManual extends Clock {
        private Instant ahora;

        RelojManual(LocalDateTime inicio) {
            this.ahora = inicio.atZone(MADRID).toInstant();
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        void irA(LocalDateTime momento) {
            ahora = momento.atZone(MADRID).toInstant();
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

    private JdbcTemplate jdbc;
    private RelojManual reloj;
    private PartidoDao partidoDao;
    private ConvocatoriaDao convocatoriaDao;
    private EquipoGestionDao equipoGestionDao;
    private UsuarioAppVinculoDao vinculoDao;
    private ComunicacionService comunicacionService;
    private ConvocatoriaJugadorDaoImpl convocatoriaJugadorDao;
    private JustificacionFaltaPartidoDaoImpl justificacionDao;

    private Partido partido;
    private Convocatoria convocatoria;
    private JustificacionFaltaPartidoServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        EsquemaH2.crear(jdbc);

        reloj = new RelojManual(MIERCOLES);
        partidoDao = mock(PartidoDao.class);
        convocatoriaDao = mock(ConvocatoriaDao.class);
        equipoGestionDao = mock(EquipoGestionDao.class);
        vinculoDao = mock(UsuarioAppVinculoDao.class);
        comunicacionService = mock(ComunicacionService.class);
        convocatoriaJugadorDao = new ConvocatoriaJugadorDaoImpl(jdbc);
        justificacionDao = new JustificacionFaltaPartidoDaoImpl(jdbc);

        partido = new Partido();
        partido.setId(PARTIDO);
        partido.setEquipoId(EQUIPO);
        partido.setRival("Pego C.F.");
        partido.setDia(Date.valueOf(SABADO));
        partido.setHora("11:00");

        convocatoria = new Convocatoria();
        convocatoria.setId(CONVOCATORIA);
        convocatoria.setEquipoId(EQUIPO);
        convocatoria.setPartidoId(PARTIDO);
        convocatoria.setHoraConvocatoria("09:30");

        when(partidoDao.obtenerPorId(PARTIDO)).thenReturn(partido);
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(true);
        when(equipoGestionDao.perteneceJugadorAEquipo(anyLong(), eq(EQUIPO))).thenReturn(true);
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of(ENTRENADOR));
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO)).thenReturn(List.of(100L, 101L, 102L));
        when(equipoGestionDao.obtenerNombreJugador(anyLong())).thenAnswer(i -> "Jugador " + i.getArgument(0));

        service = new JustificacionFaltaPartidoServiceImpl(
                justificacionDao, partidoDao, convocatoriaDao, convocatoriaJugadorDao, equipoGestionDao,
                vinculoDao, comunicacionService, reloj);
    }

    private static JustificacionFaltaRequest peticion(Long jugadorId, String motivo) {
        JustificacionFaltaRequest request = new JustificacionFaltaRequest();
        request.setJugadorId(jugadorId);
        request.setMotivo(motivo);
        return request;
    }

    private void conConvocatoria(Long... jugadores) {
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(convocatoria);

        for (Long jugador : jugadores) {
            convocatoriaJugadorDao.guardar(new ConvocatoriaJugador(null, CONVOCATORIA, jugador));
        }
    }

    private List<Long> convocados() {
        return convocatoriaJugadorDao.obtenerPorConvocatoria(CONVOCATORIA).stream()
                .map(ConvocatoriaJugador::getJugadorId).toList();
    }

    private JustificacionFaltaPartidoResponse justificar(Long jugador, String motivo) {
        return service.justificar(USUARIO, PARTIDO, peticion(jugador, motivo));
    }

    // ---- reglas -------------------------------------------------------------------------

    @Test
    void justificaYGuardaMotivoRecortadoAutorYFecha() {
        JustificacionFaltaPartidoResponse respuesta = justificar(JUGADOR, "  Examen de mates  ");

        assertThat(respuesta.motivo()).isEqualTo("Examen de mates");
        assertThat(respuesta.jugador()).isEqualTo("Jugador 100");
        assertThat(respuesta.partidoId()).isEqualTo(PARTIDO);

        var guardada = justificacionDao.obtenerPorPartidoYJugador(PARTIDO, JUGADOR);

        assertThat(guardada.getMotivo()).isEqualTo("Examen de mates");
        assertThat(guardada.getUsuarioAppId()).isEqualTo(USUARIO);
        assertThat(guardada.getFechaCreacion()).isEqualTo(MIERCOLES);
    }

    @Test
    void bloqueaElPartidoAntesDeLeerLasJustificaciones() {
        justificar(JUGADOR, "Examen");
        service.retirar(USUARIO, PARTIDO, JUGADOR);

        verify(partidoDao, times(2)).bloquearPorId(PARTIDO);
    }

    @Test
    void sePuedeJustificarDesdeQueElPartidoExisteHayaONoConvocatoria() {
        // Sin convocatoria (el miércoles al importar el partido).
        assertThat(justificar(JUGADOR, "Examen")).isNotNull();

        // Con convocatoria, otro jugador.
        conConvocatoria(101L);
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), 102L)).thenReturn(true);
        assertThat(justificar(102L, "Viaje")).isNotNull();
    }

    @Test
    void sinVinculoConElJugadorEs403AunqueTodoLoDemasEsteMal() {
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(false);
        partido.setCancelado(true);

        assertThatThrownBy(() -> justificar(JUGADOR, ""))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.retirar(USUARIO, PARTIDO, JUGADOR))
                .isInstanceOf(SecurityException.class);

        assertThat(justificacionDao.obtenerPorPartido(PARTIDO)).isEmpty();
    }

    @Test
    void unJugadorDeOtroEquipoEs400() {
        when(equipoGestionDao.perteneceJugadorAEquipo(JUGADOR, EQUIPO)).thenReturn(false);

        assertThatThrownBy(() -> justificar(JUGADOR, "Examen"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pertenece al equipo");
    }

    @Test
    void elMotivoEsObligatorioDe3A500CaracteresRecortado() {
        for (String malo : new String[] { null, "", "   ", "ab", "  ab  ", "x".repeat(501) }) {
            assertThatThrownBy(() -> justificar(JUGADOR, malo))
                    .as("motivo '%s'", malo)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("motivo");
        }

        assertThat(justificar(JUGADOR, "abc")).isNotNull();
        assertThat(justificar(jugador101(), "x".repeat(500))).isNotNull();
    }

    private Long jugador101() {
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), 101L)).thenReturn(true);
        return 101L;
    }

    @Test
    void unPartidoCanceladoEs400() {
        partido.setCancelado(true);

        assertThatThrownBy(() -> justificar(JUGADOR, "Examen"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void sinRequestONiJugadorEs400() {
        assertThatThrownBy(() -> service.justificar(USUARIO, PARTIDO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.justificar(USUARIO, PARTIDO, peticion(null, "Examen")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.justificar(USUARIO, 999L, peticion(JUGADOR, "Examen")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
        assertThatThrownBy(() -> service.justificar(null, PARTIDO, peticion(JUGADOR, "Examen")))
                .isInstanceOf(SecurityException.class);
    }

    // ---- plazo --------------------------------------------------------------------------

    @Test
    void conConvocatoriaConHoraElPlazoEsLaHoraDeConvocatoria() {
        conConvocatoria(101L);

        reloj.irA(LocalDateTime.of(2026, 10, 24, 9, 30));
        assertThat(justificar(JUGADOR, "Examen")).isNotNull();   // justo a la hora: todavía se puede

        reloj.irA(LocalDateTime.of(2026, 10, 24, 9, 31));
        assertThatThrownBy(() -> justificar(JUGADOR, "Otro motivo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plazo")
                .hasMessageContaining("24/10/2026 a las 09:30");
    }

    @Test
    void conConvocatoriaSinHoraElPlazoEsLaHoraDelPartido() {
        convocatoria.setHoraConvocatoria(null);
        conConvocatoria(101L);

        reloj.irA(LocalDateTime.of(2026, 10, 24, 11, 0));
        assertThat(justificar(JUGADOR, "Examen")).isNotNull();

        reloj.irA(LocalDateTime.of(2026, 10, 24, 11, 1));
        assertThatThrownBy(() -> justificar(JUGADOR, "Otro motivo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("11:00");
    }

    @Test
    void sinConvocatoriaElPlazoEsLaHoraDelPartidoYSinHoraLaMedianoche() {
        reloj.irA(LocalDateTime.of(2026, 10, 24, 10, 59));
        assertThat(justificar(JUGADOR, "Examen")).isNotNull();

        reloj.irA(LocalDateTime.of(2026, 10, 24, 11, 1));
        assertThatThrownBy(() -> justificar(JUGADOR, "Otro motivo"))
                .isInstanceOf(IllegalArgumentException.class);

        partido.setHora(null);

        reloj.irA(LocalDateTime.of(2026, 10, 24, 0, 0));
        assertThat(justificar(JUGADOR, "Examen otra vez")).isNotNull();

        reloj.irA(LocalDateTime.of(2026, 10, 24, 0, 1));
        assertThatThrownBy(() -> justificar(JUGADOR, "Otro motivo más"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laHoraDeConvocatoriaAdmiteLosFormatosDeHoraTexto() {
        for (String hora : List.of("9:30", "09:30", "09:30:00", "09.30")) {
            convocatoria.setHoraConvocatoria(hora);
            when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(convocatoria);

            reloj.irA(LocalDateTime.of(2026, 10, 24, 9, 31));

            assertThatThrownBy(() -> justificar(JUGADOR, "Examen"))
                    .as("hora '%s'", hora)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("09:30");
        }
    }

    @Test
    void retirarTambienTieneElPlazo() {
        justificar(JUGADOR, "Examen");

        reloj.irA(LocalDateTime.of(2026, 10, 24, 11, 1));

        assertThatThrownBy(() -> service.retirar(USUARIO, PARTIDO, JUGADOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plazo");

        assertThat(justificacionDao.obtenerPorPartidoYJugador(PARTIDO, JUGADOR)).isNotNull();
    }

    // ---- avisos al justificar ---------------------------------------------------------------

    private record Aviso(Comunicacion comunicacion, OpcionesPushComunicacion push) {
    }

    private Aviso unicoAviso() {
        ArgumentCaptor<Comunicacion> comunicacion = ArgumentCaptor.forClass(Comunicacion.class);
        ArgumentCaptor<OpcionesPushComunicacion> push = ArgumentCaptor.forClass(OpcionesPushComunicacion.class);

        verify(comunicacionService).crearPrivada(comunicacion.capture(), eq(List.of(ENTRENADOR)), eq(USUARIO),
                push.capture());

        return new Aviso(comunicacion.getValue(), push.getValue());
    }

    @Test
    void siNoEstaConvocadoSeAvisaAlCuerpoTecnicoSinElMotivoEnElPush() {
        justificar(JUGADOR, MOTIVO_SECRETO);

        Aviso aviso = unicoAviso();

        assertThat(aviso.push().contenidoPush())
                .isEqualTo("Jugador 100 no podrá jugar el partido contra Pego C.F. (24/10/2026). Ausencia justificada.");
        // El motivo solo se lee dentro de la app.
        assertThat(aviso.comunicacion().getContenido()).contains(MOTIVO_SECRETO);
        assertThat(aviso.push().contenidoPush()).doesNotContain("cita");
        assertThat(aviso.push().datos().toString()).doesNotContain("cita");
        assertThat(aviso.comunicacion().getTitulo()).doesNotContain("cita");
        // No tiene nada que ver con la edición de la convocatoria.
        assertThat(aviso.push().datos()).doesNotContainKey("tipo");
    }

    @Test
    void siNoEstaConvocadoPeroHayConvocatoriaTambienSeAvisaYNoSeToca() {
        conConvocatoria(101L, 102L);

        justificar(JUGADOR, "Examen");

        assertThat(unicoAviso().push().contenidoPush()).contains("no podrá jugar");
        assertThat(convocados()).containsExactly(101L, 102L);
    }

    @Test
    void siEstaConvocadoYHayDisponiblesSeQuedaYSeAvisaParaEditar() {
        conConvocatoria(100L, 101L);   // el 102 está disponible

        justificar(JUGADOR, MOTIVO_SECRETO);

        assertThat(convocados()).containsExactly(100L, 101L);

        Aviso aviso = unicoAviso();

        // El rival ya termina en punto ("Pego C.F."): el texto del aviso añade el suyo.
        assertThat(aviso.push().contenidoPush()).isEqualTo(
                "Jugador 100 está convocado y ha justificado su ausencia para el partido contra Pego C.F."
                        + ". Edita la convocatoria para sustituirlo.");
        assertThat(aviso.push().datos())
                .containsEntry("tipo", "CONVOCATORIA_EDITAR")
                .containsEntry("convocatoriaId", String.valueOf(CONVOCATORIA));
        assertThat(aviso.push().contenidoPush()).doesNotContain("cita");
        assertThat(aviso.push().datos().values()).noneMatch(v -> v.contains("cita"));
        assertThat(aviso.comunicacion().getContenido()).contains(MOTIVO_SECRETO);
    }

    @Test
    void unDisponibleConAusenciaPropiaNoCuentaComoDisponible() {
        conConvocatoria(100L, 101L);   // el 102 sería el único disponible...
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), 102L)).thenReturn(true);
        justificar(102L, "Examen");     // ...pero también ha justificado
        org.mockito.Mockito.clearInvocations(comunicacionService);

        justificar(JUGADOR, MOTIVO_SECRETO);

        // Sin disponibles: se le quita de la convocatoria.
        assertThat(convocados()).containsExactly(101L);
    }

    @Test
    void siEstaConvocadoYNoHayDisponiblesSeLeQuitaDeLaConvocatoriaYSeAvisa() {
        conConvocatoria(100L, 101L, 102L);   // todo el equipo está convocado

        justificar(JUGADOR, MOTIVO_SECRETO);

        // Solo se borra su fila; los demás siguen.
        assertThat(convocados()).containsExactly(101L, 102L);

        Aviso aviso = unicoAviso();

        assertThat(aviso.push().contenidoPush()).isEqualTo(
                "Jugador 100 ha justificado su ausencia y se ha quitado de la convocatoria del partido contra"
                        + " Pego C.F. (no quedan más jugadores disponibles).");
        assertThat(aviso.push().contenidoPush()).doesNotContain("cita");
        assertThat(aviso.push().datos().toString()).doesNotContain("cita");
        assertThat(aviso.comunicacion().getContenido()).contains(MOTIVO_SECRETO);
    }

    @Test
    void cambiarSoloElMotivoActualizaSinNuevoAviso() {
        conConvocatoria(100L, 101L);

        justificar(JUGADOR, "Primer motivo");
        org.mockito.Mockito.clearInvocations(comunicacionService);

        reloj.avanzar(Duration.ofHours(2));
        JustificacionFaltaPartidoResponse editada = justificar(JUGADOR, "Segundo motivo");

        assertThat(editada.motivo()).isEqualTo("Segundo motivo");
        assertThat(justificacionDao.obtenerPorPartido(PARTIDO)).hasSize(1);

        var guardada = justificacionDao.obtenerPorPartidoYJugador(PARTIDO, JUGADOR);

        assertThat(guardada.getMotivo()).isEqualTo("Segundo motivo");
        assertThat(guardada.getFechaCreacion()).isEqualTo(MIERCOLES.plusHours(2));
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
    }

    @Test
    void quienJustificaYEsDelCuerpoTecnicoNoSeAvisaASiMismo() {
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of(USUARIO));

        justificar(JUGADOR, "Examen");

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
    }

    @Test
    void sinEntrenadoresNoSeIntentaAvisarPeroSiSeQuitaDeLaConvocatoria() {
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of());
        conConvocatoria(100L, 101L, 102L);

        justificar(JUGADOR, "Examen");

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
        assertThat(convocados()).containsExactly(101L, 102L);
    }

    // ---- retirar -------------------------------------------------------------------------

    @Test
    void retirarBorraLaFilaAvisaYNoVuelveAConvocar() {
        conConvocatoria(101L, 102L);
        justificar(JUGADOR, MOTIVO_SECRETO);
        org.mockito.Mockito.clearInvocations(comunicacionService);

        // Pasan más de 10 minutos: el aviso de retirada ya no está limitado.
        reloj.avanzar(Duration.ofMinutes(11));
        service.retirar(USUARIO, PARTIDO, JUGADOR);

        assertThat(justificacionDao.obtenerPorPartidoYJugador(PARTIDO, JUGADOR)).isNull();
        assertThat(convocados()).containsExactly(101L, 102L);

        Aviso aviso = unicoAviso();

        assertThat(aviso.push().contenidoPush()).isEqualTo(
                "Jugador 100 ya puede jugar el partido contra Pego C.F.; no se ha añadido a la convocatoria.");
        assertThat(aviso.comunicacion().getContenido()).doesNotContain("cita");
    }

    @Test
    void retirarUnaJustificacionQueNoExisteEs400() {
        assertThatThrownBy(() -> service.retirar(USUARIO, PARTIDO, JUGADOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene una ausencia");
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
    }

    @Test
    void retirarNoTocaLaJustificacionDeOtroJugador() {
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), 101L)).thenReturn(true);
        justificar(JUGADOR, "Examen");
        justificar(101L, "Viaje");

        service.retirar(USUARIO, PARTIDO, JUGADOR);

        assertThat(justificacionDao.obtenerPorPartido(PARTIDO)).extracting(j -> j.getJugadorId())
                .containsExactly(101L);
    }

    // ---- como máximo un aviso cada 10 minutos ---------------------------------------------------

    @Test
    void unCambioDeEstadoSiempreSeAvisaAunqueSeaAlInstante() {
        justificar(JUGADOR, "Examen");                       // JUSTIFICADA (10:00)

        reloj.avanzar(Duration.ofMinutes(2));
        service.retirar(USUARIO, PARTIDO, JUGADOR);          // RETIRADA (10:02): otro tipo, se avisa

        reloj.avanzar(Duration.ofMinutes(2));
        justificar(JUGADOR, "Otra vez");                     // JUSTIFICADA (10:04): el anterior fue RETIRADA

        reloj.avanzar(Duration.ofMinutes(1));
        service.retirar(USUARIO, PARTIDO, JUGADOR);          // RETIRADA (10:05)

        verify(comunicacionService, times(4)).crearPrivada(any(), any(), anyLong(), any());
    }

    @Test
    void dosCambiosDeMotivoSeguidosNoAvisan() {
        justificar(JUGADOR, "Primer motivo");                // aviso (10:00)

        reloj.avanzar(Duration.ofMinutes(1));
        justificar(JUGADOR, "Segundo motivo");
        reloj.avanzar(Duration.ofMinutes(1));
        justificar(JUGADOR, "Tercer motivo");

        verify(comunicacionService, times(1)).crearPrivada(any(), any(), anyLong(), any());
        assertThat(justificacionDao.obtenerPorPartidoYJugador(PARTIDO, JUGADOR).getMotivo())
                .isEqualTo("Tercer motivo");
    }

    @Test
    void elMismoTipoDeAvisoSeDescartaAntesDe10MinutosYSeEnviaDespues() {
        justificar(JUGADOR, "Examen");                       // JUSTIFICADA (10:00)

        // Se borra por otra vía (sin aviso) y se vuelve a justificar: mismo tipo que el anterior.
        justificacionDao.eliminar(PARTIDO, JUGADOR);
        reloj.avanzar(Duration.ofMinutes(9).plusSeconds(59));
        justificar(JUGADOR, "Examen otra vez");              // descartado

        verify(comunicacionService, times(1)).crearPrivada(any(), any(), anyLong(), any());

        justificacionDao.eliminar(PARTIDO, JUGADOR);
        reloj.avanzar(Duration.ofSeconds(1));                // 10 minutos desde el primer aviso
        justificar(JUGADOR, "Y otra");                       // se envía

        verify(comunicacionService, times(2)).crearPrivada(any(), any(), anyLong(), any());
    }

    @Test
    void elEnvioSeAnotaEnElLimitadorSoloCuandoLaTransaccionConfirma() {
        TransactionSynchronizationManager.initSynchronization();

        try {
            justificar(JUGADOR, "Examen");

            // Dentro de la transacción todavía no consta ningún aviso enviado...
            assertThat(service.ultimoTipoAviso(PARTIDO, JUGADOR)).isNull();

            // ...y consta al confirmar.
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());

            assertThat(service.ultimoTipoAviso(PARTIDO, JUGADOR)).isEqualTo("JUSTIFICADA");

        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void siLaTransaccionSeDeshaceNoConstaElAviso() {
        TransactionSynchronizationManager.initSynchronization();

        try {
            justificar(JUGADOR, "Examen");

            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));

            assertThat(service.ultimoTipoAviso(PARTIDO, JUGADOR)).isNull();

        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    // ---- destinatarios ------------------------------------------------------------------------

    @Test
    void sinEntrenadoresConCuentaElAvisoVaALosCoordinadores() {
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of());
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(77L, 78L));

        justificar(JUGADOR, "Examen");

        verify(comunicacionService).crearPrivada(any(), eq(List.of(77L, 78L)), eq(USUARIO), any());
    }

    @Test
    void conEntrenadoresNoSeAvisaALosCoordinadores() {
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(77L));

        justificar(JUGADOR, "Examen");

        verify(comunicacionService).crearPrivada(any(), eq(List.of(ENTRENADOR)), eq(USUARIO), any());
    }

    @Test
    void sinEntrenadoresNiCoordinadoresSeDejaUnWarn() {
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of());
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of());

        Logger logger = (Logger) LoggerFactory.getLogger(JustificacionFaltaPartidoServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            justificar(JUGADOR, "Examen");

            assertThat(appender.list)
                    .filteredOn(evento -> evento.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(evento -> assertThat(evento.getFormattedMessage()).contains(String.valueOf(EQUIPO)));

        } finally {
            logger.detachAppender(appender);
        }

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
    }

    @Test
    void elLimiteEsPorJugadorYPorPartido() {
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), 101L)).thenReturn(true);

        Partido otroPartido = new Partido();
        otroPartido.setId(31L);
        otroPartido.setEquipoId(EQUIPO);
        otroPartido.setRival("Elx");
        otroPartido.setDia(Date.valueOf(SABADO.plusDays(1)));
        otroPartido.setHora("12:00");
        when(partidoDao.obtenerPorId(31L)).thenReturn(otroPartido);

        justificar(JUGADOR, "Examen");
        justificar(101L, "Viaje");
        service.justificar(USUARIO, 31L, peticion(JUGADOR, "Otro partido"));

        verify(comunicacionService, times(3)).crearPrivada(any(), any(), anyLong(), any());
    }

    @Test
    void elAvisoDeQuitadoDeLaConvocatoriaSeEnviaSiempre() {
        conConvocatoria(100L, 101L, 102L);

        justificar(JUGADOR, "Examen");                       // se le quita: aviso (10:00)
        convocatoriaJugadorDao.guardar(new ConvocatoriaJugador(null, CONVOCATORIA, JUGADOR));
        justificacionDao.eliminar(PARTIDO, JUGADOR);          // el entrenador lo vuelve a convocar a mano

        reloj.avanzar(Duration.ofMinutes(1));
        justificar(JUGADOR, "Examen otra vez");              // vuelve a quitarse: aviso aunque haya pasado 1 minuto

        verify(comunicacionService, times(2)).crearPrivada(any(), any(), anyLong(), any());
        assertThat(convocados()).containsExactly(101L, 102L);
    }

    // ---- consulta del entrenador -----------------------------------------------------------------

    @Test
    void elEntrenadorVeQuienHaJustificado() {
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), 101L)).thenReturn(true);
        justificar(JUGADOR, "Examen");
        justificar(101L, "Viaje");
        when(equipoGestionDao.puedeGestionarEquipo(ENTRENADOR, EQUIPO)).thenReturn(true);

        List<JustificacionFaltaPartidoResponse> lista = service.obtener(ENTRENADOR, PARTIDO);

        assertThat(lista).extracting(JustificacionFaltaPartidoResponse::jugadorId,
                JustificacionFaltaPartidoResponse::motivo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(100L, "Examen"),
                        org.assertj.core.groups.Tuple.tuple(101L, "Viaje"));
    }

    @Test
    void quienNoGestionaElEquipoNoPuedeConsultar() {
        justificar(JUGADOR, "Examen");
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO, EQUIPO)).thenReturn(false);

        assertThatThrownBy(() -> service.obtener(USUARIO, PARTIDO)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.obtener(ENTRENADOR, 999L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ningunAvisoLlevaElMotivoEnElPushNiSiquieraAlRetirar() {
        conConvocatoria(100L, 101L);
        justificar(JUGADOR, MOTIVO_SECRETO);
        reloj.avanzar(Duration.ofMinutes(11));
        service.retirar(USUARIO, PARTIDO, JUGADOR);
        reloj.avanzar(Duration.ofMinutes(11));
        justificar(JUGADOR, MOTIVO_SECRETO);

        ArgumentCaptor<OpcionesPushComunicacion> push = ArgumentCaptor.forClass(OpcionesPushComunicacion.class);
        verify(comunicacionService, times(3)).crearPrivada(any(), any(), anyLong(), push.capture());

        for (OpcionesPushComunicacion opciones : push.getAllValues()) {
            assertThat(opciones.contenidoPush()).isNotNull().doesNotContain("cita").doesNotContain("médica");
            assertThat(opciones.datos().values()).noneMatch(v -> v.contains("cita"));
        }

        var orden = inOrder(partidoDao);
        orden.verify(partidoDao, times(3)).bloquearPorId(PARTIDO);
    }
}
