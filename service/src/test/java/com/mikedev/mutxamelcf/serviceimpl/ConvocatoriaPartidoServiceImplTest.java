package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.ConvocatoriaPartidoResponse;
import com.mikedev.mutxamelcf.model.JugadorBasico;
import com.mikedev.mutxamelcf.model.Partido;

@ExtendWith(MockitoExtension.class)
class ConvocatoriaPartidoServiceImplTest {

    private static final Long USUARIO = 7L;
    private static final Long PARTIDO = 30L;
    private static final Long JUGADOR = 100L;
    private static final Long EQUIPO = 10L;

    @Mock
    private ConvocatoriaDao convocatoriaDao;
    @Mock
    private ConvocatoriaJugadorDao convocatoriaJugadorDao;
    @Mock
    private PartidoDao partidoDao;
    @Mock
    private EquipoGestionDao equipoGestionDao;
    @Mock
    private UsuarioAppVinculoDao usuarioAppVinculoDao;
    @Mock
    private PartidoLiveEventoDao partidoLiveEventoDao;

    private ConvocatoriaPartidoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConvocatoriaPartidoServiceImpl(convocatoriaDao, convocatoriaJugadorDao, partidoDao,
                equipoGestionDao, usuarioAppVinculoDao, partidoLiveEventoDao);

        Partido partido = new Partido();
        partido.setId(PARTIDO);
        partido.setEquipoId(EQUIPO);
        partido.setRival("Pego");
        partido.setDia(Date.valueOf(LocalDate.of(2026, 10, 24)));
        partido.setHora("11:00");
        partido.setCampo("Municipal");
        partido.setLocal(false);

        lenient().when(usuarioAppVinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(true);
        lenient().when(partidoDao.obtenerPorId(PARTIDO)).thenReturn(partido);
        lenient().when(equipoGestionDao.perteneceJugadorAEquipo(JUGADOR, EQUIPO)).thenReturn(true);
    }

    private void convocatoria(Long... jugadores) {
        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setId(5L);
        convocatoria.setHoraConvocatoria("09:30");
        convocatoria.setLugarConvocatoria("Vestuarios");
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(convocatoria);

        ConvocatoriaJugador[] filas = new ConvocatoriaJugador[jugadores.length];
        for (int i = 0; i < jugadores.length; i++) {
            filas[i] = new ConvocatoriaJugador();
            filas[i].setJugadorId(jugadores[i]);
        }
        when(convocatoriaJugadorDao.obtenerPorConvocatoria(5L)).thenReturn(List.of(filas));
    }

    @Test
    void devuelveElDetalleSinDatosDeContactoYOrdenadoPorDorsal() {
        convocatoria(100L, 101L, 102L);
        when(partidoLiveEventoDao.obtenerJugadoresBasicos(List.of(100L, 101L, 102L))).thenReturn(List.of(
                new JugadorBasico(100L, 9, "Pablo", "Gil"),
                new JugadorBasico(101L, null, "Luis", "Mas"),
                new JugadorBasico(102L, 1, "Ana", "Ruiz")));

        ConvocatoriaPartidoResponse respuesta = service.obtenerParaJugador(USUARIO, PARTIDO, JUGADOR);

        assertThat(respuesta.rival()).isEqualTo("Pego");
        assertThat(respuesta.fecha()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(respuesta.hora()).isEqualTo("11:00");
        assertThat(respuesta.campo()).isEqualTo("Municipal");
        assertThat(respuesta.local()).isFalse();
        assertThat(respuesta.horaCitacion()).isEqualTo("09:30");
        assertThat(respuesta.lugarCitacion()).isEqualTo("Vestuarios");
        assertThat(respuesta.convocado()).isTrue();
        assertThat(respuesta.convocados()).extracting(ConvocatoriaPartidoResponse.Convocado::dorsal)
                .containsExactly(1, 9, null);
    }

    @Test
    void indicaQueElJugadorNoEstaConvocado() {
        convocatoria(101L);
        when(partidoLiveEventoDao.obtenerJugadoresBasicos(List.of(101L)))
                .thenReturn(List.of(new JugadorBasico(101L, 4, "Luis", "Mas")));

        assertThat(service.obtenerParaJugador(USUARIO, PARTIDO, JUGADOR).convocado()).isFalse();
    }

    @Test
    void sinVinculoConElJugadorEs403() {
        when(usuarioAppVinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(false);

        assertThatThrownBy(() -> service.obtenerParaJugador(USUARIO, PARTIDO, JUGADOR))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void siElJugadorEsDeOtroEquipoEs403() {
        when(equipoGestionDao.perteneceJugadorAEquipo(JUGADOR, EQUIPO)).thenReturn(false);

        assertThatThrownBy(() -> service.obtenerParaJugador(USUARIO, PARTIDO, JUGADOR))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void sinConvocatoriaEs404() {
        when(convocatoriaDao.obtenerPorPartidoId(PARTIDO)).thenReturn(null);

        assertThatThrownBy(() -> service.obtenerParaJugador(USUARIO, PARTIDO, JUGADOR))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void partidoInexistenteEs404() {
        when(partidoDao.obtenerPorId(PARTIDO)).thenReturn(null);

        assertThatThrownBy(() -> service.obtenerParaJugador(USUARIO, PARTIDO, JUGADOR))
                .isInstanceOf(NoSuchElementException.class);
    }
}
