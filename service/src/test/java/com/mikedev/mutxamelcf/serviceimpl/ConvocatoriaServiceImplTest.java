package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaGuardarRequest;
import com.mikedev.mutxamelcf.model.ConvocatoriaResponse;
import com.mikedev.mutxamelcf.service.ComunicacionService;

@ExtendWith(MockitoExtension.class)
class ConvocatoriaServiceImplTest {

    @Mock
    private ConvocatoriaDao convocatoriaDao;

    @Mock
    private ConvocatoriaJugadorDao convocatoriaJugadorDao;

    @Mock
    private EquipoGestionDao equipoGestionDao;

    @Mock
    private ComunicacionService comunicacionService;

    private ConvocatoriaServiceImpl service;

    private static final Long USUARIO_ID = 1L;
    private static final Long EQUIPO_ID = 10L;
    private static final Long JUGADOR_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new ConvocatoriaServiceImpl(
                convocatoriaDao, convocatoriaJugadorDao, equipoGestionDao, comunicacionService);
    }

    private ConvocatoriaGuardarRequest requestValido() {
        ConvocatoriaGuardarRequest request = new ConvocatoriaGuardarRequest();
        request.setEquipoId(EQUIPO_ID);
        request.setRival("Rival CF");
        request.setCampo("Campo Municipal");
        request.setFechaPartido(LocalDate.of(2026, 3, 1));
        request.setHoraPartido("17:00");
        request.setHoraConvocatoria("16:00");
        request.setLugarConvocatoria("Vestuarios");
        request.setJugadoresIds(List.of(JUGADOR_ID));
        return request;
    }

    @Test
    void crearConUsuarioInvalidoLanzaExcepcionSinConsultarNada() {
        assertThatThrownBy(() -> service.crear(null, requestValido()))
                .isInstanceOf(SecurityException.class);

        Mockito.verifyNoInteractions(convocatoriaDao, equipoGestionDao);
    }

    @Test
    void crearConEquipoInexistenteLanzaExcepcion() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.crear(USUARIO_ID, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void crearSinPermisoParaGestionarElEquipoLanzaExcepcion() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.crear(USUARIO_ID, requestValido()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("permiso");
    }

    @Test
    void crearConEquipoSinJugadoresLanzaExcepcion() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.crear(USUARIO_ID, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tiene jugadores");
    }

    @Test
    void crearConConvocatoriaDuplicadaMismoDiaLanzaExcepcion() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(JUGADOR_ID));
        when(convocatoriaDao.existePorEquipoYFecha(eq(EQUIPO_ID), any())).thenReturn(true);

        assertThatThrownBy(() -> service.crear(USUARIO_ID, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe una convocatoria");

        verify(convocatoriaDao, never()).guardar(any());
    }

    @Test
    void crearConJugadorQueNoPerteneceAlEquipoLanzaExcepcion() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(999L));
        when(convocatoriaDao.existePorEquipoYFecha(eq(EQUIPO_ID), any())).thenReturn(false);

        assertThatThrownBy(() -> service.crear(USUARIO_ID, requestValido()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("no pertenece al equipo");

        verify(convocatoriaDao, never()).guardar(any());
    }

    @Test
    void crearConDatosValidosGuardaLaConvocatoriaYSusJugadores() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(JUGADOR_ID));
        when(convocatoriaDao.existePorEquipoYFecha(eq(EQUIPO_ID), any())).thenReturn(false);
        when(equipoGestionDao.obtenerNombreEquipo(EQUIPO_ID)).thenReturn("Equipo Alevin A");
        when(equipoGestionDao.obtenerNombreJugador(JUGADOR_ID)).thenReturn("Jugador Uno");
        when(equipoGestionDao.obtenerUsuariosPorJugador(JUGADOR_ID)).thenReturn(List.of());
        when(equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(JUGADOR_ID)).thenReturn(List.of());

        when(convocatoriaDao.guardar(any(Convocatoria.class))).thenAnswer(invocacion -> {
            Convocatoria convocatoria = invocacion.getArgument(0);
            convocatoria.setId(50L);
            return convocatoria;
        });

        when(convocatoriaJugadorDao.obtenerPorConvocatoria(50L)).thenReturn(List.of());

        ConvocatoriaResponse respuesta = service.crear(USUARIO_ID, requestValido());

        assertThat(respuesta.getId()).isEqualTo(50L);
        assertThat(respuesta.getEquipo()).isEqualTo("Equipo Alevin A");

        verify(convocatoriaDao).guardar(any(Convocatoria.class));
        verify(convocatoriaJugadorDao).guardar(argThat(
                convocatoriaJugador -> convocatoriaJugador.getConvocatoriaId().equals(50L)
                        && convocatoriaJugador.getJugadorId().equals(JUGADOR_ID)));
        verify(comunicacionService, never()).crearPrivada(any(), any(), any());
    }

    @Test
    void eliminarSinPermisoNoBorraNada() {
        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setId(50L);
        convocatoria.setEquipoId(EQUIPO_ID);

        when(convocatoriaDao.obtenerPorId(50L)).thenReturn(convocatoria);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.eliminar(USUARIO_ID, 50L))
                .isInstanceOf(SecurityException.class);

        verify(convocatoriaJugadorDao, never()).eliminarPorConvocatoria(anyLong());
        verify(convocatoriaDao, never()).eliminar(anyLong());
    }

    @Test
    void eliminarBorraPrimeroLosJugadoresYLuegoLaConvocatoria() {
        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setId(50L);
        convocatoria.setEquipoId(EQUIPO_ID);

        when(convocatoriaDao.obtenerPorId(50L)).thenReturn(convocatoria);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);

        service.eliminar(USUARIO_ID, 50L);

        InOrder orden = Mockito.inOrder(convocatoriaJugadorDao, convocatoriaDao);
        orden.verify(convocatoriaJugadorDao).eliminarPorConvocatoria(50L);
        orden.verify(convocatoriaDao).eliminar(50L);
    }
}
