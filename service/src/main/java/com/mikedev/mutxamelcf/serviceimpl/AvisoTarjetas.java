package com.mikedev.mutxamelcf.serviceimpl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.ResumenTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.ComunicacionService;

/**
 * El aviso de tarjetas al cuerpo técnico: se dispara tanto al guardar las estadísticas del partido como al guardar
 * las del final de una retransmisión, con exactamente las mismas reglas.
 */
@Component
class AvisoTarjetas {

    private static final Logger logger = LoggerFactory.getLogger(AvisoTarjetas.class);

    /** Lo que un guardado deja a un jugador: si ve la roja en el partido (las amarillas se comparan por acumulado). */
    record TarjetasJugador(Long jugadorId, boolean roja) {
    }

    private final TemporadaDao temporadaDao;
    private final ResumenTemporadaDao resumenTemporadaDao;
    private final EquipoGestionDao equipoGestionDao;
    private final ComunicacionService comunicacionService;
    private final ReglaSancionAmarillas reglaSancion;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final TextosAvisos textos;
    private final IdiomasUsuarios idiomas;

    AvisoTarjetas(
            TemporadaDao temporadaDao,
            ResumenTemporadaDao resumenTemporadaDao,
            EquipoGestionDao equipoGestionDao,
            ComunicacionService comunicacionService,
            ReglaSancionAmarillas reglaSancion,
            PlatformTransactionManager transactionManager,
            Clock clock,
            TextosAvisos textos,
            IdiomasUsuarios idiomas) {

        this.temporadaDao = temporadaDao;
        this.resumenTemporadaDao = resumenTemporadaDao;
        this.equipoGestionDao = equipoGestionDao;
        this.comunicacionService = comunicacionService;
        this.reglaSancion = reglaSancion;
        this.transactionManager = transactionManager;
        this.clock = clock;
        this.textos = textos;
        this.idiomas = idiomas;
    }

    /*
     * Amarillas acumuladas por jugador del equipo en la temporada activa (partidos
     * no cancelados). Sin temporada activa, nadie acumula nada.
     */
    Map<Long, Integer> amarillasAcumuladas(Partido partido) {

        Temporada temporada = temporadaDao.obtenerTemporadaActiva();

        Map<Long, Integer> amarillas = new HashMap<>();

        if (temporada == null) {
            return amarillas;
        }

        LocalDate hoy = LocalDate.now(clock);
        LocalDate desde = temporada.getFechaInicio() != null ? aLocalDate(temporada.getFechaInicio()) : LocalDate.of(1900, 1, 1);
        LocalDate hasta = temporada.getFechaFin() != null ? aLocalDate(temporada.getFechaFin()) : hoy.plusYears(1);

        for (EstadisticasJugadorEquipo jugador : resumenTemporadaDao
                .estadisticasJugadoresDelEquipo(partido.getEquipoId(), desde, hasta, hoy)) {

            amarillas.put(jugador.jugadorId(), jugador.amarillas());
        }

        return amarillas;
    }

    private static LocalDate aLocalDate(Date fecha) {

        if (fecha instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        return fecha.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    /*
     * Un único aviso por jugador y guardado, al cuerpo técnico del equipo, si el
     * guardado hace cruzar un umbral (posible sanción, o una amarilla antes de ella)
     * o el jugador ve la roja en este partido (y no la tenía ya). Volver a guardar
     * sin nada nuevo no avisa. Si coinciden varias cosas, manda la más grave:
     * posible sanción por amarillas, roja, y por último "a una".
     *
     * Solo se avisa al llegar a un umbral MAYOR que el que ya había antes de este
     * guardado (se compara el acumulado de antes y de después; si "después" no
     * supera a "antes", no hay aviso), y la roja solo si en este partido pasa de 0
     * a 1. Limitación: sin histórico de avisos no se puede saber qué umbrales se
     * avisaron en guardados anteriores, así que bajar las amarillas en un guardado
     * y volver a subirlas en otro puede avisar otra vez.
     */
    void avisar(
            Partido partido,
            List<TarjetasJugador> jugadores,
            Map<Long, Integer> antes,
            Map<Long, Integer> despues,
            Map<Long, Boolean> rojaAntes,
            Long usuarioAppId) {

        // Una línea por jugador, que se redacta en el idioma de cada destinatario.
        List<Function<String, String>> avisos = new ArrayList<>();

        for (TarjetasJugador jugador : jugadores) {

            Long id = jugador.jugadorId();

            int amarillasAntes = antes.getOrDefault(id, 0);
            int amarillasDespues = despues.getOrDefault(id, 0);

            int umbralSancion = reglaSancion.umbralSancionCruzado(amarillasAntes, amarillasDespues);
            boolean rojaNueva = jugador.roja() && !rojaAntes.getOrDefault(id, false);
            boolean aUna = reglaSancion.cruzaAvisoPrevio(amarillasAntes, amarillasDespues);

            if (umbralSancion == 0 && !rojaNueva && !aUna) {
                continue;
            }

            String nombre = equipoGestionDao.obtenerNombreJugador(id);

            if (umbralSancion > 0) {
                avisos.add(idioma -> textos.texto(idioma, "tarjetas.sancion",
                        "jugador", nombre, "n", String.valueOf(umbralSancion)));
            } else if (rojaNueva) {
                avisos.add(idioma -> textos.texto(idioma, "tarjetas.roja",
                        "jugador", nombre, "rival", partido.getRival()));
            } else {
                avisos.add(idioma -> textos.texto(idioma, "tarjetas.aUna",
                        "jugador", nombre, "n", String.valueOf(amarillasDespues)));
            }
        }

        if (avisos.isEmpty()) {
            return;
        }

        List<Long> destinatarios = new ArrayList<>(
                equipoGestionDao.obtenerEntrenadoresPorEquipo(partido.getEquipoId()));

        // Sin ningún entrenador con cuenta, los coordinadores.
        if (destinatarios.isEmpty()) {
            destinatarios.addAll(equipoGestionDao.obtenerCoordinadores());
        }

        // Quien guarda ya lo sabe: no se avisa a sí mismo.
        destinatarios.remove(usuarioAppId);

        if (destinatarios.isEmpty()) {
            logger.warn("Aviso de tarjetas sin destinatarios: el equipo {} no tiene entrenadores"
                    + " (ni coordinadores distintos de quien guarda)", partido.getEquipoId());
            return;
        }

        // Una sola comunicación por guardado e idioma, con una línea por jugador.
        enviarTrasElCommit(avisos, destinatarios, usuarioAppId);
    }

    /*
     * El aviso se calcula dentro de la transacción del guardado pero se ENVÍA
     * cuando esta ya se ha confirmado: un fallo al avisar nunca deshace el
     * guardado. Crear la comunicación escribe en la base de datos, y en
     * afterCommit la transacción ya está cerrada, así que se hace en una
     * transacción nueva (REQUIRES_NEW).
     */
    private void enviarTrasElCommit(List<Function<String, String>> avisos, List<Long> destinatarios,
            Long usuarioAppId) {

        Runnable envio = () -> {
            try {

                TransactionTemplate plantilla = new TransactionTemplate(transactionManager);
                plantilla.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                plantilla.executeWithoutResult(estado -> idiomas.crearPrivadaPorIdioma(
                        comunicacionService, destinatarios, usuarioAppId, null,
                        idioma -> new TextoAviso(
                                textos.texto(idioma, "tarjetas.titulo"),
                                avisos.stream().map(aviso -> aviso.apply(idioma)).collect(Collectors.joining("\n")))));

            } catch (Exception e) {

                logger.error("No se ha podido enviar el aviso de tarjetas a {} destinatarios: {}",
                        destinatarios.size(), e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
            return;
        }

        envio.run();
    }
}
