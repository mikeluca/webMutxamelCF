package com.mikedev.mutxamelcf.mvc.communication;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.service.ImportacionFfcvService;

/**
 * Los miércoles a las 18:00 (hora de Madrid) avisa por email a la gestión
 * del club si todavía no se ha confirmado la importación de los partidos
 * del fin de semana siguiente. No accede a ninguna web externa: el CSV lo
 * descarga y sube el coordinador a mano.
 */
@Component
public class RecordatorioImportacionFfcvScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RecordatorioImportacionFfcvScheduler.class);

    static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private final ImportacionFfcvService importacionFfcvService;
    private final ComunicacionesService comunicacionesService;

    public RecordatorioImportacionFfcvScheduler(
            ImportacionFfcvService importacionFfcvService,
            ComunicacionesService comunicacionesService) {

        this.importacionFfcvService = importacionFfcvService;
        this.comunicacionesService = comunicacionesService;
    }

    @Scheduled(cron = "0 0 18 * * WED", zone = "Europe/Madrid")
    public void recordarImportacion() {
        recordarImportacion(LocalDate.now(ZONA));
    }

    void recordarImportacion(LocalDate hoy) {

        logger.info("Inicio recordatorio de importación de partidos FFCV");

        try {

            LocalDate sabado = hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));

            if (importacionFfcvService.hayImportacionParaFinDeSemana(sabado)) {
                logger.info("Fin recordatorio de importación FFCV: ya importada, finDeSemana={}", sabado);
                return;
            }

            boolean enviado = comunicacionesService.enviarRecordatorioImportacionFfcv();

            logger.info("Fin recordatorio de importación FFCV: finDeSemana={}, enviado={}", sabado, enviado);

        } catch (Exception e) {

            logger.error("Error en el recordatorio de importación FFCV: {}", e.getMessage(), e);
        }
    }

}
