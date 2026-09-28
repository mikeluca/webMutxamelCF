package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.HorarioEntrenamientoDao;
import com.mikedev.mutxamelcf.model.HorarioEntrenamiento;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoActualizarRequest;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoCrearRequest;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoResponse;
import com.mikedev.mutxamelcf.service.SesionEntrenamientoService;

@ExtendWith(MockitoExtension.class)
class HorarioEntrenamientoServiceImplTest {

    @Mock
    private HorarioEntrenamientoDao horarioEntrenamientoDao;

    @Mock
    private EquipoGestionDao equipoGestionDao;

    @Mock
    private SesionEntrenamientoService sesionEntrenamientoService;

    private HorarioEntrenamientoServiceImpl service;

    private static final Long USUARIO_ID = 1L;
    private static final Long EQUIPO_ID = 10L;
    private static final Long HORARIO_ID = 20L;

    @BeforeEach
    void setUp() {
        service = new HorarioEntrenamientoServiceImpl(horarioEntrenamientoDao, equipoGestionDao,
                sesionEntrenamientoService);
    }

    private static HorarioEntrenamientoCrearRequest requestCrearValido() {
        HorarioEntrenamientoCrearRequest request = new HorarioEntrenamientoCrearRequest();
        request.setEquipoId(EQUIPO_ID);
        request.setDiaSemana(2);
        request.setHora("18:00");
        request.setLugar("Campo Municipal");
        return request;
    }

    private static HorarioEntrenamiento horarioExistente(int diaSemana, boolean activo) {
        HorarioEntrenamiento horario = new HorarioEntrenamiento();
        horario.setId(HORARIO_ID);
        horario.setEquipoId(EQUIPO_ID);
        horario.setDiaSemana(diaSemana);
        horario.setHora("18:00");
        horario.setLugar("Campo Municipal");
        horario.setActivo(activo);
        return horario;
    }

    // ---------- crear ----------

    @Test
    void crearLanzaExcepcionSiElUsuarioNoEstaAutenticado() {
        assertThatThrownBy(() -> service.crear(null, requestCrearValido()))
                .isInstanceOf(SecurityException.class);

        Mockito.verifyNoInteractions(horarioEntrenamientoDao, sesionEntrenamientoService);
    }

    @Test
    void crearLanzaExcepcionSiElDiaSemanaEsInvalido() {
        HorarioEntrenamientoCrearRequest request = requestCrearValido();
        request.setDiaSemana(8);

        assertThatThrownBy(() -> service.crear(USUARIO_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("día de la semana");
    }

    @Test
    void crearLanzaExcepcionSiElEquipoNoExiste() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.crear(USUARIO_ID, requestCrearValido()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void crearLanzaExcepcionSiNoPuedeGestionarElEquipo() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.crear(USUARIO_ID, requestCrearValido()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("permiso");

        verify(horarioEntrenamientoDao, never()).crear(any());
    }

    @Test
    void crearGuardaElHorarioYGeneraSesionesDeInmediato() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.obtenerNombreEquipo(EQUIPO_ID)).thenReturn("Alevin A");

        when(horarioEntrenamientoDao.crear(any(HorarioEntrenamiento.class))).thenAnswer(invocation -> {
            HorarioEntrenamiento horario = invocation.getArgument(0);
            horario.setId(HORARIO_ID);
            return horario;
        });

        HorarioEntrenamientoResponse respuesta = service.crear(USUARIO_ID, requestCrearValido());

        assertThat(respuesta.getId()).isEqualTo(HORARIO_ID);
        assertThat(respuesta.getEquipo()).isEqualTo("Alevin A");
        assertThat(respuesta.isActivo()).isTrue();

        verify(sesionEntrenamientoService).generarSesiones(eq(HORARIO_ID), any(LocalDate.class));
    }

    // ---------- actualizar ----------

    @Test
    void actualizarLanzaExcepcionSiElHorarioNoExiste() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(null);

        HorarioEntrenamientoActualizarRequest request = new HorarioEntrenamientoActualizarRequest();
        request.setDiaSemana(2);
        request.setHora("18:00");
        request.setActivo(true);

        assertThatThrownBy(() -> service.actualizar(USUARIO_ID, HORARIO_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void actualizarLanzaExcepcionSiNoPuedeGestionarElEquipo() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horarioExistente(2, true));
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        HorarioEntrenamientoActualizarRequest request = new HorarioEntrenamientoActualizarRequest();
        request.setDiaSemana(2);
        request.setHora("18:00");
        request.setActivo(true);

        assertThatThrownBy(() -> service.actualizar(USUARIO_ID, HORARIO_ID, request))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void actualizarCambiandoSoloHoraRegeneraSinCancelarFuturas() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horarioExistente(2, true));
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);

        HorarioEntrenamientoActualizarRequest request = new HorarioEntrenamientoActualizarRequest();
        request.setDiaSemana(2);
        request.setHora("19:00");
        request.setLugar("Otro campo");
        request.setActivo(true);

        service.actualizar(USUARIO_ID, HORARIO_ID, request);

        verify(sesionEntrenamientoService, never()).cancelarFuturasPorHorario(anyLong());
        verify(sesionEntrenamientoService).generarSesiones(eq(HORARIO_ID), any(LocalDate.class));
    }

    @Test
    void actualizarCambiandoElDiaCancelaFuturasYRegenera() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horarioExistente(2, true));
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);

        HorarioEntrenamientoActualizarRequest request = new HorarioEntrenamientoActualizarRequest();
        request.setDiaSemana(4);
        request.setHora("18:00");
        request.setActivo(true);

        service.actualizar(USUARIO_ID, HORARIO_ID, request);

        verify(sesionEntrenamientoService).cancelarFuturasPorHorario(HORARIO_ID);
        verify(sesionEntrenamientoService).generarSesiones(eq(HORARIO_ID), any(LocalDate.class));
    }

    @Test
    void actualizarDesactivandoCancelaFuturasYNoRegenera() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horarioExistente(2, true));
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);

        HorarioEntrenamientoActualizarRequest request = new HorarioEntrenamientoActualizarRequest();
        request.setDiaSemana(2);
        request.setHora("18:00");
        request.setActivo(false);

        service.actualizar(USUARIO_ID, HORARIO_ID, request);

        verify(sesionEntrenamientoService).cancelarFuturasPorHorario(HORARIO_ID);
        verify(sesionEntrenamientoService, never()).generarSesiones(anyLong(), any());
    }

    // ---------- eliminar ----------

    @Test
    void eliminarLanzaExcepcionSinPermiso() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horarioExistente(2, true));
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.eliminar(USUARIO_ID, HORARIO_ID))
                .isInstanceOf(SecurityException.class);

        verify(horarioEntrenamientoDao, never()).actualizar(any());
    }

    @Test
    void eliminarDesactivaYCancelaSesionesFuturas() {
        HorarioEntrenamiento horario = horarioExistente(2, true);

        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horario);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);

        service.eliminar(USUARIO_ID, HORARIO_ID);

        assertThat(horario.isActivo()).isFalse();
        verify(horarioEntrenamientoDao).actualizar(horario);
        verify(sesionEntrenamientoService).cancelarFuturasPorHorario(HORARIO_ID);
    }

    // ---------- obtenerActivosPorEquipo ----------

    @Test
    void obtenerActivosPorEquipoLanzaExcepcionSinPermiso() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.obtenerActivosPorEquipo(USUARIO_ID, EQUIPO_ID))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void obtenerActivosPorEquipoDevuelveLosHorariosMapeados() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.obtenerNombreEquipo(EQUIPO_ID)).thenReturn("Alevin A");
        when(horarioEntrenamientoDao.obtenerActivosPorEquipo(EQUIPO_ID))
                .thenReturn(List.of(horarioExistente(2, true)));

        List<HorarioEntrenamientoResponse> resultado = service.obtenerActivosPorEquipo(USUARIO_ID, EQUIPO_ID);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getEquipo()).isEqualTo("Alevin A");
    }
}
