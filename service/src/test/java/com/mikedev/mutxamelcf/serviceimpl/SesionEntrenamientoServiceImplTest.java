package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.HorarioEntrenamientoDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaEntrenamientoDao;
import com.mikedev.mutxamelcf.dao.SesionEntrenamientoDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.HorarioEntrenamiento;
import com.mikedev.mutxamelcf.model.JustificacionFaltaEntrenamiento;
import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.model.JustificacionFaltaResponse;
import com.mikedev.mutxamelcf.model.SesionEntrenamiento;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoCrearRequest;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.EntrenamientoService;

@ExtendWith(MockitoExtension.class)
class SesionEntrenamientoServiceImplTest {

    @Mock
    private SesionEntrenamientoDao sesionEntrenamientoDao;

    @Mock
    private HorarioEntrenamientoDao horarioEntrenamientoDao;

    @Mock
    private JustificacionFaltaEntrenamientoDao justificacionFaltaEntrenamientoDao;

    @Mock
    private EquipoGestionDao equipoGestionDao;

    @Mock
    private UsuarioAppVinculoDao usuarioAppVinculoDao;

    @Mock
    private ComunicacionService comunicacionService;

    @Mock
    private EntrenamientoService entrenamientoService;

    private SesionEntrenamientoServiceImpl service;

    private static final Long USUARIO_ID = 1L;
    private static final Long EQUIPO_ID = 10L;
    private static final Long HORARIO_ID = 20L;
    private static final Long SESION_ID = 30L;
    private static final Long JUGADOR_ID = 40L;

    @BeforeEach
    void setUp() {
        service = new SesionEntrenamientoServiceImpl(sesionEntrenamientoDao, horarioEntrenamientoDao,
                justificacionFaltaEntrenamientoDao, equipoGestionDao, usuarioAppVinculoDao, comunicacionService,
                entrenamientoService);
    }

    private static HorarioEntrenamiento horario(int diaSemana, boolean activo) {
        HorarioEntrenamiento horario = new HorarioEntrenamiento();
        horario.setId(HORARIO_ID);
        horario.setEquipoId(EQUIPO_ID);
        horario.setDiaSemana(diaSemana);
        horario.setHora("18:00");
        horario.setLugar("Campo Municipal");
        horario.setActivo(activo);
        return horario;
    }

    private static SesionEntrenamiento sesion(LocalDate fecha, String estado) {
        SesionEntrenamiento sesion = new SesionEntrenamiento();
        sesion.setId(SESION_ID);
        sesion.setEquipoId(EQUIPO_ID);
        sesion.setHorarioId(HORARIO_ID);
        sesion.setFecha(fecha);
        sesion.setHora("18:00");
        sesion.setLugar("Campo Municipal");
        sesion.setEstado(estado);
        return sesion;
    }

    // ---------- generarSesiones ----------

    @Test
    void generarSesionesNoHaceNadaSiElHorarioNoExiste() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(null);

        service.generarSesiones(HORARIO_ID, LocalDate.now().plusMonths(2));

        verify(sesionEntrenamientoDao, never()).crear(any());
    }

    @Test
    void generarSesionesNoHaceNadaSiElHorarioEstaInactivo() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horario(2, false));

        service.generarSesiones(HORARIO_ID, LocalDate.now().plusMonths(2));

        verify(sesionEntrenamientoDao, never()).crear(any());
    }

    @Test
    void generarSesionesCreaUnaSesionPorCadaSemanaHastaLaFechaLimite() {
        /*
         * Horario en martes (DayOfWeek=2). Generamos desde hoy hasta
         * dentro de 3 semanas: debe crear entre 3 y 4 sesiones (una por
         * cada martes del rango), ninguna repetida, y todas en martes.
         */
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horario(2, true));
        when(sesionEntrenamientoDao.obtenerUltimaFechaGenerada(HORARIO_ID)).thenReturn(null);
        when(sesionEntrenamientoDao.existePorHorarioYFecha(eq(HORARIO_ID), any(LocalDate.class))).thenReturn(false);

        LocalDate hasta = LocalDate.now().plusWeeks(3);

        service.generarSesiones(HORARIO_ID, hasta);

        verify(sesionEntrenamientoDao, times((int) contarMartes(LocalDate.now(), hasta))).crear(any());
    }

    private static long contarMartes(LocalDate desde, LocalDate hasta) {
        long total = 0;
        LocalDate fecha = desde;
        while (!fecha.isAfter(hasta)) {
            if (fecha.getDayOfWeek() == DayOfWeek.TUESDAY) {
                total++;
            }
            fecha = fecha.plusDays(1);
        }
        return total;
    }

    @Test
    void generarSesionesNoDuplicaUnaFechaYaGenerada() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horario(2, true));
        when(sesionEntrenamientoDao.obtenerUltimaFechaGenerada(HORARIO_ID)).thenReturn(null);
        when(sesionEntrenamientoDao.existePorHorarioYFecha(eq(HORARIO_ID), any(LocalDate.class))).thenReturn(true);

        service.generarSesiones(HORARIO_ID, LocalDate.now().plusWeeks(2));

        verify(sesionEntrenamientoDao, never()).crear(any());
    }

    @Test
    void generarSesionesNoHaceNadaSiLaUltimaFechaGeneradaYaCubreElRangoPedido() {
        LocalDate ultimaGenerada = LocalDate.now().plusWeeks(1);

        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horario(2, true));
        when(sesionEntrenamientoDao.obtenerUltimaFechaGenerada(HORARIO_ID)).thenReturn(ultimaGenerada);

        // Se pide generar solo hasta la fecha ya cubierta: no hay nada
        // nuevo que generar (desde > hasta), así que ni se consulta si
        // ya existe una sesión para ninguna fecha.
        service.generarSesiones(HORARIO_ID, ultimaGenerada);

        verify(sesionEntrenamientoDao, never()).existePorHorarioYFecha(anyLong(), any(LocalDate.class));
        verify(sesionEntrenamientoDao, never()).crear(any());
    }

    @Test
    void generarSesionesCreaElEntrenamientoAutomaticoConElUsuarioQueActualizoElHorario() {
        HorarioEntrenamiento horario = horario(2, true);
        horario.setUsuarioActualizoId(777L);

        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horario);
        when(sesionEntrenamientoDao.obtenerUltimaFechaGenerada(HORARIO_ID)).thenReturn(null);
        when(sesionEntrenamientoDao.existePorHorarioYFecha(eq(HORARIO_ID), any(LocalDate.class))).thenReturn(false);

        LocalDate hasta = LocalDate.now().plusWeeks(1);

        service.generarSesiones(HORARIO_ID, hasta);

        verify(entrenamientoService, times((int) contarMartes(LocalDate.now(), hasta))).crearAutomaticoParaSesion(
                any(), eq(EQUIPO_ID), any(LocalDate.class), eq(777L));
        verify(equipoGestionDao, never()).obtenerCoordinadores();
    }

    @Test
    void generarSesionesUsaElPrimerCoordinadorSiElHorarioNoTieneUsuarioActualizo() {
        HorarioEntrenamiento horario = horario(2, true);
        horario.setUsuarioActualizoId(null);

        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horario);
        when(sesionEntrenamientoDao.obtenerUltimaFechaGenerada(HORARIO_ID)).thenReturn(null);
        when(sesionEntrenamientoDao.existePorHorarioYFecha(eq(HORARIO_ID), any(LocalDate.class))).thenReturn(false);
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(555L));

        LocalDate hasta = LocalDate.now().plusWeeks(1);

        service.generarSesiones(HORARIO_ID, hasta);

        verify(entrenamientoService, times((int) contarMartes(LocalDate.now(), hasta))).crearAutomaticoParaSesion(
                any(), eq(EQUIPO_ID), any(LocalDate.class), eq(555L));
    }

    @Test
    void generarSesionesNoLlamaAlEntrenamientoAutomaticoSiLaSesionYaExiste() {
        when(horarioEntrenamientoDao.obtenerPorId(HORARIO_ID)).thenReturn(horario(2, true));
        when(sesionEntrenamientoDao.obtenerUltimaFechaGenerada(HORARIO_ID)).thenReturn(null);
        when(sesionEntrenamientoDao.existePorHorarioYFecha(eq(HORARIO_ID), any(LocalDate.class))).thenReturn(true);

        service.generarSesiones(HORARIO_ID, LocalDate.now().plusWeeks(2));

        verify(entrenamientoService, never()).crearAutomaticoParaSesion(any(), any(), any(), any());
    }

    // ---------- crear (sesión suelta) ----------

    @Test
    void crearUnaSesionSueltaCreaAutomaticamenteElEntrenamientoConElUsuarioCreador() {
        when(equipoGestionDao.existeEquipo(EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.obtenerNombreEquipo(EQUIPO_ID)).thenReturn("Alevin A");

        when(sesionEntrenamientoDao.crear(any(SesionEntrenamiento.class))).thenAnswer(invocation -> {
            SesionEntrenamiento sesion = invocation.getArgument(0);
            sesion.setId(SESION_ID);
            return sesion;
        });

        SesionEntrenamientoCrearRequest request = new SesionEntrenamientoCrearRequest();
        request.setEquipoId(EQUIPO_ID);
        request.setFecha(LocalDate.now().plusDays(3));
        request.setHora("19:00");
        request.setLugar("Campo Municipal");

        service.crear(USUARIO_ID, request);

        verify(entrenamientoService).crearAutomaticoParaSesion(SESION_ID, EQUIPO_ID, request.getFecha(), USUARIO_ID);
    }

    // ---------- cancelar ----------

    @Test
    void cancelarLanzaExcepcionSinPermiso() {
        when(sesionEntrenamientoDao.obtenerPorId(SESION_ID)).thenReturn(sesion(LocalDate.now().plusDays(1),
                SesionEntrenamiento.ESTADO_PROGRAMADA));
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.cancelar(USUARIO_ID, SESION_ID, "Lluvia"))
                .isInstanceOf(SecurityException.class);

        verify(sesionEntrenamientoDao, never()).cancelar(anyLong(), anyString());
    }

    @Test
    void cancelarLanzaExcepcionSiElMotivoEstaVacio() {
        assertThatThrownBy(() -> service.cancelar(USUARIO_ID, SESION_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");

        verify(sesionEntrenamientoDao, never()).cancelar(anyLong(), anyString());
    }

    @Test
    void cancelarCancelaLaSesionYNotificaAJugadoresYFamiliares() {
        when(sesionEntrenamientoDao.obtenerPorId(SESION_ID)).thenReturn(sesion(LocalDate.now().plusDays(1),
                SesionEntrenamiento.ESTADO_PROGRAMADA));
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(true);
        when(equipoGestionDao.obtenerNombreEquipo(EQUIPO_ID)).thenReturn("Alevin A");
        when(equipoGestionDao.obtenerJugadoresPorEquipo(EQUIPO_ID)).thenReturn(List.of(JUGADOR_ID));
        when(equipoGestionDao.obtenerUsuariosPorJugador(JUGADOR_ID)).thenReturn(List.of(100L));
        when(equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(JUGADOR_ID)).thenReturn(List.of(200L));

        service.cancelar(USUARIO_ID, SESION_ID, "Lluvia");

        verify(sesionEntrenamientoDao).cancelar(SESION_ID, "Lluvia");
        verify(comunicacionService).crearPrivada(any(), eq(List.of(100L)), eq(USUARIO_ID));
        verify(comunicacionService).crearPrivada(any(), eq(List.of(200L)), eq(USUARIO_ID));
        verify(entrenamientoService).eliminarPorSesionEntrenamientoId(SESION_ID);
    }

    // ---------- cancelarFuturasPorHorario ----------

    @Test
    void cancelarFuturasPorHorarioEliminaLasSesionesYLosEntrenamientosVinculados() {
        SesionEntrenamiento sesion1 = sesion(LocalDate.now().plusDays(1), SesionEntrenamiento.ESTADO_PROGRAMADA);
        sesion1.setId(31L);
        SesionEntrenamiento sesion2 = sesion(LocalDate.now().plusDays(8), SesionEntrenamiento.ESTADO_PROGRAMADA);
        sesion2.setId(32L);

        when(sesionEntrenamientoDao.obtenerFuturasProgramadasPorHorario(eq(HORARIO_ID), any(LocalDate.class)))
                .thenReturn(List.of(sesion1, sesion2));

        service.cancelarFuturasPorHorario(HORARIO_ID);

        verify(sesionEntrenamientoDao).eliminarFuturasProgramadasPorHorario(eq(HORARIO_ID), any(LocalDate.class));
        verify(entrenamientoService).eliminarPorSesionEntrenamientoId(31L);
        verify(entrenamientoService).eliminarPorSesionEntrenamientoId(32L);
    }

    @Test
    void cancelarFuturasPorHorarioNoLlamaAEliminarSiNoHaySesionesAfectadas() {
        when(sesionEntrenamientoDao.obtenerFuturasProgramadasPorHorario(eq(HORARIO_ID), any(LocalDate.class)))
                .thenReturn(List.of());

        service.cancelarFuturasPorHorario(HORARIO_ID);

        verify(entrenamientoService, never()).eliminarPorSesionEntrenamientoId(any());
    }

    // ---------- justificar ----------

    @Test
    void justificarLanzaExcepcionSiLaSesionEstaCancelada() {
        when(sesionEntrenamientoDao.obtenerPorId(SESION_ID)).thenReturn(
                sesion(LocalDate.now().plusDays(1), SesionEntrenamiento.ESTADO_CANCELADA));

        JustificacionFaltaRequest request = new JustificacionFaltaRequest();
        request.setJugadorId(JUGADOR_ID);
        request.setMotivo("Lesión");

        assertThatThrownBy(() -> service.justificar(USUARIO_ID, SESION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void justificarLanzaExcepcionSiLaSesionYaHaPasado() {
        when(sesionEntrenamientoDao.obtenerPorId(SESION_ID)).thenReturn(
                sesion(LocalDate.now().minusDays(1), SesionEntrenamiento.ESTADO_PROGRAMADA));

        JustificacionFaltaRequest request = new JustificacionFaltaRequest();
        request.setJugadorId(JUGADOR_ID);

        assertThatThrownBy(() -> service.justificar(USUARIO_ID, SESION_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pasado");
    }

    @Test
    void justificarLanzaExcepcionSiElUsuarioNoEstaVinculadoAlJugador() {
        when(sesionEntrenamientoDao.obtenerPorId(SESION_ID)).thenReturn(
                sesion(LocalDate.now().plusDays(1), SesionEntrenamiento.ESTADO_PROGRAMADA));
        when(equipoGestionDao.perteneceJugadorAEquipo(JUGADOR_ID, EQUIPO_ID)).thenReturn(true);
        when(usuarioAppVinculoDao.tieneVinculoConJugador(USUARIO_ID.intValue(), JUGADOR_ID)).thenReturn(false);

        JustificacionFaltaRequest request = new JustificacionFaltaRequest();
        request.setJugadorId(JUGADOR_ID);

        assertThatThrownBy(() -> service.justificar(USUARIO_ID, SESION_ID, request))
                .isInstanceOf(SecurityException.class);

        verify(justificacionFaltaEntrenamientoDao, never()).crear(any());
    }

    @Test
    void justificarCreaUnaNuevaJustificacionSiNoExisteTodavia() {
        when(sesionEntrenamientoDao.obtenerPorId(SESION_ID)).thenReturn(
                sesion(LocalDate.now().plusDays(1), SesionEntrenamiento.ESTADO_PROGRAMADA));
        when(equipoGestionDao.perteneceJugadorAEquipo(JUGADOR_ID, EQUIPO_ID)).thenReturn(true);
        when(usuarioAppVinculoDao.tieneVinculoConJugador(USUARIO_ID.intValue(), JUGADOR_ID)).thenReturn(true);
        when(justificacionFaltaEntrenamientoDao.obtenerPorSesionYJugador(SESION_ID, JUGADOR_ID)).thenReturn(null);
        when(equipoGestionDao.obtenerNombreJugador(JUGADOR_ID)).thenReturn("Jugador Uno");

        when(justificacionFaltaEntrenamientoDao.crear(any(JustificacionFaltaEntrenamiento.class)))
                .thenAnswer(invocation -> {
                    JustificacionFaltaEntrenamiento justificacion = invocation.getArgument(0);
                    justificacion.setId(99L);
                    return justificacion;
                });

        JustificacionFaltaRequest request = new JustificacionFaltaRequest();
        request.setJugadorId(JUGADOR_ID);
        request.setMotivo("Lesión");

        JustificacionFaltaResponse respuesta = service.justificar(USUARIO_ID, SESION_ID, request);

        assertThat(respuesta.getId()).isEqualTo(99L);
        assertThat(respuesta.getJugador()).isEqualTo("Jugador Uno");
        assertThat(respuesta.getMotivo()).isEqualTo("Lesión");

        verify(justificacionFaltaEntrenamientoDao, never()).actualizar(any());
        verify(entrenamientoService).sincronizarEstadoPorJustificacion(SESION_ID, JUGADOR_ID, "FALTA_JUSTIFICADA");
    }

    @Test
    void justificarActualizaLaJustificacionExistenteEnVezDeDuplicar() {
        when(sesionEntrenamientoDao.obtenerPorId(SESION_ID)).thenReturn(
                sesion(LocalDate.now().plusDays(1), SesionEntrenamiento.ESTADO_PROGRAMADA));
        when(equipoGestionDao.perteneceJugadorAEquipo(JUGADOR_ID, EQUIPO_ID)).thenReturn(true);
        when(usuarioAppVinculoDao.tieneVinculoConJugador(USUARIO_ID.intValue(), JUGADOR_ID)).thenReturn(true);

        JustificacionFaltaEntrenamiento existente = new JustificacionFaltaEntrenamiento();
        existente.setId(55L);
        existente.setSesionId(SESION_ID);
        existente.setJugadorId(JUGADOR_ID);
        existente.setMotivo("Motivo antiguo");

        when(justificacionFaltaEntrenamientoDao.obtenerPorSesionYJugador(SESION_ID, JUGADOR_ID))
                .thenReturn(existente);

        JustificacionFaltaRequest request = new JustificacionFaltaRequest();
        request.setJugadorId(JUGADOR_ID);
        request.setMotivo("Motivo nuevo");

        JustificacionFaltaResponse respuesta = service.justificar(USUARIO_ID, SESION_ID, request);

        assertThat(respuesta.getId()).isEqualTo(55L);
        assertThat(respuesta.getMotivo()).isEqualTo("Motivo nuevo");

        verify(justificacionFaltaEntrenamientoDao).actualizar(existente);
        verify(justificacionFaltaEntrenamientoDao, never()).crear(any());
        verify(entrenamientoService).sincronizarEstadoPorJustificacion(SESION_ID, JUGADOR_ID, "FALTA_JUSTIFICADA");
    }

    // ---------- obtenerJustificaciones ----------

    @Test
    void obtenerJustificacionesLanzaExcepcionSinPermiso() {
        when(sesionEntrenamientoDao.obtenerPorId(SESION_ID)).thenReturn(
                sesion(LocalDate.now().plusDays(1), SesionEntrenamiento.ESTADO_PROGRAMADA));
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO_ID, EQUIPO_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.obtenerJustificaciones(USUARIO_ID, SESION_ID))
                .isInstanceOf(SecurityException.class);
    }
}
