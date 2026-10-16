package com.mikedev.mutxamelcf.serviceimpl;

import java.text.SimpleDateFormat;
import java.time.Clock;
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
 * Job que, cada 15 minutos, recuerda al cuerpo técnico de un equipo que
 * todavía no ha introducido el resultado de un partido que empezó hace al
 * menos 2 horas (así el aviso llega ~2 h después del inicio).
 *
 * <ul>
 * <li>Solo se avisa si no hay resultado por ninguna vía (ni goles ni texto
 * de resultado) y el partido no está cancelado.</li>
 * <li>Solo se avisa de partidos que empezaron hace menos de 3 días: los más
 * antiguos sin aviso se marcan como avisados sin enviar nada, para que una
 * importación o un alta tardía no dispare avisos de hace semanas.</li>
 * <li>El aviso es único por partido: se marca AVISO_RESULTADO_ENVIADO = 1
 * se hayan encontrado o no entrenadores a los que avisar.</li>
 * <li>Las horas de los partidos son hora de España: el job usa un Clock en
 * Europe/Madrid.</li>
 * </ul>
 */
@Component
public class RecordatorioResultadoPartidoScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RecordatorioResultadoPartidoScheduler.class);

    static final int HORAS_HASTA_EL_AVISO = 2;
    static final int DIAS_MAXIMOS_DE_ANTIGUEDAD = 3;

    private final PartidoDao partidoDao;
    private final EquipoGestionDao equipoGestionDao;
    private final ComunicacionService comunicacionService;
    private final Clock clock;

    public RecordatorioResultadoPartidoScheduler(
            PartidoDao partidoDao,
            EquipoGestionDao equipoGestionDao,
            ComunicacionService comunicacionService,
            Clock clock) {

        this.partidoDao = partidoDao;
        this.equipoGestionDao = equipoGestionDao;
        this.comunicacionService = comunicacionService;
        this.clock = clock;
    }

    /**
     * Cada 15 minutos, en hora de Madrid.
     */
    @Scheduled(cron = "0 */15 * * * *", zone = "Europe/Madrid")
    @Transactional
    public void enviarRecordatoriosDeResultadoPendiente() {

        logger.info("Inicio job de recordatorio de resultado de partido pendiente");

        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime masAntiguoAvisable = ahora.minusDays(DIAS_MAXIMOS_DE_ANTIGUEDAD);

        // Una sola UPDATE: lo más antiguo sin resultado ni aviso se da por avisado.
        partidoDao.marcarAvisosAntiguosComoEnviados(masAntiguoAvisable);

        List<Partido> pendientes = partidoDao.obtenerPendientesDeAvisoResultado(
                masAntiguoAvisable,
                ahora.minusHours(HORAS_HASTA_EL_AVISO));

        for (Partido partido : pendientes) {
            procesarPartidoPendiente(partido);
        }

        logger.info("Fin job de recordatorio de resultado de partido pendiente: total={}", pendientes.size());
    }

    private void procesarPartidoPendiente(Partido partido) {

        try {

            /*
             * Se vuelve a leer justo antes de enviar: el resultado pudo
             * rellenarse (o el partido cancelarse) entre la consulta y el
             * envío.
             */
            Partido actual = partidoDao.obtenerPorId(partido.getId());

            if (actual == null || actual.isCancelado() || actual.isAvisoResultadoEnviado()
                    || tieneResultado(actual)) {
                return;
            }

            enviarAviso(actual);

        } finally {

            /*
             * Aviso único: se marca como enviado aunque no se haya
             * encontrado ningún destinatario, para no volver a
             * intentarlo en la siguiente ejecución del job. Si el partido
             * ya tenía resultado, la consulta tampoco lo vuelve a traer.
             */
            partidoDao.marcarAvisoResultadoEnviado(partido.getId());
        }
    }

    private void enviarAviso(Partido partido) {

        List<Long> entrenadores = equipoGestionDao.obtenerEntrenadoresPorEquipo(partido.getEquipoId());

        Long usuarioAutorId = obtenerUsuarioAutor(partido, entrenadores);

        if (entrenadores.isEmpty() || usuarioAutorId == null) {
            logger.warn(
                    "No se ha podido enviar el recordatorio de resultado del partido {}: entrenadores={}, usuarioAutorId={}",
                    partido.getId(), entrenadores.size(), usuarioAutorId);
            return;
        }

        Comunicacion comunicacion = new Comunicacion();

        comunicacion.setTitulo("Resultado pendiente");

        comunicacion.setContenido(
                "Introduce el resultado del partido "
                        + nombreEquipo(partido)
                        + " - "
                        + partido.getRival()
                        + " del día "
                        + formatearFecha(partido)
                        + " (goles del Mutxamel - goles del rival).");

        comunicacionService.crearPrivada(comunicacion, entrenadores, usuarioAutorId);
    }

    /*
     * Autor del mensaje (crearPrivada exige un remitente): el primer
     * coordinador que NO sea destinatario, para que el aviso no le llegue
     * "de sí mismo"; si no, el primer ADMIN_APP que tampoco lo sea; y solo
     * como último recurso el último usuario que actualizó el partido.
     */
    private Long obtenerUsuarioAutor(Partido partido, List<Long> destinatarios) {

        for (Long coordinador : equipoGestionDao.obtenerCoordinadores()) {
            if (!destinatarios.contains(coordinador)) {
                return coordinador;
            }
        }

        for (Long administrador : equipoGestionDao.obtenerAdministradoresApp()) {
            if (!destinatarios.contains(administrador)) {
                return administrador;
            }
        }

        return partido.getUsuarioActualizoId();
    }

    private String nombreEquipo(Partido partido) {

        String nombre = equipoGestionDao.obtenerNombreEquipo(partido.getEquipoId());

        return nombre == null || nombre.isBlank() ? "Mutxamel" : nombre.trim();
    }

    /** Resultado puesto por cualquier vía; " - " o "-" cuentan como vacío. */
    private static boolean tieneResultado(Partido partido) {

        if (partido.getGolesFavor() != null) {
            return true;
        }

        String resultado = partido.getResultado();

        return resultado != null && !resultado.replace("-", "").isBlank();
    }

    private static String formatearFecha(Partido partido) {

        if (partido.getDia() == null) {
            return "";
        }

        return new SimpleDateFormat("dd/MM/yyyy").format(partido.getDia());
    }
}
