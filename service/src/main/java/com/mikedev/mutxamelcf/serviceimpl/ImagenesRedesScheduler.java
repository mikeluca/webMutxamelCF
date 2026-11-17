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
 * Genera las imágenes para redes sociales: la PREVIA los viernes a las 12:00 (partidos de ese fin de semana) y los
 * RESULTADOS los lunes a las 09:00 (los del fin de semana que acaba de terminar), hora de Madrid. Al terminar avisa a
 * coordinación (bandeja + push).
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
    public void generarPrevia() {
        generarPrevia(LocalDate.now(clock));
    }

    @Scheduled(cron = "0 0 9 * * MON", zone = "Europe/Madrid")
    public void generarResultados() {
        generarResultados(LocalDate.now(clock));
    }

    /** La previa del fin de semana que empieza el viernes de {@code hoy}. */
    void generarPrevia(LocalDate hoy) {
        generar(ImagenesRedesService.TIPO_PREVIA, hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)));
    }

    /** Los resultados del fin de semana anterior: su viernes es el anterior a {@code hoy}. */
    void generarResultados(LocalDate hoy) {
        generar(ImagenesRedesService.TIPO_RESULTADOS, hoy.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY)));
    }

    private void generar(String tipo, LocalDate viernes) {

        logger.info("Inicio generación de imágenes para redes {} del fin de semana {}", tipo, viernes);

        try {

            int generadas = imagenesRedesService.generarYAvisar(tipo, viernes);

            logger.info("Fin generación de imágenes para redes {}: fin de semana={}, imágenes={}", tipo, viernes,
                    generadas);

        } catch (Exception e) {

            logger.error("Error al generar las imágenes para redes {}: {}", tipo, e.getMessage(), e);
        }
    }
}
