package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugador;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugadorRequest;
import com.mikedev.mutxamelcf.model.PartidoEstadisticasGuardarRequest;
import com.mikedev.mutxamelcf.model.PartidoEstadisticasResponse;
import com.mikedev.mutxamelcf.model.Temporada;

@ExtendWith(MockitoExtension.class)
class PartidoEstadisticaServiceImplTest {

    @Mock
    private PartidoDao partidoDao;

    @Mock
    private PartidoEstadisticaJugadorDao estadisticaJugadorDao;

    @Mock
    private EquipoGestionDao equipoGestionDao;

    @Mock
    private ConvocatoriaDao convocatoriaDao;

    @Mock
    private ConvocatoriaJugadorDao convocatoriaJugadorDao;

    @Mock
    private com.mikedev.mutxamelcf.dao.TemporadaDao temporadaDao;

    @Mock
    private com.mikedev.mutxamelcf.dao.ResumenTemporadaDao resumenTemporadaDao;

    @Mock
    private com.mikedev.mutxamelcf.service.ComunicacionService comunicacionService;

    private PartidoEstadisticaServiceImpl service;

    private static final Long USUARIO_ID = 1L;
    private static final Long EQUIPO_ID = 10L;
    private static final Long PARTIDO_ID = 20L;
    private static final Long JUGADOR_ID = 30L;

    @BeforeEach
    void setUp() {
        service = new PartidoEstadisticaServiceImpl(
                partidoDao, estadisticaJugadorDao, equipoGestionDao, convocatoriaDao, convocatoriaJugadorDao,
                temporadaDao, resumenTemporadaDao, comunicacionService, new ReglaSancionAmarillas(5, 1));
    }

    private static Date fechaHaceDias(long dias) {
        return Date.from(LocalDate.now().minusDays(dias).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date fechaEnDias(long dias) {
        return Date.from(LocalDate.now().plusDays(dias).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Partido partidoJugadoAyer() {
        Partido partido = new Partido();
        partido.setId(PARTIDO_ID);
        partido.setEquipoId(EQUIPO_ID);
        partido.setRival("Rival CF");
        partido.setDia(fechaHaceDias(1));
        partido.setHora("18:00");
        return partido;
    }

    private static PartidoEstadisticasGuardarRequest requestValido() {
        return new PartidoEstadisticasGuardarRequest(
                3, 1,
                List.of(new PartidoEstadisticaJugadorRequest(JUGADOR_ID, 2, 1, 0, false)));
    }

    // ---------- guardar ----------

    @Test
    void guardarLanzaExcepcionSiUsuarioNoEstaAutenticado() {
        assertThatThrownBy(() -> service.guardar(null, PARTIDO_ID, requestValido()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void guardarLanzaExcepcionSiElPartidoNoExiste() {
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.guardar(USUARIO_ID, PARTIDO_ID, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void guardarLanzaExcepcionSiNoPuedeGestionarElEquipo() {
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partidoJugadoAyer());
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.guardar(USUARIO_ID, PARTIDO_ID, requestValido()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void guardarLanzaExcepcionSiElPartidoTodaviaNoSeHaJugado() {
        Partido partido = new Partido();
        partido.setId(PARTIDO_ID);
        partido.setEquipoId(EQUIPO_ID);
        partido.setDia(fechaEnDias(1));
        partido.setHora("18:00");

        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partido);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.guardar(USUARIO_ID, PARTIDO_ID, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("todavía no se ha jugado");
    }

    @Test
    void guardarLanzaExcepcionSiFaltaUnJugadorDelEquipoSinConvocatoria() {
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partidoJugadoAyer());
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO_ID)).thenReturn(null);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(JUGADOR_ID, 40L));

        assertThatThrownBy(() -> service.guardar(USUARIO_ID, PARTIDO_ID, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("todos los jugadores");
    }

    @Test
    void guardarLanzaExcepcionSiUnJugadorNoPerteneceAlRoster() {
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partidoJugadoAyer());
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO_ID)).thenReturn(null);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(999L));

        assertThatThrownBy(() -> service.guardar(USUARIO_ID, PARTIDO_ID, requestValido()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void guardarUsaElRosterDeLaConvocatoriaSiExiste() {
        Long convocatoriaId = 50L;

        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setId(convocatoriaId);

        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partidoJugadoAyer());
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO_ID)).thenReturn(convocatoria);
        when(convocatoriaJugadorDao.obtenerPorConvocatoria(convocatoriaId))
                .thenReturn(List.of(new ConvocatoriaJugador(1L, convocatoriaId, JUGADOR_ID)));
        when(estadisticaJugadorDao.obtenerPorPartido(PARTIDO_ID)).thenReturn(List.of());

        service.guardar(USUARIO_ID, PARTIDO_ID, requestValido());

        verify(equipoGestionDao, never()).obtenerJugadoresPorEquipo(EQUIPO_ID);
        verify(estadisticaJugadorDao).eliminarPorPartido(PARTIDO_ID);
        verify(estadisticaJugadorDao).guardar(any(PartidoEstadisticaJugador.class));
    }

    @Test
    void guardarCalculaYSobrescribeElResultadoAPartirDeLosGoles() {
        Partido partido = partidoJugadoAyer();

        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partido);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO_ID)).thenReturn(null);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(JUGADOR_ID));
        when(estadisticaJugadorDao.obtenerPorPartido(PARTIDO_ID)).thenReturn(List.of());

        PartidoEstadisticasResponse respuesta = service.guardar(USUARIO_ID, PARTIDO_ID, requestValido());

        assertThat(partido.getGolesFavor()).isEqualTo(3);
        assertThat(partido.getGolesContra()).isEqualTo(1);
        assertThat(partido.getResultado()).isEqualTo("3-1");
        assertThat(respuesta.getResultado()).isEqualTo("3-1");
        verify(partidoDao).actualizar(partido);
    }

    @Test
    void guardarReemplazaPorCompletoLasEstadisticasExistentes() {
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partidoJugadoAyer());
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO_ID)).thenReturn(null);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(JUGADOR_ID));
        when(estadisticaJugadorDao.obtenerPorPartido(PARTIDO_ID)).thenReturn(List.of());

        service.guardar(USUARIO_ID, PARTIDO_ID, requestValido());

        verify(estadisticaJugadorDao, times(1)).eliminarPorPartido(PARTIDO_ID);
        verify(estadisticaJugadorDao, times(1)).guardar(any(PartidoEstadisticaJugador.class));
    }

    // ---------- aviso de tarjetas ----------

    private void prepararGuardado(int amarillasAntes, int amarillasDespues, boolean rojaAntes) {
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partidoJugadoAyer());
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO_ID)).thenReturn(null);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(JUGADOR_ID));

        PartidoEstadisticaJugador previa = new PartidoEstadisticaJugador();
        previa.setJugadorId(JUGADOR_ID);
        previa.setTarjetaRoja(rojaAntes);
        when(estadisticaJugadorDao.obtenerPorPartido(PARTIDO_ID)).thenReturn(List.of(previa));

        Temporada temporada = new Temporada();
        temporada.setFechaInicio(fechaHaceDias(200));
        temporada.setFechaFin(fechaEnDias(100));
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporada);

        when(resumenTemporadaDao.estadisticasJugadoresDelEquipo(any(), any(), any(), any())).thenReturn(
                List.of(totales(amarillasAntes)), List.of(totales(amarillasDespues)));

        org.mockito.Mockito.lenient().when(equipoGestionDao.obtenerNombreJugador(JUGADOR_ID)).thenReturn("Pablo");
        org.mockito.Mockito.lenient().when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO_ID))
                .thenReturn(List.of(USUARIO_ID, 2L));
    }

    private static EstadisticasJugadorEquipo totales(int amarillas) {
        return new EstadisticasJugadorEquipo(JUGADOR_ID, "Pablo", 9, 0, 0, amarillas, 0, 1);
    }

    private static PartidoEstadisticasGuardarRequest conAmarillas(int amarillas, boolean roja) {
        return new PartidoEstadisticasGuardarRequest(
                0, 0, List.of(new PartidoEstadisticaJugadorRequest(JUGADOR_ID, 0, 0, amarillas, roja)));
    }

    private Comunicacion avisoEnviado() {
        ArgumentCaptor<Comunicacion> captor = ArgumentCaptor.forClass(Comunicacion.class);
        verify(comunicacionService).crearPrivada(captor.capture(), eq(List.of(2L)), eq(USUARIO_ID));
        return captor.getValue();
    }

    @Test
    void guardarAvisaCuandoElJugadorLlegaAUnaAmarillaDeLaSancion() {
        prepararGuardado(3, 4, false);

        service.guardar(USUARIO_ID, PARTIDO_ID, conAmarillas(1, false));

        assertThat(avisoEnviado().getContenido())
                .isEqualTo("Pablo acumula 4 amarillas: a una de posible sanción.");
    }

    @Test
    void guardarAvisaCuandoElJugadorLlegaALaPosibleSancion() {
        prepararGuardado(4, 5, false);

        service.guardar(USUARIO_ID, PARTIDO_ID, conAmarillas(1, false));

        assertThat(avisoEnviado().getContenido())
                .isEqualTo("Pablo llega a 5 amarillas: posible sanción. Compruébalo en la FFCV.");
    }

    @Test
    void guardarNoAvisaSiNoSeCruzaNingunUmbral() {
        prepararGuardado(1, 2, false);

        service.guardar(USUARIO_ID, PARTIDO_ID, conAmarillas(1, false));

        verify(comunicacionService, never()).crearPrivada(any(), any(), any());
    }

    @Test
    void volverAGuardarSinCruzarUnUmbralNuevoNoAvisa() {
        // Ya estaba en 4 y sigue en 4: el umbral se cruzó en un guardado anterior.
        prepararGuardado(4, 4, false);

        service.guardar(USUARIO_ID, PARTIDO_ID, conAmarillas(1, false));

        verify(comunicacionService, never()).crearPrivada(any(), any(), any());
    }

    @Test
    void guardarAvisaDeLaRojaYNoSeRepiteAlVolverAGuardar() {
        prepararGuardado(0, 0, false);

        service.guardar(USUARIO_ID, PARTIDO_ID, conAmarillas(0, true));

        assertThat(avisoEnviado().getContenido())
                .isEqualTo("Pablo ha visto la roja en el partido contra Rival CF: posible sanción.");
    }

    @Test
    void laRojaQueYaEstabaGuardadaNoVuelveAAvisar() {
        prepararGuardado(0, 0, true);

        service.guardar(USUARIO_ID, PARTIDO_ID, conAmarillas(0, true));

        verify(comunicacionService, never()).crearPrivada(any(), any(), any());
    }

    @Test
    void conVariosUmbralesALaVezSoloSeEnviaUnAvisoPorJugador() {
        // Cruza la sanción y además ve la roja: un único aviso, el de la sanción.
        prepararGuardado(4, 5, false);

        service.guardar(USUARIO_ID, PARTIDO_ID, conAmarillas(1, true));

        verify(comunicacionService, times(1)).crearPrivada(any(), any(), any());
    }

    @Test
    void sinEntrenadoresAvisaALosCoordinadoresSinIncluirAQuienGuarda() {
        prepararGuardado(4, 5, false);
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of());
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(USUARIO_ID, 3L));

        service.guardar(USUARIO_ID, PARTIDO_ID, conAmarillas(1, false));

        verify(comunicacionService).crearPrivada(any(Comunicacion.class), eq(List.of(3L)), eq(USUARIO_ID));
    }

    // ---------- obtener ----------

    @Test
    void obtenerLanzaExcepcionSiElPartidoNoExiste() {
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.obtener(USUARIO_ID, PARTIDO_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void obtenerLanzaExcepcionSinPermiso() {
        Partido partido = partidoJugadoAyer();
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partido);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.obtener(USUARIO_ID, PARTIDO_ID))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void obtenerDevuelveListaVaciaSiNoHayEstadisticasGuardadas() {
        Partido partido = partidoJugadoAyer();
        when(partidoDao.obtenerPorId(PARTIDO_ID)).thenReturn(partido);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(estadisticaJugadorDao.obtenerPorPartido(PARTIDO_ID)).thenReturn(List.of());

        PartidoEstadisticasResponse respuesta = service.obtener(USUARIO_ID, PARTIDO_ID);

        assertThat(respuesta.getPartidoId()).isEqualTo(PARTIDO_ID);
        assertThat(respuesta.getGolesFavor()).isNull();
        assertThat(respuesta.getJugadores()).isEmpty();
    }
}
