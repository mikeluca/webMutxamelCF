package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.service.ImagenesRedesService;

/**
 * Las imágenes de la previa y de los resultados ya NO se generan solas: las genera Retransmisión (o un administrador
 * de la app) a mano. Este scheduler solo recuerda hacerlo: la PREVIA los viernes a las 12:00 (partidos del fin de
 * semana que empieza ese viernes) y los RESULTADOS los lunes a las 09:00 (los del fin de semana anterior), hora de
 * Madrid. Si ya están generadas no avisa a nadie.
 */
@Component
public class ImagenesRedesScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ImagenesRedesScheduler.class);

    private final ImagenesRedesService imagenesRedesService;
    private final Clock clock;

    public ImagenesRedesScheduler(ImagenesRedesService imagenesRedesService, Clock clock) {
        this.imagenesRedesService = imagenesRedesService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 12 * * FRI", zone = "Europe/Madrid")
    public void recordarPrevia() {
        recordarPrevia(LocalDate.now(clock));
    }

    @Scheduled(cron = "0 0 9 * * MON", zone = "Europe/Madrid")
    public void recordarResultados() {
        recordarResultados(LocalDate.now(clock));
    }

    /** La previa del fin de semana que empieza el viernes de {@code hoy}. */
    void recordarPrevia(LocalDate hoy) {
        recordar(ImagenesRedesService.TIPO_PREVIA, hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)));
    }

    /** Los resultados del fin de semana anterior: su viernes es el anterior a {@code hoy}. */
    void recordarResultados(LocalDate hoy) {
        recordar(ImagenesRedesService.TIPO_RESULTADOS, hoy.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY)));
    }

    private void recordar(String tipo, LocalDate viernes) {

        logger.info("Inicio recordatorio de imágenes para redes {} del fin de semana {}", tipo, viernes);

        try {

            int avisadas = imagenesRedesService.recordarSiPendiente(tipo, viernes);

            logger.info("Fin recordatorio de imágenes para redes {}: fin de semana={}, cuentas avisadas={}", tipo,
                    viernes, avisadas);

        } catch (Exception e) {

            logger.error("Error al recordar las imágenes para redes {}: {}", tipo, e.getMessage(), e);
        }
    }
}
