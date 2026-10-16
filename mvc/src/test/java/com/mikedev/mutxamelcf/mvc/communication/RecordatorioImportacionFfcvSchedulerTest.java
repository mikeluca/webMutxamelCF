package com.mikedev.mutxamelcf.mvc.communication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import com.mikedev.mutxamelcf.service.ImportacionFfcvService;

class RecordatorioImportacionFfcvSchedulerTest {

    // Miércoles 14-10-2026: el fin de semana siguiente es el del sábado 17.
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 14);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 17);

    private ImportacionFfcvService importacionFfcvService;
    private ComunicacionesService comunicacionesService;
    private RecordatorioImportacionFfcvScheduler scheduler;

    @BeforeEach
    void setUp() {
        importacionFfcvService = mock(ImportacionFfcvService.class);
        comunicacionesService = mock(ComunicacionesService.class);
        scheduler = new RecordatorioImportacionFfcvScheduler(importacionFfcvService, comunicacionesService);
    }

    @Test
    void siNoSeHaImportadoElFinDeSemanaSiguienteEnviaElRecordatorio() {
        when(importacionFfcvService.hayImportacionParaFinDeSemana(SABADO)).thenReturn(false);

        scheduler.recordarImportacion(MIERCOLES);

        verify(comunicacionesService).enviarRecordatorioImportacionFfcv();
    }

    @Test
    void siYaSeHaImportadoNoEnviaNada() {
        when(importacionFfcvService.hayImportacionParaFinDeSemana(SABADO)).thenReturn(true);

        scheduler.recordarImportacion(MIERCOLES);

        verify(comunicacionesService, never()).enviarRecordatorioImportacionFfcv();
    }

    @Test
    void unFalloAlConsultarNoPropagaLaExcepcion() {
        when(importacionFfcvService.hayImportacionParaFinDeSemana(any())).thenThrow(new IllegalStateException("bd"));

        scheduler.recordarImportacion(MIERCOLES);

        verify(comunicacionesService, never()).enviarRecordatorioImportacionFfcv();
    }

    @Test
    void estaProgramadoLosMiercolesALas18EnLaZonaDeMadrid() throws Exception {
        Method metodo = RecordatorioImportacionFfcvScheduler.class.getMethod("recordarImportacion");
        Scheduled scheduled = metodo.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 18 * * WED");
        assertThat(scheduled.zone()).isEqualTo("Europe/Madrid");
    }

}
