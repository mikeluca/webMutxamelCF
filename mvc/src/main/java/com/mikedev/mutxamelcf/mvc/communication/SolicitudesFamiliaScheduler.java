package com.mikedev.mutxamelcf.mvc.communication;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Caducada;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;

/**
 * Las tareas diarias de las solicitudes de acceso de las familias (hora de Madrid):
 * <ul>
 * <li>04:00, caducidad: las solicitudes sin revisar o rechazadas de más de 7 días se borran (datos del familiar y de
 * los hijos escritos), se elimina la cuenta que creó el registro si no tiene roles ni vínculos y se avisa por email al
 * familiar de que puede volver a pedirla. Las cuentas que ya tenían roles no se tocan nunca.</li>
 * <li>10:00, recordatorio a coordinación si hay solicitudes pendientes de más de 2 días.</li>
 * </ul>
 * Un fallo no para el job: se reintenta al día siguiente.
 */
@Component
public class SolicitudesFamiliaScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SolicitudesFamiliaScheduler.class);

    private final SolicitudFamiliaService solicitudFamiliaService;
    private final ComunicacionesService comunicacionesService;

    public SolicitudesFamiliaScheduler(SolicitudFamiliaService solicitudFamiliaService,
            ComunicacionesService comunicacionesService) {

        this.solicitudFamiliaService = solicitudFamiliaService;
        this.comunicacionesService = comunicacionesService;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Europe/Madrid")
    public void caducarSolicitudes() {

        logger.info("Inicio caducidad de solicitudes de familia");

        try {

            List<Caducada> caducadas = solicitudFamiliaService.caducarVencidas();
            int avisadas = 0;

            for (Caducada c : caducadas) {

                try {
                    if (comunicacionesService.enviarSolicitudFamiliaCaducada(c.email(), c.nombre(), c.idioma())) {
                        avisadas++;
                    }
                } catch (RuntimeException e) {
                    // Privacidad: ni el email ni el nombre en el log.
                    logger.warn("No se ha podido avisar por email de una solicitud de familia caducada: {}",
                            e.getMessage());
                }
            }

            logger.info("Fin caducidad de solicitudes de familia: caducadas={}, avisadasPorEmail={}", caducadas.size(),
                    avisadas);

        } catch (RuntimeException e) {
            logger.error("Ha fallado la caducidad de solicitudes de familia: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Europe/Madrid")
    public void recordarPendientes() {

        try {

            int avisadas = solicitudFamiliaService.recordarPendientes();

            logger.info("Recordatorio de solicitudes de familia pendientes: cuentasAvisadas={}", avisadas);

        } catch (RuntimeException e) {
            logger.error("Ha fallado el recordatorio de solicitudes de familia: {}", e.getMessage(), e);
        }
    }
}
