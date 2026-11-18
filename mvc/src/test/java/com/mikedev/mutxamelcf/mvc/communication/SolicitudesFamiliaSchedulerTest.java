package com.mikedev.mutxamelcf.mvc.communication;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Caducada;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;

class SolicitudesFamiliaSchedulerTest {

    private SolicitudFamiliaService servicio;
    private ComunicacionesService comunicaciones;
    private SolicitudesFamiliaScheduler scheduler;

    @BeforeEach
    void setUp() {
        servicio = mock(SolicitudFamiliaService.class);
        comunicaciones = mock(ComunicacionesService.class);
        scheduler = new SolicitudesFamiliaScheduler(servicio, comunicaciones);
    }

    @Test
    void alCaducarAvisaPorEmailACadaFamiliaEnSuIdioma() {

        when(servicio.caducarVencidas()).thenReturn(List.of(new Caducada("ana@example.com", "Ana", "ca"),
                new Caducada("luis@example.com", "Luis", "en")));
        when(comunicaciones.enviarSolicitudFamiliaCaducada(any(), any(), any())).thenReturn(true);

        scheduler.caducarSolicitudes();

        verify(comunicaciones).enviarSolicitudFamiliaCaducada("ana@example.com", "Ana", "ca");
        verify(comunicaciones).enviarSolicitudFamiliaCaducada("luis@example.com", "Luis", "en");
    }

    @Test
    void unEmailQueFallaNoImpideAvisarALosDemas() {

        when(servicio.caducarVencidas()).thenReturn(List.of(new Caducada("a@example.com", "A", "es"),
                new Caducada("b@example.com", "B", "es")));
        when(comunicaciones.enviarSolicitudFamiliaCaducada(any(), any(), any()))
                .thenThrow(new RuntimeException("smtp")).thenReturn(true);

        assertThatCode(() -> scheduler.caducarSolicitudes()).doesNotThrowAnyException();

        verify(comunicaciones).enviarSolicitudFamiliaCaducada("b@example.com", "B", "es");
    }

    @Test
    void sinCaducadasNoSeEnviaNada() {

        when(servicio.caducarVencidas()).thenReturn(List.of());

        scheduler.caducarSolicitudes();

        verify(comunicaciones, never()).enviarSolicitudFamiliaCaducada(any(), any(), any());
    }

    @Test
    void unFalloDelServicioNoParaElJob() {

        when(servicio.caducarVencidas()).thenThrow(new RuntimeException("bd"));
        when(servicio.recordarPendientes()).thenThrow(new RuntimeException("bd"));

        assertThatCode(() -> scheduler.caducarSolicitudes()).doesNotThrowAnyException();
        assertThatCode(() -> scheduler.recordarPendientes()).doesNotThrowAnyException();
    }

    @Test
    void elRecordatorioLlamaAlServicio() {

        scheduler.recordarPendientes();

        verify(servicio).recordarPendientes();
    }

    @Test
    void lasTareasSonDiariasALasCuatroYALasDiezHoraDeMadrid() throws Exception {

        Scheduled caducar = SolicitudesFamiliaScheduler.class.getMethod("caducarSolicitudes")
                .getAnnotation(Scheduled.class);
        Scheduled recordar = SolicitudesFamiliaScheduler.class.getMethod("recordarPendientes")
                .getAnnotation(Scheduled.class);

        org.assertj.core.api.Assertions.assertThat(caducar.cron()).isEqualTo("0 0 4 * * *");
        org.assertj.core.api.Assertions.assertThat(recordar.cron()).isEqualTo("0 0 10 * * *");
        org.assertj.core.api.Assertions.assertThat(caducar.zone()).isEqualTo("Europe/Madrid");
        org.assertj.core.api.Assertions.assertThat(recordar.zone()).isEqualTo("Europe/Madrid");
    }
}
