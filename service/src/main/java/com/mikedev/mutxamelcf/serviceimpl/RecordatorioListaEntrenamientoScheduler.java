package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mikedev.mutxamelcf.dao.EntrenamientoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Entrenamiento;
import com.mikedev.mutxamelcf.model.EntrenamientoPendienteDeLista;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.service.ComunicacionService;

/**
 * Job que, cada 15 minutos, recuerda a los entrenadores de un equipo que
 * todavía no han pasado lista de un entrenamiento que empezó hace al menos 2
 * horas.
 *
 * <ul>
 * <li>Solo cuenta si la lista no está revisada (LISTA_REVISADA = 0: el staff no
 * ha guardado la asistencia) y no se ha avisado ya (AVISO_LISTA_ENVIADO = 0).</li>
 * <li>No se avisa de una sesión cancelada; se marca como avisada sin enviar.</li>
 * <li>Solo se avisa de entrenamientos de los últimos 2 días: los más antiguos se
 * marcan como avisados sin enviar, para que un alta tardía no dispare avisos de
 * hace días.</li>
 * <li>La hora de la sesión se lee con {@link HoraTexto}; si no se puede leer, la
 * referencia es las 21:00.</li>
 * <li>Aviso único por sesión. Cada entrenamiento va en su propio try/catch (y sin
 * una transacción común) para que uno que falle no pare a los demás.</li>
 * <li>Horas de España: el job usa un Clock en Europe/Madrid.</li>
 * </ul>
 */
@Component
public class RecordatorioListaEntrenamientoScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RecordatorioListaEntrenamientoScheduler.class);

    static final int HORAS_HASTA_EL_AVISO = 2;
    static final int DIAS_MAXIMOS_DE_ANTIGUEDAD = 2;
    static final LocalTime HORA_POR_DEFECTO = LocalTime.of(21, 0);

    /** Valor de data["tipo"] del push que lleva a pasar lista. */
    static final String TIPO_PUSH_PASAR_LISTA = "PASAR_LISTA";

    private static final String ESTADO_CANCELADA = "CANCELADA";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final EntrenamientoDao entrenamientoDao;
    private final EquipoGestionDao equipoGestionDao;
    private final ComunicacionService comunicacionService;
    private final Clock clock;

    public RecordatorioListaEntrenamientoScheduler(
            EntrenamientoDao entrenamientoDao,
            EquipoGestionDao equipoGestionDao,
            ComunicacionService comunicacionService,
            Clock clock) {

        this.entrenamientoDao = entrenamientoDao;
        this.equipoGestionDao = equipoGestionDao;
        this.comunicacionService = comunicacionService;
        this.clock = clock;
    }

    /** Cada 15 minutos, en hora de Madrid. */
    @Scheduled(cron = "0 */15 * * * *", zone = "Europe/Madrid")
    public void enviarRecordatoriosDeListaPendiente() {

        logger.info("Inicio job de recordatorio de pasar lista");

        LocalDateTime ahora = LocalDateTime.now(clock);

        List<EntrenamientoPendienteDeLista> pendientes = entrenamientoDao.obtenerPendientesDeAvisoLista(
                ahora.toLocalDate());

        int tratados = 0;

        for (EntrenamientoPendienteDeLista pendiente : pendientes) {

            try {

                if (procesar(pendiente, ahora)) {
                    tratados++;
                }

            } catch (Exception e) {

                // Uno que falla no para a los demás; se reintenta en la siguiente pasada.
                logger.error("Fallo en el recordatorio de pasar lista del entrenamiento {}: {}",
                        pendiente.id(), e.getMessage(), e);
            }
        }

        logger.info("Fin job de recordatorio de pasar lista: candidatos={}, tratados={}", pendientes.size(), tratados);
    }

    /** true si el entrenamiento se ha tratado (avisado o descartado). */
    private boolean procesar(EntrenamientoPendienteDeLista pendiente, LocalDateTime ahora) {

        LocalTime hora = HoraTexto.leer(pendiente.hora());
        LocalDateTime inicio = pendiente.fecha().atTime(hora != null ? hora : HORA_POR_DEFECTO);

        // Todavía no han pasado las 2 horas.
        if (inicio.plusHours(HORAS_HASTA_EL_AVISO).isAfter(ahora)) {
            return false;
        }

        // Sesión cancelada: no hay lista que pasar.
        if (ESTADO_CANCELADA.equals(pendiente.estadoSesion())) {
            entrenamientoDao.marcarAvisoListaEnviado(pendiente.id());
            return true;
        }

        // Fuera de la ventana: se da por avisado sin enviar nada.
        if (inicio.isBefore(ahora.minusDays(DIAS_MAXIMOS_DE_ANTIGUEDAD))) {
            entrenamientoDao.marcarAvisoListaEnviado(pendiente.id());
            return true;
        }

        // Se vuelve a leer justo antes de enviar: la lista pudo revisarse entre medias.
        Entrenamiento actual = entrenamientoDao.obtenerPorId(pendiente.id());

        if (actual == null || actual.isListaRevisada() || actual.isAvisoListaEnviado()) {
            return false;
        }

        enviarAviso(pendiente, actual, hora != null ? hora : HORA_POR_DEFECTO, ahora.toLocalDate());

        entrenamientoDao.marcarAvisoListaEnviado(pendiente.id());

        return true;
    }

    private void enviarAviso(
            EntrenamientoPendienteDeLista pendiente, Entrenamiento entrenamiento, LocalTime hora, LocalDate hoy) {

        List<Long> entrenadores = equipoGestionDao.obtenerEntrenadoresPorEquipo(pendiente.equipoId());

        Long autor = obtenerUsuarioAutor(entrenamiento, entrenadores);

        if (entrenadores.isEmpty() || autor == null) {
            logger.warn("No se ha podido enviar el recordatorio de pasar lista del entrenamiento {}: "
                    + "entrenadores={}, autor={}", pendiente.id(), entrenadores.size(), autor);
            return;
        }

        String equipo = equipoGestionDao.obtenerNombreEquipo(pendiente.equipoId());
        String cuando = pendiente.fecha().equals(hoy) ? "de hoy" : "del " + DIA.format(pendiente.fecha());

        Comunicacion comunicacion = new Comunicacion();

        comunicacion.setTitulo("Pasar lista");
        comunicacion.setContenido("¿Has pasado lista del entrenamiento " + cuando + " (" + HORA.format(hora)
                + ")? Revisa la asistencia de " + (equipo == null || equipo.isBlank() ? "tu equipo" : equipo.trim())
                + ".");

        comunicacionService.crearPrivada(comunicacion, entrenadores, autor,
                new OpcionesPushComunicacion(null, Map.of(
                        "tipo", TIPO_PUSH_PASAR_LISTA,
                        "entrenamientoId", String.valueOf(pendiente.id()))));
    }

    /*
     * Remitente (crearPrivada exige uno): el mismo criterio que
     * RecordatorioResultadoPartidoScheduler. El primer coordinador que NO sea
     * destinatario, para que el aviso no le llegue "de sí mismo"; si no, el primer
     * ADMIN_APP que tampoco lo sea; y solo como último recurso el entrenador que
     * creó el entrenamiento.
     */
    private Long obtenerUsuarioAutor(Entrenamiento entrenamiento, List<Long> destinatarios) {

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

        return entrenamiento.getUsuarioEntrenadorId();
    }
}
