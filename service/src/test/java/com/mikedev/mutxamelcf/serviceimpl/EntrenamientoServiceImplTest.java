package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Entrenamiento;
import com.mikedev.mutxamelcf.model.EntrenamientoAsistencia;
import com.mikedev.mutxamelcf.model.EntrenamientoAsistenciaRequest;
import com.mikedev.mutxamelcf.model.EntrenamientoGuardarRequest;
import com.mikedev.mutxamelcf.model.EntrenamientoResponse;
import com.mikedev.mutxamelcf.service.ComunicacionService;

@ExtendWith(MockitoExtension.class)
class EntrenamientoServiceImplTest {

    @Mock
    private EntrenamientoDao entrenamientoDao;

    @Mock
    private EntrenamientoAsistenciaDao asistenciaDao;

    @Mock
    private EquipoGestionDao equipoGestionDao;

    @Mock
    private ComunicacionService comunicacionService;

    private EntrenamientoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EntrenamientoServiceImpl(entrenamientoDao, asistenciaDao, equipoGestionDao,
                comunicacionService);
    }

    private static EntrenamientoGuardarRequest requestValido() {
        return new EntrenamientoGuardarRequest(1L, LocalDate.now().minusDays(1),
                List.of(new EntrenamientoAsistenciaRequest(10L, "PRESENTE")));
    }

    @Test
    void crearLanzaExcepcionSiElUsuarioNoEstaAutenticado() {
        assertThatThrownBy(() -> service.crear(null, requestValido())).isInstanceOf(SecurityException.class);
    }

    @Test
    void crearLanzaExcepcionSiFaltaLaPeticion() {
        assertThatThrownBy(() -> service.crear(1L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crearLanzaExcepcionSiLaFechaEsFutura() {
        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest(1L, LocalDate.now().plusDays(1),
                List.of(new EntrenamientoAsistenciaRequest(10L, "PRESENTE")));

        assertThatThrownBy(() -> service.crear(1L, request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior a hoy");
    }

    @Test
    void crearLanzaExcepcionSiElEquipoNoExiste() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.crear(1L, requestValido())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("equipo no existe");
    }

    @Test
    void crearLanzaExcepcionSiElEntrenadorNoPuedeGestionarElEquipo() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.crear(1L, requestValido())).isInstanceOf(SecurityException.class);
    }

    @Test
    void crearLanzaExcepcionSiFaltaLaAsistenciaDeAlgunJugadorDelEquipo() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(1L)).thenReturn(List.of(10L, 20L));

        assertThatThrownBy(() -> service.crear(1L, requestValido())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("todos los jugadores");
    }

    @Test
    void crearLanzaExcepcionSiUnJugadorNoPerteneceAlEquipo() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(1L)).thenReturn(List.of(99L));

        assertThatThrownBy(() -> service.crear(1L, requestValido())).isInstanceOf(SecurityException.class)
                .hasMessageContaining("no pertenece al equipo");
    }

    @Test
    void crearLanzaExcepcionSiUnEstadoNoEsValido() {
        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest(1L, LocalDate.now(),
                List.of(new EntrenamientoAsistenciaRequest(10L, "INVENTADO")));

        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(1L)).thenReturn(List.of(10L));

        assertThatThrownBy(() -> service.crear(1L, request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Estado de asistencia no válido");
    }

    @Test
    void crearLanzaExcepcionSiUnJugadorApareceDuplicado() {
        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest(1L, LocalDate.now(),
                List.of(new EntrenamientoAsistenciaRequest(10L, "PRESENTE"),
                        new EntrenamientoAsistenciaRequest(10L, "FALTA")));

        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(1L)).thenReturn(List.of(10L));

        assertThatThrownBy(() -> service.crear(1L, request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("más de una vez");
    }

    @Test
    void crearGuardaElEntrenamientoYLasAsistencias() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(1L)).thenReturn(List.of(10L));

        Entrenamiento guardado = new Entrenamiento();
        guardado.setId(5L);
        guardado.setEquipoId(1L);
        when(entrenamientoDao.guardar(any(Entrenamiento.class))).thenReturn(guardado);
        when(equipoGestionDao.obtenerNombreEquipo(1L)).thenReturn("Senior A");
        when(asistenciaDao.obtenerPorEntrenamiento(5L)).thenReturn(List.of());

        EntrenamientoResponse resultado = service.crear(1L, requestValido());

        assertThat(resultado.getId()).isEqualTo(5L);
        assertThat(resultado.getEquipo()).isEqualTo("Senior A");
        verify(asistenciaDao).guardar(any(EntrenamientoAsistencia.class));
    }

    @Test
    void crearGeneraComunicacionDeFaltaConDestinatarios() {
        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest(1L, LocalDate.now(),
                List.of(new EntrenamientoAsistenciaRequest(10L, "FALTA")));

        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(1L)).thenReturn(List.of(10L));

        Entrenamiento guardado = new Entrenamiento();
        guardado.setId(5L);
        guardado.setEquipoId(1L);
        when(entrenamientoDao.guardar(any(Entrenamiento.class))).thenReturn(guardado);
        when(equipoGestionDao.obtenerNombreEquipo(1L)).thenReturn("Senior A");
        when(asistenciaDao.obtenerPorEntrenamiento(5L)).thenReturn(List.of());
        when(equipoGestionDao.obtenerNombreJugador(10L)).thenReturn("Juan Perez");
        when(equipoGestionDao.obtenerUsuariosPorJugador(10L)).thenReturn(List.of(100L));
        when(equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(10L)).thenReturn(List.of());

        service.crear(1L, request);

        verify(comunicacionService).crearPrivada(any(Comunicacion.class), any(), org.mockito.ArgumentMatchers.eq(1L));
    }

    @Test
    void crearNoGeneraComunicacionDeFaltaSiNoHayDestinatarios() {
        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest(1L, LocalDate.now(),
                List.of(new EntrenamientoAsistenciaRequest(10L, "FALTA")));

        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(1L)).thenReturn(List.of(10L));

        Entrenamiento guardado = new Entrenamiento();
        guardado.setId(5L);
        guardado.setEquipoId(1L);
        when(entrenamientoDao.guardar(any(Entrenamiento.class))).thenReturn(guardado);
        when(equipoGestionDao.obtenerNombreEquipo(1L)).thenReturn("Senior A");
        when(asistenciaDao.obtenerPorEntrenamiento(5L)).thenReturn(List.of());
        when(equipoGestionDao.obtenerNombreJugador(10L)).thenReturn("Juan Perez");
        when(equipoGestionDao.obtenerUsuariosPorJugador(10L)).thenReturn(List.of());
        when(equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(10L)).thenReturn(List.of());

        service.crear(1L, request);

        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong());
    }

    @Test
    void actualizarLanzaExcepcionSiCambiaElEquipo() {
        Entrenamiento existente = new Entrenamiento();
        existente.setId(5L);
        existente.setEquipoId(2L);

        when(entrenamientoDao.obtenerPorId(5L)).thenReturn(existente);

        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest(1L, LocalDate.now(),
                List.of(new EntrenamientoAsistenciaRequest(10L, "PRESENTE")));

        assertThatThrownBy(() -> service.actualizar(1L, 5L, request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cambiar el equipo");
    }

    @Test
    void actualizarLanzaExcepcionSiElEntrenamientoNoExiste() {
        when(entrenamientoDao.obtenerPorId(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.actualizar(1L, 5L, requestValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void actualizarReemplazaLasAsistencias() {
        Entrenamiento existente = new Entrenamiento();
        existente.setId(5L);
        existente.setEquipoId(1L);

        when(entrenamientoDao.obtenerPorId(5L)).thenReturn(existente);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(1L)).thenReturn(List.of(10L));
        when(equipoGestionDao.obtenerNombreEquipo(1L)).thenReturn("Senior A");
        when(asistenciaDao.obtenerPorEntrenamiento(5L)).thenReturn(List.of());

        service.actualizar(1L, 5L, requestValido());

        verify(asistenciaDao).eliminarPorEntrenamiento(5L);
        verify(asistenciaDao).guardar(any(EntrenamientoAsistencia.class));
        verify(entrenamientoDao).actualizar(existente);
    }

    @Test
    void obtenerPorIdLanzaExcepcionSiNoExiste() {
        when(entrenamientoDao.obtenerPorId(5L)).thenReturn(null);

        assertThatThrownBy(() -> service.obtenerPorId(1L, 5L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void obtenerPorIdLanzaExcepcionSiNoPuedeGestionarElEquipo() {
        Entrenamiento entrenamiento = new Entrenamiento();
        entrenamiento.setEquipoId(1L);
        when(entrenamientoDao.obtenerPorId(5L)).thenReturn(entrenamiento);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.obtenerPorId(1L, 5L)).isInstanceOf(SecurityException.class);
    }

    @Test
    void obtenerPorEquipoLanzaExcepcionSiElEquipoNoExiste() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.obtenerPorEquipo(1L, 1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void obtenerPorEquipoDevuelveLasRespuestasMapeadas() {
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);

        Entrenamiento entrenamiento = new Entrenamiento();
        entrenamiento.setId(5L);
        entrenamiento.setEquipoId(1L);

        when(entrenamientoDao.obtenerPorEquipo(1L)).thenReturn(List.of(entrenamiento));
        when(equipoGestionDao.obtenerNombreEquipo(1L)).thenReturn("Senior A");
        when(asistenciaDao.obtenerPorEntrenamiento(5L)).thenReturn(List.of());

        List<EntrenamientoResponse> resultado = service.obtenerPorEquipo(1L, 1L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getEquipo()).isEqualTo("Senior A");
    }
}
