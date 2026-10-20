package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.mikedev.mutxamelcf.dao.EntrenamientoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Entrenamiento;
import com.mikedev.mutxamelcf.model.EntrenamientoPendienteDeLista;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.service.ComunicacionService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordatorioListaEntrenamientoSchedulerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 21);

    @Mock
    private EntrenamientoDao entrenamientoDao;

    @Mock
    private EquipoGestionDao equipoGestionDao;

    @Mock
    private ComunicacionService comunicacionService;

    private RecordatorioListaEntrenamientoScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(equipoGestionDao.obtenerEntrenadoresPorEquipo(5L)).thenReturn(List.of(11L));
        when(equipoGestionDao.obtenerCoordinadores()).thenReturn(List.of(30L));
        when(equipoGestionDao.obtenerNombreEquipo(5L)).thenReturn("Cadete A");
    }

    private void ahora(int hora, int minuto) {
        scheduler = new RecordatorioListaEntrenamientoScheduler(
                entrenamientoDao, equipoGestionDao, comunicacionService,
                Clock.fixed(LocalDateTime.of(HOY, LocalTime.of(hora, minuto)).atZone(MADRID).toInstant(), MADRID));
    }

    private Entrenamiento entrenamiento(long id) {
        Entrenamiento e = new Entrenamiento();
        e.setId(id);
        e.setUsuarioEntrenadorId(11L);
        when(entrenamientoDao.obtenerPorId(id)).thenReturn(e);
        return e;
    }

    private void pendiente(long id, LocalDate fecha, String hora, String estado) {
        entrenamiento(id);
        when(entrenamientoDao.obtenerPendientesDeAvisoLista(any())).thenReturn(
                List.of(new EntrenamientoPendienteDeLista(id, 5L, fecha, hora, estado)));
    }

    @Test
    void avisaCuandoPasanDosHoras() {
        pendiente(1, HOY, "18:00", "PROGRAMADA");
        ahora(20, 0);

        scheduler.enviarRecordatoriosDeListaPendiente();

        ArgumentCaptor<Comunicacion> c = ArgumentCaptor.forClass(Comunicacion.class);
        ArgumentCaptor<OpcionesPushComunicacion> o = ArgumentCaptor.forClass(OpcionesPushComunicacion.class);
        verify(comunicacionService).crearPrivada(c.capture(), eq(List.of(11L)), eq(30L), o.capture());
        assertThat(c.getValue().getContenido())
                .isEqualTo("¿Has pasado lista del entrenamiento de hoy (18:00)? Revisa la asistencia de Cadete A.");
        assertThat(o.getValue().datos()).containsEntry("tipo", "PASAR_LISTA").containsEntry("entrenamientoId", "1");
        verify(entrenamientoDao).marcarAvisoListaEnviado(1L);
    }

    @Test
    void noAvisaAntesDeLasDosHoras() {
        pendiente(1, HOY, "18:00", "PROGRAMADA");
        ahora(19, 59);

        scheduler.enviarRecordatoriosDeListaPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), anyList(), anyLong(), any());
        verify(entrenamientoDao, never()).marcarAvisoListaEnviado(anyLong());
    }

    @Test
    void noAvisaDosVecesPorqueSeMarcaYYaNoSale() {
        pendiente(1, HOY, "18:00", "PROGRAMADA");
        ahora(20, 0);

        scheduler.enviarRecordatoriosDeListaPendiente();
        // Segunda pasada: el DAO ya no lo devuelve.
        when(entrenamientoDao.obtenerPendientesDeAvisoLista(any())).thenReturn(List.of());
        scheduler.enviarRecordatoriosDeListaPendiente();

        verify(comunicacionService, times(1)).crearPrivada(any(), anyList(), anyLong(), any());
    }

    @Test
    void noAvisaSiLaListaSeRevisoEntreMedias() {
        pendiente(1, HOY, "18:00", "PROGRAMADA");
        entrenamientoDao.obtenerPorId(1L).setListaRevisada(true);
        ahora(20, 30);

        scheduler.enviarRecordatoriosDeListaPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), anyList(), anyLong(), any());
    }

    @Test
    void siEstaCanceladaSeMarcaSinEnviar() {
        pendiente(1, HOY, "18:00", "CANCELADA");
        ahora(20, 30);

        scheduler.enviarRecordatoriosDeListaPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), anyList(), anyLong(), any());
        verify(entrenamientoDao).marcarAvisoListaEnviado(1L);
    }

    @Test
    void sinHoraUsaLas21() {
        pendiente(1, HOY, null, "PROGRAMADA");

        ahora(22, 59);
        scheduler.enviarRecordatoriosDeListaPendiente();
        verify(comunicacionService, never()).crearPrivada(any(), anyList(), anyLong(), any());

        ahora(23, 0);
        scheduler.enviarRecordatoriosDeListaPendiente();

        ArgumentCaptor<Comunicacion> c = ArgumentCaptor.forClass(Comunicacion.class);
        verify(comunicacionService).crearPrivada(c.capture(), anyList(), anyLong(), any());
        assertThat(c.getValue().getContenido()).contains("(21:00)");
    }

    @Test
    void loAnteriorALaVentanaSeMarcaSinEnviar() {
        pendiente(1, HOY.minusDays(3), "18:00", "PROGRAMADA");
        ahora(20, 0);

        scheduler.enviarRecordatoriosDeListaPendiente();

        verify(comunicacionService, never()).crearPrivada(any(), anyList(), anyLong(), any());
        verify(entrenamientoDao).marcarAvisoListaEnviado(1L);
    }

    @Test
    void dentroDeLaVentanaDeUnDiaAnteriorAvisaConLaFecha() {
        pendiente(1, HOY.minusDays(1), "18:00", "PROGRAMADA");
        ahora(20, 0);

        scheduler.enviarRecordatoriosDeListaPendiente();

        ArgumentCaptor<Comunicacion> c = ArgumentCaptor.forClass(Comunicacion.class);
        verify(comunicacionService).crearPrivada(c.capture(), anyList(), anyLong(), any());
        assertThat(c.getValue().getContenido()).contains("del 20/10");
    }

    @Test
    void unEntrenamientoQueFallaNoParaALosDemas() {
        entrenamiento(1);
        entrenamiento(2);
        when(entrenamientoDao.obtenerPendientesDeAvisoLista(any())).thenReturn(List.of(
                new EntrenamientoPendienteDeLista(1L, 5L, HOY, "10:00", "PROGRAMADA"),
                new EntrenamientoPendienteDeLista(2L, 5L, HOY, "10:00", "PROGRAMADA")));
        doThrow(new RuntimeException("boom")).when(comunicacionService)
                .crearPrivada(any(), anyList(), anyLong(), any());
        ahora(20, 0);

        scheduler.enviarRecordatoriosDeListaPendiente();

        // Se intentó con los dos y ninguno se marca como avisado.
        verify(comunicacionService, times(2)).crearPrivada(any(), anyList(), anyLong(), any());
        verify(entrenamientoDao, never()).marcarAvisoListaEnviado(anyLong());
    }
}
