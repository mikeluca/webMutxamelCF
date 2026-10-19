package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.MiConvocatoriaResponse;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.RespuestaConvocatoriaRequest;
import com.mikedev.mutxamelcf.service.ComunicacionService;

class RespuestaConvocatoriaServiceImplTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static final Long USUARIO = 7L;
    private static final Long CONVOCATORIA = 20L;
    private static final Long JUGADOR = 100L;
    private static final Long PARTIDO = 30L;
    private static final Long EQUIPO = 10L;
    private static final Long ENTRENADOR = 55L;

    /** Sábado 24/10/2026 a las 09:30 es el límite; "hoy" es el miércoles anterior. */
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 24);

    private ConvocatoriaDao convocatoriaDao;
    private ConvocatoriaJugadorDao convocatoriaJugadorDao;
    private PartidoDao partidoDao;
    private EquipoGestionDao equipoGestionDao;
    private UsuarioAppVinculoDao usuarioAppVinculoDao;
    private PerfilAppDao perfilAppDao;
    private ComunicacionService comunicacionService;

    private Convocatoria convocatoria;
    private ConvocatoriaJugador convocado;
    private Partido partido;

    @BeforeEach
    void setUp() {
        convocatoriaDao = mock(ConvocatoriaDao.class);
        convocatoriaJugadorDao = mock(ConvocatoriaJugadorDao.class);
        partidoDao = mock(PartidoDao.class);
        equipoGestionDao = mock(EquipoGestionDao.class);
        usuarioAppVinculoDao = mock(UsuarioAppVinculoDao.class);
        perfilAppDao = mock(PerfilAppDao.class);
        comunicacionService = mock(ComunicacionService.class);

        convocatoria = new Convocatoria();
        convocatoria.setId(CONVOCATORIA);
        convocatoria.setEquipoId(EQUIPO);
        convocatoria.setPartidoId(PARTIDO);
        convocatoria.setRival("Pego C.F.");
        convocatoria.setCampo("Campo Municipal");
        convocatoria.setFechaPartido(SABADO);
        convocatoria.setHoraPartido("11:00");
        convocatoria.setHoraConvocatoria("09:30");
        convocatoria.setLugarConvocatoria("Vestuarios");

        convocado = new ConvocatoriaJugador(1L, CONVOCATORIA, JUGADOR);

        partido = new Partido();
        partido.setId(PARTIDO);
        partido.setEquipoId(EQUIPO);

        when(convocatoriaDao.obtenerPorId(CONVOCATORIA)).thenReturn(convocatoria);
        when(convocatoriaJugadorDao.obtenerPorConvocatoriaYJugador(CONVOCATORIA, JUGADOR)).thenReturn(convocado);
        when(partidoDao.obtenerPorId(PARTIDO)).thenReturn(partido);
        when(usuarioAppVinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(true);
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of(ENTRENADOR));
        when(equipoGestionDao.obtenerNombreJugador(JUGADOR)).thenReturn("Pablo Garcia");
        when(equipoGestionDao.obtenerNombreEquipo(EQUIPO)).thenReturn("Cadete A");
    }

    private RespuestaConvocatoriaServiceImpl servicio(LocalDateTime ahora) {
        Clock reloj = Clock.fixed(ahora.atZone(MADRID).toInstant(), MADRID);

        return new RespuestaConvocatoriaServiceImpl(
                convocatoriaDao, convocatoriaJugadorDao, partidoDao, equipoGestionDao,
                usuarioAppVinculoDao, perfilAppDao, comunicacionService, reloj);
    }

    private RespuestaConvocatoriaServiceImpl servicioAntesDelPlazo() {
        return servicio(LocalDateTime.of(2026, 10, 21, 10, 0));
    }

    private static RespuestaConvocatoriaRequest noPuede(String motivo) {
        return new RespuestaConvocatoriaRequest("NO_PUEDE", motivo);
    }

    private static RespuestaConvocatoriaRequest va() {
        return new RespuestaConvocatoriaRequest("VA", null);
    }

    // ---- plazo -----------------------------------------------------------------------

    @Test
    void respondeDentroDelPlazoYGuardaQuienYCuando() {
        LocalDateTime ahora = LocalDateTime.of(2026, 10, 24, 9, 30);

        MiConvocatoriaResponse respuesta = servicio(ahora).responder(USUARIO, CONVOCATORIA, JUGADOR, va());

        // Justo a la hora de convocatoria todavía se puede (el límite es inclusivo).
        verify(convocatoriaJugadorDao).guardarRespuesta(CONVOCATORIA, JUGADOR, "VA", null, ahora, USUARIO);
        assertThat(respuesta.respuesta()).isEqualTo("VA");
        assertThat(respuesta.fechaRespuesta()).isEqualTo(ahora);
        assertThat(respuesta.puedeResponder()).isTrue();
        assertThat(respuesta.limiteRespuesta()).isEqualTo(LocalDateTime.of(2026, 10, 24, 9, 30));
    }

    @Test
    void fueraDelPlazoNoSePuedeResponderNiCambiar() {
        RespuestaConvocatoriaServiceImpl tarde = servicio(LocalDateTime.of(2026, 10, 24, 9, 31));

        assertThatThrownBy(() -> tarde.responder(USUARIO, CONVOCATORIA, JUGADOR, va()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plazo");

        verify(convocatoriaJugadorDao, never()).guardarRespuesta(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sinHoraDeConvocatoriaElPlazoEsLaHoraDelPartido() {
        convocatoria.setHoraConvocatoria(null);

        assertThat(RespuestaConvocatoriaServiceImpl.limite(convocatoria))
                .isEqualTo(LocalDateTime.of(2026, 10, 24, 11, 0));

        // Una hora ilegible se trata como si no hubiera.
        convocatoria.setHoraConvocatoria("pronto");

        assertThat(RespuestaConvocatoriaServiceImpl.limite(convocatoria))
                .isEqualTo(LocalDateTime.of(2026, 10, 24, 11, 0));
    }

    @Test
    void sinHoraDeConvocatoriaNiDePartidoElPlazoEsLaMedianocheDelDiaDelPartido() {
        convocatoria.setHoraConvocatoria(null);
        convocatoria.setHoraPartido(null);

        assertThat(RespuestaConvocatoriaServiceImpl.limite(convocatoria))
                .isEqualTo(LocalDateTime.of(2026, 10, 24, 0, 0));

        assertThatThrownBy(() -> servicio(LocalDateTime.of(2026, 10, 24, 0, 1))
                .responder(USUARIO, CONVOCATORIA, JUGADOR, va()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void puedeCambiarLaRespuestaTodasLasVecesQuieraDentroDelPlazo() {
        RespuestaConvocatoriaServiceImpl servicio = servicioAntesDelPlazo();

        servicio.responder(USUARIO, CONVOCATORIA, JUGADOR, va());
        servicio.responder(USUARIO, CONVOCATORIA, JUGADOR, noPuede("Lesionado"));
        servicio.responder(USUARIO, CONVOCATORIA, JUGADOR, va());

        verify(convocatoriaJugadorDao, org.mockito.Mockito.times(3))
                .guardarRespuesta(eq(CONVOCATORIA), eq(JUGADOR), any(), any(), any(), eq(USUARIO));
    }

    @Test
    void conElPartidoCanceladoNoSePuedeResponder() {
        partido.setCancelado(true);

        assertThatThrownBy(() -> servicioAntesDelPlazo().responder(USUARIO, CONVOCATORIA, JUGADOR, va()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelado");
    }

    // ---- seguridad y validación -------------------------------------------------------

    @Test
    void sinVinculoConElJugadorDevuelveSecurityException() {
        when(usuarioAppVinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(false);

        assertThatThrownBy(() -> servicioAntesDelPlazo().responder(USUARIO, CONVOCATORIA, JUGADOR, va()))
                .isInstanceOf(SecurityException.class);

        verify(convocatoriaJugadorDao, never()).guardarRespuesta(any(), any(), any(), any(), any(), any());
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void elVinculoSeComprueba_antesDeRevelarSiLaConvocatoriaExiste() {
        when(usuarioAppVinculoDao.tieneVinculoConJugador(USUARIO.intValue(), 999L)).thenReturn(false);

        assertThatThrownBy(() -> servicioAntesDelPlazo().responder(USUARIO, 12345L, 999L, va()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void noPuedeSinMotivoEsErrorDeValidacion() {
        for (String motivo : new String[] { null, "", "   ", "ab", "x".repeat(501) }) {
            assertThatThrownBy(() -> servicioAntesDelPlazo()
                    .responder(USUARIO, CONVOCATORIA, JUGADOR, noPuede(motivo)))
                    .as("motivo=%s", motivo == null ? null : motivo.length())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("motivo");
        }

        verify(convocatoriaJugadorDao, never()).guardarRespuesta(any(), any(), any(), any(), any(), any());
    }

    @Test
    void elMotivoLimiteDe3Y500CaracteresSeAcepta() {
        servicioAntesDelPlazo().responder(USUARIO, CONVOCATORIA, JUGADOR, noPuede("abc"));
        servicioAntesDelPlazo().responder(USUARIO, CONVOCATORIA, JUGADOR, noPuede("x".repeat(500)));

        verify(convocatoriaJugadorDao, org.mockito.Mockito.times(2))
                .guardarRespuesta(any(), any(), eq("NO_PUEDE"), any(), any(), any());
    }

    @Test
    void conVaElMotivoSeIgnora() {
        servicioAntesDelPlazo().responder(USUARIO, CONVOCATORIA, JUGADOR,
                new RespuestaConvocatoriaRequest("va", "no debería guardarse"));

        verify(convocatoriaJugadorDao).guardarRespuesta(eq(CONVOCATORIA), eq(JUGADOR), eq("VA"), eq(null), any(),
                eq(USUARIO));
    }

    @Test
    void unaRespuestaDistintaDeVaONoPuedeEsErrorDeValidacion() {
        for (String respuesta : new String[] { null, "", "QUIZAS" }) {
            assertThatThrownBy(() -> servicioAntesDelPlazo().responder(USUARIO, CONVOCATORIA, JUGADOR,
                    new RespuestaConvocatoriaRequest(respuesta, "motivo válido")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void unJugadorQueNoEstaEnLaConvocatoriaEsErrorDeValidacion() {
        when(convocatoriaJugadorDao.obtenerPorConvocatoriaYJugador(CONVOCATORIA, JUGADOR)).thenReturn(null);

        assertThatThrownBy(() -> servicioAntesDelPlazo().responder(USUARIO, CONVOCATORIA, JUGADOR, va()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no está");

        verify(convocatoriaJugadorDao, never()).guardarRespuesta(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unaConvocatoriaQueNoExisteEsErrorDeValidacion() {
        when(convocatoriaDao.obtenerPorId(CONVOCATORIA)).thenReturn(null);

        assertThatThrownBy(() -> servicioAntesDelPlazo().responder(USUARIO, CONVOCATORIA, JUGADOR, va()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- notificaciones al cuerpo técnico ----------------------------------------------

    private void respondeCon(String anterior, RespuestaConvocatoriaRequest peticion) {
        convocado.setRespuesta(anterior);
        servicioAntesDelPlazo().responder(USUARIO, CONVOCATORIA, JUGADOR, peticion);
    }

    @Test
    void sinResponderANoPuedeAvisaAlCuerpoTecnicoConElMotivo() {
        respondeCon(null, noPuede("Enfermo"));

        ArgumentCaptor<Comunicacion> comunicacion = ArgumentCaptor.forClass(Comunicacion.class);
        verify(comunicacionService).crearPrivada(comunicacion.capture(), eq(List.of(ENTRENADOR)), eq(USUARIO));

        assertThat(comunicacion.getValue().getContenido())
                .isEqualTo("Pablo Garcia no puede ir al partido contra Pego C.F.: Enfermo");
    }

    @Test
    void deVaANoPuedeAvisa() {
        respondeCon("VA", noPuede("Enfermo"));

        verify(comunicacionService).crearPrivada(any(), eq(List.of(ENTRENADOR)), eq(USUARIO));
    }

    @Test
    void deNoPuedeAVaAvisaQueFinalmenteSiPuede() {
        respondeCon("NO_PUEDE", va());

        ArgumentCaptor<Comunicacion> comunicacion = ArgumentCaptor.forClass(Comunicacion.class);
        verify(comunicacionService).crearPrivada(comunicacion.capture(), eq(List.of(ENTRENADOR)), eq(USUARIO));

        assertThat(comunicacion.getValue().getContenido())
                .isEqualTo("Pablo Garcia finalmente sí puede ir al partido contra Pego C.F.");
    }

    @Test
    void sinResponderAVaNoAvisa() {
        respondeCon(null, va());

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void cambiarElMotivoDentroDeNoPuedeNoVuelveAAvisar() {
        respondeCon("NO_PUEDE", noPuede("Otro motivo distinto"));

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void repetirVaNoAvisa() {
        respondeCon("VA", va());

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void siNoHayEntrenadoresNoSeIntentaNotificar() {
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of());

        respondeCon(null, noPuede("Enfermo"));

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void elEntrenadorQueRespondeNoSeAvisaASiMismo() {
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO)).thenReturn(List.of(USUARIO, ENTRENADOR));

        respondeCon(null, noPuede("Enfermo"));

        verify(comunicacionService).crearPrivada(any(), eq(List.of(ENTRENADOR)), eq(USUARIO));
    }

    // ---- mis convocatorias --------------------------------------------------------------

    @Test
    void misConvocatoriasPideLosProximos14DiasDeCadaJugadorVinculado() {
        Jugador pablo = new Jugador();
        pablo.setId(JUGADOR);
        pablo.setNombre("Pablo");
        pablo.setApellidos("Garcia");

        when(perfilAppDao.obtenerJugadoresPorUsuario(USUARIO.intValue())).thenReturn(List.of(pablo));
        when(convocatoriaJugadorDao.obtenerPorJugadorYRango(eq(JUGADOR), any(), any()))
                .thenReturn(List.of(convocado));

        convocado.setRespuesta("NO_PUEDE");
        convocado.setMotivo("Enfermo");

        List<MiConvocatoriaResponse> convocatorias = servicioAntesDelPlazo().obtenerMisConvocatorias(USUARIO);

        verify(convocatoriaJugadorDao).obtenerPorJugadorYRango(
                JUGADOR, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 4));

        assertThat(convocatorias).singleElement().satisfies(c -> {
            assertThat(c.jugador()).isEqualTo("Pablo Garcia");
            assertThat(c.rival()).isEqualTo("Pego C.F.");
            assertThat(c.horaConvocatoria()).isEqualTo("09:30");
            assertThat(c.lugarConvocatoria()).isEqualTo("Vestuarios");
            assertThat(c.local()).isTrue();
            assertThat(c.respuesta()).isEqualTo("NO_PUEDE");
            assertThat(c.motivo()).isEqualTo("Enfermo");
            assertThat(c.puedeResponder()).isTrue();
        });
    }

    @Test
    void misConvocatoriasMarcaQueYaNoSePuedeResponderTrasElPlazo() {
        Jugador pablo = new Jugador();
        pablo.setId(JUGADOR);
        pablo.setNombre("Pablo");

        when(perfilAppDao.obtenerJugadoresPorUsuario(USUARIO.intValue())).thenReturn(List.of(pablo));
        when(convocatoriaJugadorDao.obtenerPorJugadorYRango(eq(JUGADOR), any(), any()))
                .thenReturn(List.of(convocado));

        List<MiConvocatoriaResponse> convocatorias = servicio(LocalDateTime.of(2026, 10, 24, 10, 0))
                .obtenerMisConvocatorias(USUARIO);

        assertThat(convocatorias).singleElement()
                .satisfies(c -> assertThat(c.puedeResponder()).isFalse());
    }

    @Test
    void misConvocatoriasOrdenaPorFechaYJugador() {
        Jugador pablo = new Jugador();
        pablo.setId(JUGADOR);
        pablo.setNombre("Pablo");

        Jugador lucia = new Jugador();
        lucia.setId(101L);
        lucia.setNombre("Lucia");

        Convocatoria otra = new Convocatoria();
        otra.setId(21L);
        otra.setEquipoId(EQUIPO);
        otra.setPartidoId(31L);
        otra.setRival("Elx");
        otra.setFechaPartido(SABADO.minusDays(1));
        otra.setHoraConvocatoria("17:00");

        Partido otroPartido = new Partido();
        otroPartido.setId(31L);

        when(convocatoriaDao.obtenerPorId(21L)).thenReturn(otra);
        when(partidoDao.obtenerPorId(31L)).thenReturn(otroPartido);

        when(perfilAppDao.obtenerJugadoresPorUsuario(USUARIO.intValue())).thenReturn(List.of(pablo, lucia));
        when(convocatoriaJugadorDao.obtenerPorJugadorYRango(eq(JUGADOR), any(), any()))
                .thenReturn(List.of(convocado));
        when(convocatoriaJugadorDao.obtenerPorJugadorYRango(eq(101L), any(), any()))
                .thenReturn(List.of(new ConvocatoriaJugador(2L, 21L, 101L)));

        List<MiConvocatoriaResponse> convocatorias = servicioAntesDelPlazo().obtenerMisConvocatorias(USUARIO);

        assertThat(convocatorias).extracting(MiConvocatoriaResponse::rival).containsExactly("Elx", "Pego C.F.");
    }

}
