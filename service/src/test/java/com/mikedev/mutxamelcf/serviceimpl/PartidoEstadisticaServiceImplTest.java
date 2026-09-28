package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugador;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugadorRequest;
import com.mikedev.mutxamelcf.model.PartidoEstadisticasGuardarRequest;
import com.mikedev.mutxamelcf.model.PartidoEstadisticasResponse;

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

    private PartidoEstadisticaServiceImpl service;

    private static final Long USUARIO_ID = 1L;
    private static final Long EQUIPO_ID = 10L;
    private static final Long PARTIDO_ID = 20L;
    private static final Long JUGADOR_ID = 30L;

    @BeforeEach
    void setUp() {
        service = new PartidoEstadisticaServiceImpl(
                partidoDao, estadisticaJugadorDao, equipoGestionDao, convocatoriaDao, convocatoriaJugadorDao);
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
