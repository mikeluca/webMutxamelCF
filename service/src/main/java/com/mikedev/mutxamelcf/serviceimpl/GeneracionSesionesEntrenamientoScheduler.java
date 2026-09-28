package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.service.SesionEntrenamientoService;

/**
 * Job mensual que mantiene la ventana de generación de sesiones de
 * entrenamiento (hoy + 2 meses) para todos los horarios activos de
 * todos los equipos, aunque nadie vuelva a editar el horario.
 *
 * La generación "al vuelo" al crear/editar un horario
 * (HorarioEntrenamientoServiceImpl) ya cubre el caso normal; este job
 * es la red de seguridad para que la ventana nunca se quede corta con
 * el simple paso del tiempo.
 */
@Component
public class GeneracionSesionesEntrenamientoScheduler {

    private static final Logger logger = LoggerFactory.getLogger(GeneracionSesionesEntrenamientoScheduler.class);

    private final SesionEntrenamientoService sesionEntrenamientoService;

    public GeneracionSesionesEntrenamientoScheduler(SesionEntrenamientoService sesionEntrenamientoService) {
        this.sesionEntrenamientoService = sesionEntrenamientoService;
    }

    /**
     * A las 3:00 del día 1 de cada mes.
     */
    @Scheduled(cron = "0 0 3 1 * ?")
    public void generarSesionesMensualmente() {

        logger.info("Inicio job mensual de generación de sesiones de entrenamiento");

        sesionEntrenamientoService.generarSesionesParaTodosLosHorarios();

        logger.info("Fin job mensual de generación de sesiones de entrenamiento");
    }

}
