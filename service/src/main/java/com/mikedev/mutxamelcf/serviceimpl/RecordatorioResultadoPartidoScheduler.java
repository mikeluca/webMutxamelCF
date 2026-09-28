package com.mikedev.mutxamelcf.serviceimpl;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;

/**
 * Job que, cada 2 horas, recuerda al cuerpo técnico de un equipo que
 * todavía no ha introducido el resultado de un partido ya jugado (al
 * menos 2 horas atrás). El aviso es único por partido: en cuanto se
 * procesa un partido pendiente se marca AVISO_RESULTADO_ENVIADO = 1
 * (PartidoDao.marcarAvisoResultadoEnviado), se hayan encontrado o no
 * entrenadores a los que avisar, para no reintentarlo indefinidamente.
 */
@Component
public class RecordatorioResultadoPartidoScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RecordatorioResultadoPartidoScheduler.class);

    private final PartidoDao partidoDao;
    private final EquipoGestionDao equipoGestionDao;
    private final ComunicacionService comunicacionService;

    public RecordatorioResultadoPartidoScheduler(
            PartidoDao partidoDao,
            EquipoGestionDao equipoGestionDao,
            ComunicacionService comunicacionService) {

        this.partidoDao = partidoDao;
        this.equipoGestionDao = equipoGestionDao;
        this.comunicacionService = comunicacionService;
    }

    /**
     * Cada 2 horas en punto (00:00, 02:00, 04:00, ... 22:00).
     */
    @Scheduled(cron = "0 0 0/2 * * ?")
    @Transactional
    public void enviarRecordatoriosDeResultadoPendiente() {

        logger.info("Inicio job de recordatorio de resultado de partido pendiente");

        LocalDateTime limite = LocalDateTime.now().minusHours(2);

        List<Partido> pendientes = partidoDao.obtenerPendientesDeAvisoResultado(limite);

        for (Partido partido : pendientes) {
            procesarPartidoPendiente(partido);
        }

        logger.info("Fin job de recordatorio de resultado de partido pendiente: total={}", pendientes.size());
    }

    private void procesarPartidoPendiente(Partido partido) {

        try {

            List<Long> entrenadores = equipoGestionDao.obtenerEntrenadoresPorEquipo(
                    partido.getEquipoId());

            /*
             * Necesitamos un usuarioId "autor" para poder reutilizar
             * ComunicacionService.crearPrivada (exige un remitente
             * autenticado). Como este job no lo dispara ningún usuario,
             * usamos el último usuario que actualizó el partido y, si no
             * hay ninguno, el primer coordinador disponible. Si ninguno
             * de los dos existe, no se puede enviar el aviso (se marca
             * igualmente como enviado para no reintentar indefinidamente).
             */
            Long usuarioAutorId = obtenerUsuarioAutor(partido);

            if (!entrenadores.isEmpty() && usuarioAutorId != null) {

                Comunicacion comunicacion = new Comunicacion();

                comunicacion.setTitulo("Resultado pendiente");

                comunicacion.setContenido(
                        "Introduce el resultado del partido contra "
                                + partido.getRival()
                                + " del día "
                                + formatearFecha(partido)
                                + ".");

                comunicacionService.crearPrivada(
                        comunicacion,
                        entrenadores,
                        usuarioAutorId);
            } else {
                logger.warn(
                        "No se ha podido enviar el recordatorio de resultado del partido {}: entrenadores={}, usuarioAutorId={}",
                        partido.getId(), entrenadores.size(), usuarioAutorId);
            }

        } finally {

            /*
             * Aviso único: se marca como enviado aunque no se haya
             * encontrado ningún destinatario, para no volver a
             * intentarlo en la siguiente ejecución del job.
             */
            partidoDao.marcarAvisoResultadoEnviado(partido.getId());
        }
    }

    private Long obtenerUsuarioAutor(Partido partido) {

        if (partido.getUsuarioActualizoId() != null) {
            return partido.getUsuarioActualizoId();
        }

        List<Long> coordinadores = equipoGestionDao.obtenerCoordinadores();

        return coordinadores.isEmpty() ? null : coordinadores.get(0);
    }

    private static String formatearFecha(Partido partido) {

        if (partido.getDia() == null) {
            return "";
        }

        return new SimpleDateFormat("dd/MM/yyyy").format(partido.getDia());
    }
}
