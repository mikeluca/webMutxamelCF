package com.mikedev.mutxamelcf.serviceimpl;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugador;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugadorRequest;
import com.mikedev.mutxamelcf.model.PartidoEstadisticaJugadorResponse;
import com.mikedev.mutxamelcf.model.PartidoEstadisticasGuardarRequest;
import com.mikedev.mutxamelcf.model.PartidoEstadisticasResponse;
import com.mikedev.mutxamelcf.service.PartidoEstadisticaService;

@Service
public class PartidoEstadisticaServiceImpl
        implements PartidoEstadisticaService {

    private final PartidoDao partidoDao;
    private final PartidoEstadisticaJugadorDao estadisticaJugadorDao;
    private final EquipoGestionDao equipoGestionDao;
    private final ConvocatoriaDao convocatoriaDao;
    private final ConvocatoriaJugadorDao convocatoriaJugadorDao;

    public PartidoEstadisticaServiceImpl(
            PartidoDao partidoDao,
            PartidoEstadisticaJugadorDao estadisticaJugadorDao,
            EquipoGestionDao equipoGestionDao,
            ConvocatoriaDao convocatoriaDao,
            ConvocatoriaJugadorDao convocatoriaJugadorDao) {

        this.partidoDao = partidoDao;
        this.estadisticaJugadorDao = estadisticaJugadorDao;
        this.equipoGestionDao = equipoGestionDao;
        this.convocatoriaDao = convocatoriaDao;
        this.convocatoriaJugadorDao = convocatoriaJugadorDao;
    }

    @Override
    @Transactional
    public PartidoEstadisticasResponse guardar(
            Long usuarioAppId,
            Long partidoId,
            PartidoEstadisticasGuardarRequest request) {

        if (usuarioAppId == null) {
            throw new SecurityException(
                    "Usuario no autenticado");
        }

        if (partidoId == null) {
            throw new IllegalArgumentException(
                    "El ID del partido es obligatorio");
        }

        validarRequest(request);

        Partido partido = partidoDao.obtenerPorId(partidoId);

        if (partido == null) {
            throw new IllegalArgumentException(
                    "El partido no existe");
        }

        /*
         * Mismo control de permisos que el resto de operaciones "App"
         * sobre partidos.
         */
        if (!equipoGestionDao.puedeGestionarEquipo(
                usuarioAppId,
                partido.getEquipoId())) {

            throw new SecurityException(
                    "El usuario no puede gestionar este partido");
        }

        /*
         * No se puede introducir el resultado de un partido que todavía
         * no se ha jugado.
         */
        LocalDateTime fechaHoraPartido = combinarFechaHora(
                partido.getDia(),
                partido.getHora());

        if (fechaHoraPartido != null
                && fechaHoraPartido.isAfter(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "No se puede introducir el resultado de un partido que todavía no se ha jugado");
        }

        /*
         * El roster a validar es el de la convocatoria del partido si
         * existe (es opcional); si no, el roster completo del equipo.
         */
        List<Long> roster = obtenerRoster(partido);

        validarEstadisticas(
                roster,
                request.getJugadores());

        /*
         * RESULTADO (texto libre) se sigue calculando y sobreescribiendo
         * a partir de GOLES_FAVOR/GOLES_CONTRA, para que todas las
         * pantallas que ya lo leen (admin web, Flutter) sigan
         * funcionando sin cambios.
         */
        partido.setGolesFavor(request.getGolesFavor());
        partido.setGolesContra(request.getGolesContra());
        partido.setResultado(
                request.getGolesFavor() + "-" + request.getGolesContra());
        partido.setUsuarioActualizoId(usuarioAppId);
        partido.setFechaActualizacion(
                Timestamp.valueOf(LocalDateTime.now()));

        partidoDao.actualizar(partido);

        /*
         * Reemplazo completo de las estadísticas por jugador, igual que
         * EntrenamientoServiceImpl.actualizar hace con las asistencias.
         */
        estadisticaJugadorDao.eliminarPorPartido(partidoId);

        for (PartidoEstadisticaJugadorRequest jugadorRequest : request.getJugadores()) {

            PartidoEstadisticaJugador estadistica = new PartidoEstadisticaJugador();

            estadistica.setPartidoId(partidoId);
            estadistica.setJugadorId(jugadorRequest.getJugadorId());
            estadistica.setGoles(jugadorRequest.getGoles());
            estadistica.setAsistencias(jugadorRequest.getAsistencias());
            estadistica.setTarjetasAmarillas(jugadorRequest.getTarjetasAmarillas());
            estadistica.setTarjetaRoja(jugadorRequest.isTarjetaRoja());

            estadisticaJugadorDao.guardar(estadistica);
        }

        return construirResponse(partido);
    }

    @Override
    @Transactional(readOnly = true)
    public PartidoEstadisticasResponse obtener(
            Long usuarioAppId,
            Long partidoId) {

        if (usuarioAppId == null) {
            throw new SecurityException(
                    "Usuario no autenticado");
        }

        if (partidoId == null) {
            throw new IllegalArgumentException(
                    "El ID del partido es obligatorio");
        }

        Partido partido = partidoDao.obtenerPorId(partidoId);

        if (partido == null) {
            throw new IllegalArgumentException(
                    "El partido no existe");
        }

        if (!equipoGestionDao.puedeGestionarEquipo(
                usuarioAppId,
                partido.getEquipoId())) {

            throw new SecurityException(
                    "El usuario no puede consultar este partido");
        }

        return construirResponse(partido);
    }

    private List<Long> obtenerRoster(Partido partido) {

        Convocatoria convocatoria = convocatoriaDao.obtenerPorPartidoId(
                partido.getId());

        if (convocatoria != null) {

            List<ConvocatoriaJugador> convocados = convocatoriaJugadorDao.obtenerPorConvocatoria(
                    convocatoria.getId());

            List<Long> roster = new ArrayList<>();

            for (ConvocatoriaJugador convocado : convocados) {
                roster.add(convocado.getJugadorId());
            }

            return roster;
        }

        return equipoGestionDao.obtenerJugadoresPorEquipo(
                partido.getEquipoId());
    }

    private void validarRequest(
            PartidoEstadisticasGuardarRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "La petición es obligatoria");
        }

        if (request.getGolesFavor() == null
                || request.getGolesFavor() < 0) {

            throw new IllegalArgumentException(
                    "Los goles a favor son obligatorios y no pueden ser negativos");
        }

        if (request.getGolesContra() == null
                || request.getGolesContra() < 0) {

            throw new IllegalArgumentException(
                    "Los goles en contra son obligatorios y no pueden ser negativos");
        }

        if (request.getJugadores() == null) {
            throw new IllegalArgumentException(
                    "Debe indicarse la estadística de los jugadores");
        }
    }

    private void validarEstadisticas(
            List<Long> roster,
            List<PartidoEstadisticaJugadorRequest> jugadores) {

        Set<Long> rosterSet = new HashSet<>(roster);

        Set<Long> jugadoresRecibidos = new HashSet<>();

        for (PartidoEstadisticaJugadorRequest jugador : jugadores) {

            if (jugador == null || jugador.getJugadorId() == null) {
                throw new IllegalArgumentException(
                        "Existe una estadística inválida");
            }

            if (jugador.getGoles() == null || jugador.getGoles() < 0
                    || jugador.getAsistencias() == null || jugador.getAsistencias() < 0
                    || jugador.getTarjetasAmarillas() == null || jugador.getTarjetasAmarillas() < 0) {

                throw new IllegalArgumentException(
                        "Las estadísticas del jugador "
                                + jugador.getJugadorId()
                                + " no son válidas");
            }

            /*
             * Un jugador no puede aparecer dos veces.
             */
            if (!jugadoresRecibidos.add(jugador.getJugadorId())) {

                throw new IllegalArgumentException(
                        "El jugador "
                                + jugador.getJugadorId()
                                + " aparece más de una vez");
            }

            /*
             * No aceptamos un jugador que no pertenezca a la
             * convocatoria del partido (o al equipo, si no tiene
             * convocatoria).
             */
            if (!rosterSet.contains(jugador.getJugadorId())) {

                throw new SecurityException(
                        "El jugador "
                                + jugador.getJugadorId()
                                + " no forma parte de la convocatoria (o del equipo) de este partido");
            }
        }

        /*
         * Deben estar TODOS los jugadores de la convocatoria (o del
         * equipo, si no tiene convocatoria).
         */
        if (!jugadoresRecibidos.equals(rosterSet)) {

            throw new IllegalArgumentException(
                    "Debe indicar la estadística de todos los jugadores de la convocatoria "
                            + "(o de todo el equipo si el partido no tiene convocatoria)");
        }
    }

    private PartidoEstadisticasResponse construirResponse(
            Partido partido) {

        List<PartidoEstadisticaJugador> estadisticas = estadisticaJugadorDao.obtenerPorPartido(
                partido.getId());

        List<PartidoEstadisticaJugadorResponse> jugadores = new ArrayList<>();

        for (PartidoEstadisticaJugador estadistica : estadisticas) {

            String nombreJugador = equipoGestionDao.obtenerNombreJugador(
                    estadistica.getJugadorId());

            jugadores.add(
                    new PartidoEstadisticaJugadorResponse(
                            estadistica.getJugadorId(),
                            nombreJugador,
                            estadistica.getGoles(),
                            estadistica.getAsistencias(),
                            estadistica.getTarjetasAmarillas(),
                            estadistica.isTarjetaRoja()));
        }

        return new PartidoEstadisticasResponse(
                partido.getId(),
                partido.getGolesFavor(),
                partido.getGolesContra(),
                partido.getResultado(),
                jugadores);
    }

    /*
     * Combina Partido.dia (fecha, sin hora) y Partido.hora ("HH:mm",
     * opcional) en un único LocalDateTime, para poder comparar con
     * "ahora" y detectar partidos que todavía no se han jugado. Sin
     * hora informada se asume medianoche (00:00), lo que en la práctica
     * solo bloquea el mismo día del partido si todavía no ha empezado.
     */
    private static LocalDateTime combinarFechaHora(
            Date dia,
            String hora) {

        if (dia == null) {
            return null;
        }

        LocalDate fecha = new java.sql.Date(dia.getTime()).toLocalDate();

        return LocalDateTime.of(fecha, parsearHora(hora));
    }

    /*
     * HORA es texto libre: acepta "H:mm", "HH:mm", "18.30" o "18:30h".
     * Si no se puede interpretar se asume medianoche, para no bloquear
     * el guardado del resultado por un formato de hora inesperado.
     */
    private static LocalTime parsearHora(String hora) {

        if (hora == null || hora.isBlank()) {
            return LocalTime.MIDNIGHT;
        }

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{1,2})\\s*[:.hH]\\s*(\\d{2})")
                .matcher(hora);

        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));

            if (h <= 23 && min <= 59) {
                return LocalTime.of(h, min);
            }
        }

        return LocalTime.MIDNIGHT;
    }
}
