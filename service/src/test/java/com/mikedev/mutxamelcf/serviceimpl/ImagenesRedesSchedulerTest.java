package com.mikedev.mutxamelcf.serviceimpl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

import com.mikedev.mutxamelcf.service.ImagenesRedesService;

class ImagenesRedesSchedulerTest {

    private final ImagenesRedesService servicio = mock(ImagenesRedesService.class);
    private final ImagenesRedesScheduler scheduler = new ImagenesRedesScheduler(servicio,
            Clock.system(ZoneId.of("Europe/Madrid")));

    @Test
    void laPreviaDelViernesEsDeEseMismoFinDeSemana() {

        // Viernes 13/11/2026
        scheduler.generarPrevia(LocalDate.of(2026, 11, 13));

        verify(servicio).generarYAvisar("PREVIA", LocalDate.of(2026, 11, 13));
    }

    @Test
    void losResultadosDelLunesSonDelViernesAnterior() {

        // Lunes 16/11/2026 -> fin de semana 13-15/11
        scheduler.generarResultados(LocalDate.of(2026, 11, 16));

        verify(servicio).generarYAvisar("RESULTADOS", LocalDate.of(2026, 11, 13));
    }

    @Test
    void lasHorasDeLosCronEnHoraDeMadrid() throws Exception {

        Scheduled previa = anotacion("generarPrevia");
        Scheduled resultados = anotacion("generarResultados");

        assertThat(previa.cron()).isEqualTo("0 0 12 * * FRI");
        assertThat(previa.zone()).isEqualTo("Europe/Madrid");
        assertThat(resultados.cron()).isEqualTo("0 0 9 * * MON");
        assertThat(resultados.zone()).isEqualTo("Europe/Madrid");
    }

    private static Scheduled anotacion(String metodo) throws Exception {
        Method m = ImagenesRedesScheduler.class.getMethod(metodo);
        return m.getAnnotation(Scheduled.class);
    }
}
