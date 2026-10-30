package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.service.InvitadosService;

/**
 * Job que, cada 15 minutos, avisa a coordinación de los jugadores invitados que llevan más de 12 horas pendientes de
 * que su entrenador los libere (docs/convocatoria-invitados.md, §2.3). El aviso es único por invitación
 * (AVISO_12H_ENVIADO).
 */
@Component
public class InvitacionesPlazoScheduler {

    private static final Logger logger = LoggerFactory.getLogger(InvitacionesPlazoScheduler.class);

    private final InvitadosService invitadosService;

    public InvitacionesPlazoScheduler(InvitadosService invitadosService) {
        this.invitadosService = invitadosService;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "Europe/Madrid")
    public void avisarPlazosVencidos() {

        try {
            invitadosService.avisarPlazosVencidos();

        } catch (RuntimeException e) {
            // Un fallo no debe parar el job: se reintenta en la siguiente pasada.
            logger.error("Error al avisar de las invitaciones con el plazo de 12 horas vencido: {}", e.getMessage(), e);
        }
    }
}
