package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Entrenamiento;
import com.mikedev.mutxamelcf.model.EntrenamientoAsistencia;
import com.mikedev.mutxamelcf.model.EntrenamientoAsistenciaRequest;
import com.mikedev.mutxamelcf.model.EntrenamientoAsistenciaResponse;
import com.mikedev.mutxamelcf.model.EntrenamientoGuardarRequest;
import com.mikedev.mutxamelcf.model.EntrenamientoResponse;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.EntrenamientoService;

@Service
public class EntrenamientoServiceImpl
                implements EntrenamientoService {

        private static final Set<String> ESTADOS_VALIDOS = Set.of(
                        "PRESENTE",
                        "FALTA",
                        "FALTA_JUSTIFICADA",
                        "MAL_COMPORTAMIENTO",
                        "RETRASO");

        private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        private final EntrenamientoDao entrenamientoDao;
        private final EntrenamientoAsistenciaDao asistenciaDao;
        private final EquipoGestionDao equipoGestionDao;
        private final ComunicacionService comunicacionService;

        public EntrenamientoServiceImpl(
                        EntrenamientoDao entrenamientoDao,
                        EntrenamientoAsistenciaDao asistenciaDao,
                        EquipoGestionDao equipoGestionDao,
                        ComunicacionService comunicacionService) {

                this.entrenamientoDao = entrenamientoDao;
                this.asistenciaDao = asistenciaDao;
                this.equipoGestionDao = equipoGestionDao;
                this.comunicacionService = comunicacionService;
        }

        @Override
        @Transactional
        public EntrenamientoResponse crear(
                        Long usuarioAppId,
                        EntrenamientoGuardarRequest request) {

                validarRequest(request);

                if (usuarioAppId == null) {
                        throw new SecurityException(
                                        "Usuario no autenticado");
                }

                Long equipoId = request.getEquipoId();

                /*
                 * Comprobamos que el equipo existe.
                 */
                if (!equipoGestionDao.existeEquipo(equipoId)) {
                        throw new IllegalArgumentException(
                                        "El equipo no existe");
                }

                /*
                 * El entrenador solo puede gestionar equipos
                 * que tenga asignados.
                 */
                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                equipoId)) {

                        throw new SecurityException(
                                        "El usuario no puede gestionar este equipo");
                }

                /*
                 * Obtenemos los jugadores reales del equipo.
                 */
                List<Long> jugadoresEquipo = equipoGestionDao.obtenerJugadoresPorEquipo(
                                equipoId);

                validarAsistencias(
                                jugadoresEquipo,
                                request.getAsistencias());

                /*
                 * Creamos el entrenamiento.
                 */
                Entrenamiento entrenamiento = new Entrenamiento();

                entrenamiento.setEquipoId(equipoId);
                entrenamiento.setFecha(request.getFecha());
                entrenamiento.setUsuarioEntrenadorId(usuarioAppId);

                entrenamiento = entrenamientoDao.guardar(entrenamiento);

                /*
                 * Guardamos todas las asistencias.
                 */
                for (EntrenamientoAsistenciaRequest asistenciaRequest : request.getAsistencias()) {

                        EntrenamientoAsistencia asistencia = new EntrenamientoAsistencia();

                        asistencia.setEntrenamientoId(
                                        entrenamiento.getId());

                        asistencia.setJugadorId(
                                        asistenciaRequest.getJugadorId());

                        asistencia.setEstado(
                                        asistenciaRequest.getEstado());

                        asistenciaDao.guardar(asistencia);
                }

                /*
                 * Generamos las comunicaciones/notificaciones
                 * correspondientes.
                 */
                generarComunicaciones(
                                request.getFecha(),
                                equipoId,
                                request.getAsistencias(),
                                usuarioAppId);

                return construirResponse(entrenamiento);
        }

        @Override
        @Transactional
        public EntrenamientoResponse actualizar(
                        Long usuarioAppId,
                        Long entrenamientoId,
                        EntrenamientoGuardarRequest request) {

                if (usuarioAppId == null) {
                        throw new SecurityException(
                                        "Usuario no autenticado");
                }

                if (entrenamientoId == null) {
                        throw new IllegalArgumentException(
                                        "El ID del entrenamiento es obligatorio");
                }

                validarRequest(request);

                Entrenamiento entrenamiento = entrenamientoDao.obtenerPorId(entrenamientoId);

                if (entrenamiento == null) {
                        throw new IllegalArgumentException(
                                        "El entrenamiento no existe");
                }

                /*
                 * El entrenamiento pertenece a un equipo concreto.
                 * No permitimos cambiar de equipo al editar.
                 */
                if (!entrenamiento.getEquipoId().equals(request.getEquipoId())) {
                        throw new IllegalArgumentException(
                                        "No se puede cambiar el equipo del entrenamiento");
                }

                /*
                 * Comprobamos que el usuario puede gestionar
                 * el equipo al que pertenece el entrenamiento.
                 */
                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                entrenamiento.getEquipoId())) {

                        throw new SecurityException(
                                        "El usuario no puede gestionar este entrenamiento");
                }

                /*
                 * Obtenemos los jugadores reales del equipo
                 * y aplicamos exactamente las mismas validaciones
                 * que utilizamos al crear.
                 */
                List<Long> jugadoresEquipo = equipoGestionDao.obtenerJugadoresPorEquipo(
                                entrenamiento.getEquipoId());

                validarAsistencias(
                                jugadoresEquipo,
                                request.getAsistencias());

                /*
                 * Actualizamos la fecha.
                 */
                entrenamiento.setFecha(request.getFecha());

                entrenamientoDao.actualizar(entrenamiento);

                /*
                 * Reemplazamos las asistencias actuales por
                 * las nuevas.
                 */
                asistenciaDao.eliminarPorEntrenamiento(
                                entrenamientoId);

                for (EntrenamientoAsistenciaRequest asistenciaRequest : request.getAsistencias()) {

                        EntrenamientoAsistencia asistencia = new EntrenamientoAsistencia();

                        asistencia.setEntrenamientoId(entrenamientoId);

                        asistencia.setJugadorId(
                                        asistenciaRequest.getJugadorId());

                        asistencia.setEstado(
                                        asistenciaRequest.getEstado());

                        asistenciaDao.guardar(asistencia);
                }

                return construirResponse(entrenamiento);
        }

        @Override
        @Transactional(readOnly = true)
        public EntrenamientoResponse obtenerPorId(
                        Long usuarioAppId,
                        Long entrenamientoId) {

                if (usuarioAppId == null) {
                        throw new SecurityException(
                                        "Usuario no autenticado");
                }

                if (entrenamientoId == null) {
                        throw new IllegalArgumentException(
                                        "El ID del entrenamiento es obligatorio");
                }

                Entrenamiento entrenamiento = entrenamientoDao.obtenerPorId(
                                entrenamientoId);

                if (entrenamiento == null) {
                        throw new IllegalArgumentException(
                                        "El entrenamiento no existe");
                }

                /*
                 * Un entrenador solo puede consultar
                 * entrenamientos de sus equipos.
                 */
                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                entrenamiento.getEquipoId())) {

                        throw new SecurityException(
                                        "El usuario no puede consultar este entrenamiento");
                }

                return construirResponse(entrenamiento);
        }

        private void validarRequest(
                        EntrenamientoGuardarRequest request) {

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "La petición es obligatoria");
                }

                if (request.getEquipoId() == null) {
                        throw new IllegalArgumentException(
                                        "El equipo es obligatorio");
                }

                if (request.getFecha() == null) {
                        throw new IllegalArgumentException(
                                        "La fecha del entrenamiento es obligatoria");
                }

                if (request.getFecha().isAfter(LocalDate.now())) {
                        throw new IllegalArgumentException(
                                        "La fecha del entrenamiento no puede ser posterior a hoy");
                }

                if (request.getAsistencias() == null
                                || request.getAsistencias().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "Debe indicarse la asistencia de todos los jugadores");
                }
        }

        private void validarAsistencias(
                        List<Long> jugadoresEquipo,
                        List<EntrenamientoAsistenciaRequest> asistencias) {

                Set<Long> jugadoresEquipoSet = new HashSet<>(jugadoresEquipo);

                Set<Long> jugadoresRecibidos = new HashSet<>();

                for (EntrenamientoAsistenciaRequest asistencia : asistencias) {

                        if (asistencia == null) {
                                throw new IllegalArgumentException(
                                                "Existe una asistencia inválida");
                        }

                        if (asistencia.getJugadorId() == null) {
                                throw new IllegalArgumentException(
                                                "El jugador es obligatorio");
                        }

                        if (asistencia.getEstado() == null
                                        || !ESTADOS_VALIDOS.contains(
                                                        asistencia.getEstado())) {

                                throw new IllegalArgumentException(
                                                "Estado de asistencia no válido para el jugador "
                                                                + asistencia.getJugadorId());
                        }

                        /*
                         * Un jugador no puede aparecer dos veces.
                         */
                        if (!jugadoresRecibidos.add(
                                        asistencia.getJugadorId())) {

                                throw new IllegalArgumentException(
                                                "El jugador "
                                                                + asistencia.getJugadorId()
                                                                + " aparece más de una vez");
                        }

                        /*
                         * No aceptamos un jugador que no pertenezca
                         * al equipo solicitado.
                         */
                        if (!jugadoresEquipoSet.contains(
                                        asistencia.getJugadorId())) {

                                throw new SecurityException(
                                                "El jugador "
                                                                + asistencia.getJugadorId()
                                                                + " no pertenece al equipo");
                        }
                }

                /*
                 * Deben estar TODOS los jugadores del equipo.
                 */
                if (!jugadoresRecibidos.equals(
                                jugadoresEquipoSet)) {

                        throw new IllegalArgumentException(
                                        "Debe indicar la asistencia de todos los jugadores del equipo");
                }
        }

        private void generarComunicaciones(
                        LocalDate fecha,
                        Long equipoId,
                        List<EntrenamientoAsistenciaRequest> asistencias,
                        Long usuarioAppId) {

                String nombreEquipo = equipoGestionDao.obtenerNombreEquipo(
                                equipoId);

                String fechaTexto = fecha.format(FORMATO_FECHA);

                for (EntrenamientoAsistenciaRequest asistencia : asistencias) {

                        Long jugadorId = asistencia.getJugadorId();

                        String estado = asistencia.getEstado();

                        if ("FALTA".equals(estado)) {

                                crearComunicacionFalta(
                                                jugadorId,
                                                fechaTexto,
                                                usuarioAppId);
                        }

                        if ("MAL_COMPORTAMIENTO".equals(estado)) {

                                crearComunicacionMalComportamiento(
                                                jugadorId,
                                                nombreEquipo,
                                                fechaTexto,
                                                usuarioAppId);
                        }
                }
        }

        private void crearComunicacionFalta(
                        Long jugadorId,
                        String fechaTexto,
                        Long usuarioAppId) {

                String nombreJugador = equipoGestionDao.obtenerNombreJugador(
                                jugadorId);

                String mensaje = "Hola.\n\n"
                                + nombreJugador
                                + " no acudió al entrenamiento el dia "
                                + fechaTexto
                                + ".\n\n Por favor avisen al entrenador de las ausencias "
                                + "para un mejor control de los ejercicios del equipo.\n\n "
                                + "Muchas gracias.";

                Set<Long> destinatarios = new HashSet<>();

                /*
                 * Usuario directamente asociado al jugador.
                 */
                destinatarios.addAll(
                                equipoGestionDao.obtenerUsuariosPorJugador(
                                                jugadorId));

                /*
                 * Usuarios familiares asociados al jugador.
                 */
                destinatarios.addAll(
                                equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(
                                                jugadorId));

                if (destinatarios.isEmpty()) {
                        return;
                }

                Comunicacion comunicacion = new Comunicacion();

                comunicacion.setTitulo(
                                "Falta al entrenamiento");

                comunicacion.setContenido(
                                mensaje);

                /*
                 * La propia capa de comunicaciones se encarga de:
                 *
                 * 1. Crear COMUNICACIONES
                 * 2. Asociarla a los usuarios
                 * 3. Crear NOTIFICACIONES_APP
                 * 4. Enviar FCM
                 */
                comunicacionService.crearPrivada(
                                comunicacion,
                                new ArrayList<>(destinatarios),
                                usuarioAppId);
        }

        private void crearComunicacionMalComportamiento(
                        Long jugadorId,
                        String nombreEquipo,
                        String fechaTexto,
                        Long usuarioAppId) {

                String nombreJugador = equipoGestionDao.obtenerNombreJugador(
                                jugadorId);

                String mensaje = nombreJugador
                                + " del equipo "
                                + nombreEquipo
                                + " ha tenido un mal comportamiento "
                                + "en el entrenamiento del dia "
                                + fechaTexto;

                List<Long> coordinadores = equipoGestionDao.obtenerCoordinadores();

                if (coordinadores.isEmpty()) {
                        return;
                }

                Comunicacion comunicacion = new Comunicacion();

                comunicacion.setTitulo(
                                "Mal comportamiento");

                comunicacion.setContenido(
                                mensaje);

                /*
                 * Únicamente los coordinadores reciben
                 * esta comunicación.
                 */
                comunicacionService.crearPrivada(
                                comunicacion,
                                coordinadores,
                                usuarioAppId);
        }

        private EntrenamientoResponse construirResponse(
                        Entrenamiento entrenamiento) {

                List<EntrenamientoAsistencia> asistencias = asistenciaDao.obtenerPorEntrenamiento(
                                entrenamiento.getId());

                List<EntrenamientoAsistenciaResponse> respuestas = new ArrayList<>();

                for (EntrenamientoAsistencia asistencia : asistencias) {

                        String nombreJugador = equipoGestionDao.obtenerNombreJugador(
                                        asistencia.getJugadorId());

                        respuestas.add(
                                        new EntrenamientoAsistenciaResponse(
                                                        asistencia.getJugadorId(),
                                                        nombreJugador,
                                                        asistencia.getEstado()));
                }

                return new EntrenamientoResponse(
                                entrenamiento.getId(),
                                entrenamiento.getEquipoId(),
                                equipoGestionDao.obtenerNombreEquipo(
                                                entrenamiento.getEquipoId()),
                                entrenamiento.getFecha(),
                                entrenamiento.getUsuarioEntrenadorId(),
                                respuestas);
        }

        @Override
        @Transactional(readOnly = true)
        public List<EntrenamientoResponse> obtenerPorEquipo(
                        Long usuarioAppId,
                        Long equipoId) {

                if (usuarioAppId == null) {
                        throw new SecurityException(
                                        "Usuario no autenticado");
                }

                if (equipoId == null) {
                        throw new IllegalArgumentException(
                                        "El equipo es obligatorio");
                }

                if (!equipoGestionDao.existeEquipo(equipoId)) {
                        throw new IllegalArgumentException(
                                        "El equipo no existe");
                }

                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                equipoId)) {

                        throw new SecurityException(
                                        "El usuario no puede consultar los entrenamientos de este equipo");
                }

                List<Entrenamiento> entrenamientos = entrenamientoDao.obtenerPorEquipo(equipoId);

                return entrenamientos.stream()
                                .map(this::construirResponse)
                                .toList();
        }

}