package com.mikedev.mutxamelcf.serviceimpl;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.model.ConvocatoriaGuardarRequest;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugadorResponse;
import com.mikedev.mutxamelcf.model.ConvocatoriaResponse;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.ConvocatoriaService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConvocatoriaServiceImpl implements ConvocatoriaService {

        /** Valor de "tipo" en el data del push de una convocatoria (nueva o actualizada). */
        static final String TIPO_PUSH_CONVOCATORIA = "CONVOCATORIA";

        private final ConvocatoriaDao convocatoriaDao;
        private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
        private final EquipoGestionDao equipoGestionDao;
        private final PartidoDao partidoDao;
        private final ComunicacionService comunicacionService;
        private final JustificacionFaltaPartidoDao justificacionFaltaPartidoDao;

        public ConvocatoriaServiceImpl(
                        ConvocatoriaDao convocatoriaDao,
                        ConvocatoriaJugadorDao convocatoriaJugadorDao,
                        EquipoGestionDao equipoGestionDao,
                        PartidoDao partidoDao,
                        ComunicacionService comunicacionService,
                        JustificacionFaltaPartidoDao justificacionFaltaPartidoDao) {

                this.justificacionFaltaPartidoDao = justificacionFaltaPartidoDao;
                this.convocatoriaDao = convocatoriaDao;
                this.convocatoriaJugadorDao = convocatoriaJugadorDao;
                this.equipoGestionDao = equipoGestionDao;
                this.partidoDao = partidoDao;
                this.comunicacionService = comunicacionService;
        }

        @Override
        @Transactional
        public ConvocatoriaResponse crear(
                        Long usuarioAppId,
                        ConvocatoriaGuardarRequest request) {

                validarUsuario(usuarioAppId);
                validarRequest(request);

                Partido partido = partidoDao.obtenerPorId(request.getPartidoId());

                if (partido == null) {
                        throw new IllegalArgumentException(
                                        "El partido no existe");
                }

                Long equipoId = partido.getEquipoId();

                if (!equipoGestionDao.existeEquipo(equipoId)) {
                        throw new IllegalArgumentException(
                                        "El equipo no existe");
                }

                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                equipoId)) {

                        throw new SecurityException(
                                        "No tienes permiso para gestionar este equipo");
                }

                List<Long> jugadoresEquipo = equipoGestionDao.obtenerJugadoresPorEquipo(equipoId);

                if (jugadoresEquipo.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "El equipo no tiene jugadores");
                }

                // Se bloquea el partido ANTES de comprobar si ya tiene convocatoria:
                // así dos altas simultáneas no pasan las dos la comprobación, y se
                // serializa también con quien justifica su ausencia a este partido.
                partidoDao.bloquearPorId(partido.getId());

                if (convocatoriaDao.existePorPartido(
                                partido.getId(),
                                null)) {

                        throw new IllegalArgumentException(
                                        "Ya existe una convocatoria para este partido");
                }

                validarJugadoresSeleccionados(
                                request.getJugadoresIds(),
                                jugadoresEquipo);

                rechazarJustificados(
                                partido.getId(),
                                request.getJugadoresIds(),
                                Set.of());

                Convocatoria convocatoria = new Convocatoria();

                convocatoria.setEquipoId(equipoId);
                convocatoria.setPartidoId(partido.getId());
                convocatoria.setHoraConvocatoria(
                                limpiar(request.getHoraConvocatoria()));
                convocatoria.setLugarConvocatoria(
                                limpiar(request.getLugarConvocatoria()));
                convocatoria.setUsuarioEntrenadorId(
                                usuarioAppId);

                Convocatoria guardada = convocatoriaDao.guardar(convocatoria);

                /*
                 * El rival/campo/fecha/hora no se persisten en la
                 * convocatoria: se copian aquí, en memoria, desde el
                 * Partido ya cargado, únicamente para poder construir la
                 * respuesta y las notificaciones sin tener que volver a
                 * leer de base de datos.
                 */
                aplicarDatosPartido(guardada, partido);

                for (Long jugadorId : request.getJugadoresIds()) {

                        ConvocatoriaJugador convocatoriaJugador = new ConvocatoriaJugador();

                        convocatoriaJugador.setConvocatoriaId(
                                        guardada.getId());

                        convocatoriaJugador.setJugadorId(
                                        jugadorId);

                        convocatoriaJugadorDao.guardar(
                                        convocatoriaJugador);
                }

                generarNotificaciones(
                                guardada,
                                request.getJugadoresIds());

                return construirResponse(guardada);
        }

        private void aplicarDatosPartido(
                        Convocatoria convocatoria,
                        Partido partido) {

                convocatoria.setRival(partido.getRival());
                convocatoria.setCampo(partido.getCampo());

                convocatoria.setFechaPartido(
                                partido.getDia() != null
                                                ? new java.sql.Date(partido.getDia().getTime()).toLocalDate()
                                                : null);

                convocatoria.setHoraPartido(partido.getHora());
        }

        private void generarNotificaciones(
                        Convocatoria convocatoria,
                        List<Long> jugadoresIds) {

                String equipo = equipoGestionDao.obtenerNombreEquipo(
                                convocatoria.getEquipoId());

                String fecha = convocatoria.getFechaPartido() != null
                                ? convocatoria.getFechaPartido().toString()
                                : "";

                String horaPartido = convocatoria.getHoraPartido() != null
                                ? convocatoria.getHoraPartido()
                                : "";

                String horaConvocatoria = convocatoria.getHoraConvocatoria() != null
                                ? convocatoria.getHoraConvocatoria()
                                : "";

                String lugar = convocatoria.getLugarConvocatoria() != null
                                ? convocatoria.getLugarConvocatoria()
                                : "";

                String titulo = "Nueva convocatoria";

                // A quien ha justificado su ausencia a este partido no se le avisa de
                // que "ha sido convocado" (sigue en la lista hasta que se le sustituya).
                Set<Long> ausentes = new HashSet<>();

                if (convocatoria.getPartidoId() != null) {

                        for (JustificacionFaltaPartido justificacion : justificacionFaltaPartidoDao
                                        .obtenerPorPartido(convocatoria.getPartidoId())) {

                                ausentes.add(justificacion.getJugadorId());
                        }
                }

                for (Long jugadorId : jugadoresIds) {

                        if (ausentes.contains(jugadorId)) {
                                continue;
                        }

                        String jugador = equipoGestionDao.obtenerNombreJugador(
                                        jugadorId);

                        List<Long> usuariosDirectos = equipoGestionDao.obtenerUsuariosPorJugador(
                                        jugadorId);

                        List<Long> usuariosFamiliares = equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(
                                        jugadorId);

                        Set<Long> destinatariosUnicos = new HashSet<>();

                        destinatariosUnicos.addAll(usuariosDirectos);
                        destinatariosUnicos.addAll(usuariosFamiliares);

                        for (Long usuarioId : destinatariosUnicos) {

                                boolean esJugador = usuariosDirectos.contains(usuarioId);

                                String mensaje;

                                if (esJugador) {

                                        mensaje = "Has sido convocado con el equipo "
                                                        + equipo
                                                        + " para el partido contra "
                                                        + convocatoria.getRival()
                                                        + " el día "
                                                        + fecha
                                                        + " a las "
                                                        + horaPartido
                                                        + "h.\n\n"
                                                        + "La convocatoria es a las "
                                                        + horaConvocatoria
                                                        + "h en "
                                                        + lugar
                                                        + ".";

                                } else {

                                        mensaje = jugador
                                                        + " ha sido convocado con el equipo "
                                                        + equipo
                                                        + " para el partido contra "
                                                        + convocatoria.getRival()
                                                        + " el día "
                                                        + fecha
                                                        + " a las "
                                                        + horaPartido
                                                        + "h.\n\n"
                                                        + "La convocatoria es a las "
                                                        + horaConvocatoria
                                                        + "h en "
                                                        + lugar
                                                        + ".";
                                }

                                Comunicacion comunicacion = new Comunicacion();

                                comunicacion.setTitulo(titulo);
                                comunicacion.setContenido(mensaje);

                                // La app navega según este tipo (no según el título
                                // del aviso): JUGADOR/FAMILIAR abren el calendario en
                                // el día del partido (fechaPartido, yyyy-MM-dd).
                                Map<String, String> datosPush = new HashMap<>();

                                datosPush.put("tipo", TIPO_PUSH_CONVOCATORIA);
                                datosPush.put("convocatoriaId", String.valueOf(convocatoria.getId()));

                                if (convocatoria.getFechaPartido() != null) {
                                        datosPush.put("fechaPartido", convocatoria.getFechaPartido().toString());
                                }

                                comunicacionService.crearPrivada(
                                                comunicacion,
                                                List.of(usuarioId),
                                                convocatoria.getUsuarioEntrenadorId(),
                                                new OpcionesPushComunicacion(null, datosPush));
                        }
                }
        }

        @Override
        @Transactional
        public ConvocatoriaResponse actualizar(
                        Long usuarioAppId,
                        Long convocatoriaId,
                        ConvocatoriaGuardarRequest request) {

                validarUsuario(usuarioAppId);
                validarRequestActualizacion(request);

                if (convocatoriaId == null || convocatoriaId <= 0) {
                        throw new IllegalArgumentException(
                                        "El ID de la convocatoria no es válido");
                }

                Convocatoria convocatoria = convocatoriaDao.obtenerPorId(convocatoriaId);

                if (convocatoria == null) {
                        throw new IllegalArgumentException(
                                        "La convocatoria no existe");
                }

                /*
                 * Comprobamos que el usuario puede gestionar el equipo
                 * ACTUAL de la convocatoria (antes de aplicar ningún
                 * cambio del request).
                 */
                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                convocatoria.getEquipoId())) {

                        throw new SecurityException(
                                        "No tienes permiso para actualizar esta convocatoria");
                }

                Partido partido = partidoDao.obtenerPorId(request.getPartidoId());

                if (partido == null) {
                        throw new IllegalArgumentException(
                                        "El partido no existe");
                }

                /*
                 * No permitimos cambiar el equipo de la convocatoria: el
                 * partido elegido al re-apuntar la convocatoria tiene que
                 * seguir siendo del mismo equipo.
                 */
                if (!convocatoria.getEquipoId().equals(partido.getEquipoId())) {
                        throw new IllegalArgumentException(
                                        "No se puede cambiar el equipo de la convocatoria");
                }

                // Se bloquea el partido ANTES de comprobar si ya tiene convocatoria
                // (como en crear): serializa con otras altas o ediciones sobre el mismo
                // partido y con quien justifica su ausencia a él.
                partidoDao.bloquearPorId(partido.getId());

                if (convocatoriaDao.existePorPartido(
                                partido.getId(),
                                convocatoriaId)) {

                        throw new IllegalArgumentException(
                                        "Ya existe una convocatoria para este partido");
                }

                boolean cambiaElPartido = !partido.getId().equals(convocatoria.getPartidoId());

                /*
                 * Si el request trae la lista de jugadores, la convocatoria
                 * pasa a tener esos: salen (solo su fila) los que ya no
                 * están, entran los nuevos y los demás se conservan. Sin
                 * lista (null), los convocados no se tocan.
                 */
                List<ConvocatoriaJugador> actuales = convocatoriaJugadorDao.obtenerPorConvocatoria(
                                convocatoriaId);

                Set<Long> idsActuales = new HashSet<>();

                for (ConvocatoriaJugador actual : actuales) {
                        idsActuales.add(actual.getJugadorId());
                }

                List<Long> nuevosIds = request.getJugadoresIds();

                if (nuevosIds != null) {

                        if (nuevosIds.isEmpty()) {
                                throw new IllegalArgumentException(
                                                "Debes seleccionar al menos un jugador");
                        }

                        validarJugadoresSeleccionados(
                                        nuevosIds,
                                        equipoGestionDao.obtenerJugadoresPorEquipo(
                                                        convocatoria.getEquipoId()));

                        // Mantener a uno que ya estaba se permite aunque haya justificado su
                        // ausencia, pero solo si el partido no cambia: sus justificaciones eran
                        // de otro partido, y con el nuevo se rechaza a quien esté justificado.
                        rechazarJustificados(
                                        partido.getId(),
                                        nuevosIds,
                                        cambiaElPartido ? Set.<Long>of() : idsActuales);
                }

                /*
                 * Actualizamos el partido vinculado y los datos propios
                 * de la convocatoria.
                 */
                convocatoria.setPartidoId(partido.getId());

                convocatoria.setHoraConvocatoria(
                                limpiar(request.getHoraConvocatoria()));

                convocatoria.setLugarConvocatoria(
                                limpiar(request.getLugarConvocatoria()));

                convocatoriaDao.actualizar(convocatoria);

                /*
                 * Sin lista de jugadores y con otro partido, los convocados actuales
                 * se revisan contra las justificaciones del partido NUEVO: quien esté
                 * justificado se quita de la convocatoria (haya o no disponibles) y se
                 * avisa al cuerpo técnico.
                 */
                if (nuevosIds == null && cambiaElPartido) {
                        quitarJustificadosDelPartidoNuevo(convocatoria, partido.getId(), actuales, usuarioAppId);
                }

                if (nuevosIds != null) {

                        for (Long actualId : idsActuales) {

                                if (!nuevosIds.contains(actualId)) {
                                        convocatoriaJugadorDao.eliminarPorConvocatoriaYJugador(
                                                        convocatoriaId,
                                                        actualId);
                                }
                        }

                        for (Long nuevoId : nuevosIds) {

                                if (!idsActuales.contains(nuevoId)) {

                                        ConvocatoriaJugador convocatoriaJugador = new ConvocatoriaJugador();

                                        convocatoriaJugador.setConvocatoriaId(convocatoriaId);
                                        convocatoriaJugador.setJugadorId(nuevoId);

                                        convocatoriaJugadorDao.guardar(convocatoriaJugador);
                                }
                        }
                }

                aplicarDatosPartido(convocatoria, partido);

                /*
                 * Obtenemos los jugadores que ya están asociados
                 * a la convocatoria.
                 */
                List<ConvocatoriaJugador> jugadoresConvocados = convocatoriaJugadorDao.obtenerPorConvocatoria(
                                convocatoriaId);

                List<Long> jugadoresIds = jugadoresConvocados.stream()
                                .map(ConvocatoriaJugador::getJugadorId)
                                .toList();

                /*
                 * Volvemos a notificar a todos los jugadores/familiares
                 * de la convocatoria con los datos actualizados.
                 */
                generarNotificaciones(
                                convocatoria,
                                jugadoresIds);

                return construirResponse(convocatoria);
        }

        @Override
        public ConvocatoriaResponse obtenerPorId(
                        Long usuarioAppId,
                        Long convocatoriaId) {

                validarUsuario(usuarioAppId);

                if (convocatoriaId == null || convocatoriaId <= 0) {
                        throw new IllegalArgumentException(
                                        "El ID de la convocatoria no es válido");
                }

                Convocatoria convocatoria = convocatoriaDao.obtenerPorId(convocatoriaId);

                if (convocatoria == null) {
                        throw new IllegalArgumentException(
                                        "La convocatoria no existe");
                }

                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                convocatoria.getEquipoId())) {

                        throw new SecurityException(
                                        "No tienes permiso para consultar esta convocatoria");
                }

                return construirResponse(convocatoria);
        }

        @Override
        public List<ConvocatoriaResponse> obtenerPorEquipo(
                        Long usuarioAppId,
                        Long equipoId) {

                validarUsuario(usuarioAppId);

                if (equipoId == null || equipoId <= 0) {
                        throw new IllegalArgumentException(
                                        "El ID del equipo no es válido");
                }

                if (!equipoGestionDao.existeEquipo(equipoId)) {
                        throw new IllegalArgumentException(
                                        "El equipo no existe");
                }

                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                equipoId)) {

                        throw new SecurityException(
                                        "No tienes permiso para consultar este equipo");
                }

                List<Convocatoria> convocatorias = convocatoriaDao.obtenerPorEquipo(equipoId);

                List<ConvocatoriaResponse> respuesta = new ArrayList<>();

                for (Convocatoria convocatoria : convocatorias) {
                        respuesta.add(
                                        construirResponse(convocatoria));
                }

                return respuesta;
        }

        @Override
        @Transactional
        public void eliminar(
                        Long usuarioAppId,
                        Long convocatoriaId) {

                validarUsuario(usuarioAppId);

                if (convocatoriaId == null || convocatoriaId <= 0) {
                        throw new IllegalArgumentException(
                                        "El ID de la convocatoria no es válido");
                }

                Convocatoria convocatoria = convocatoriaDao.obtenerPorId(convocatoriaId);

                if (convocatoria == null) {
                        throw new IllegalArgumentException(
                                        "La convocatoria no existe");
                }

                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                convocatoria.getEquipoId())) {

                        throw new SecurityException(
                                        "No tienes permiso para eliminar esta convocatoria");
                }

                convocatoriaJugadorDao.eliminarPorConvocatoria(
                                convocatoriaId);

                convocatoriaDao.eliminar(convocatoriaId);
        }

        private ConvocatoriaResponse construirResponse(
                        Convocatoria convocatoria) {

                ConvocatoriaResponse response = new ConvocatoriaResponse();

                response.setId(convocatoria.getId());
                response.setEquipoId(convocatoria.getEquipoId());
                response.setEquipo(
                                equipoGestionDao.obtenerNombreEquipo(
                                                convocatoria.getEquipoId()));
                response.setPartidoId(convocatoria.getPartidoId());
                response.setRival(convocatoria.getRival());
                response.setCampo(convocatoria.getCampo());
                response.setFechaPartido(
                                convocatoria.getFechaPartido());
                response.setHoraPartido(
                                convocatoria.getHoraPartido());
                response.setHoraConvocatoria(
                                convocatoria.getHoraConvocatoria());
                response.setLugarConvocatoria(
                                convocatoria.getLugarConvocatoria());
                response.setUsuarioEntrenadorId(
                                convocatoria.getUsuarioEntrenadorId());

                List<ConvocatoriaJugador> jugadores = convocatoriaJugadorDao.obtenerPorConvocatoria(
                                convocatoria.getId());

                List<ConvocatoriaJugadorResponse> jugadoresResponse = new ArrayList<>();

                Map<Long, JustificacionFaltaPartido> justificadas = new HashMap<>();

                if (convocatoria.getPartidoId() != null) {

                        for (JustificacionFaltaPartido justificacion : justificacionFaltaPartidoDao
                                        .obtenerPorPartido(convocatoria.getPartidoId())) {

                                justificadas.put(justificacion.getJugadorId(), justificacion);
                        }
                }

                for (ConvocatoriaJugador jugador : jugadores) {

                        String nombre = equipoGestionDao.obtenerNombreJugador(
                                        jugador.getJugadorId());

                        JustificacionFaltaPartido ausencia = justificadas.get(jugador.getJugadorId());

                        jugadoresResponse.add(
                                        new ConvocatoriaJugadorResponse(
                                                        jugador.getJugadorId(),
                                                        nombre,
                                                        ausencia != null,
                                                        ausencia != null ? ausencia.getMotivo() : null));
                }

                response.setJugadores(jugadoresResponse);

                return response;
        }

        private void quitarJustificadosDelPartidoNuevo(
                        Convocatoria convocatoria,
                        Long partidoNuevoId,
                        List<ConvocatoriaJugador> actuales,
                        Long usuarioAppId) {

                Set<Long> justificados = new HashSet<>();

                for (JustificacionFaltaPartido justificacion : justificacionFaltaPartidoDao
                                .obtenerPorPartido(partidoNuevoId)) {

                        justificados.add(justificacion.getJugadorId());
                }

                List<String> nombres = new ArrayList<>();

                for (ConvocatoriaJugador actual : actuales) {

                        if (justificados.contains(actual.getJugadorId())) {

                                convocatoriaJugadorDao.eliminarPorConvocatoriaYJugador(
                                                convocatoria.getId(),
                                                actual.getJugadorId());

                                nombres.add(equipoGestionDao.obtenerNombreJugador(actual.getJugadorId()));
                        }
                }

                if (nombres.isEmpty()) {
                        return;
                }

                List<Long> destinatarios = new ArrayList<>(
                                equipoGestionDao.obtenerEntrenadoresPorEquipo(convocatoria.getEquipoId()));

                if (destinatarios.isEmpty()) {
                        destinatarios.addAll(equipoGestionDao.obtenerCoordinadores());
                }

                // Quien edita ya lo sabe: no se avisa a sí mismo.
                destinatarios.remove(usuarioAppId);

                if (destinatarios.isEmpty()) {
                        return;
                }

                Comunicacion comunicacion = new Comunicacion();

                comunicacion.setTitulo("Convocatoria actualizada");
                comunicacion.setContenido(
                                "Se han quitado de la convocatoria por ausencia justificada: "
                                                + String.join(", ", nombres));

                comunicacionService.crearPrivada(comunicacion, destinatarios, usuarioAppId);
        }

        /*
         * Un jugador con ausencia justificada a este partido no se puede
         * añadir a la convocatoria (sí mantener, si ya estaba en ella).
         */
        private void rechazarJustificados(
                        Long partidoId,
                        List<Long> jugadoresIds,
                        Set<Long> yaConvocados) {

                for (JustificacionFaltaPartido justificacion : justificacionFaltaPartidoDao
                                .obtenerPorPartido(partidoId)) {

                        Long jugadorId = justificacion.getJugadorId();

                        if (jugadoresIds.contains(jugadorId) && !yaConvocados.contains(jugadorId)) {

                                throw new IllegalArgumentException(
                                                equipoGestionDao.obtenerNombreJugador(jugadorId)
                                                                + " ha justificado su ausencia a este partido"
                                                                + " y no se puede convocar");
                        }
                }
        }

        private void validarRequest(
                        ConvocatoriaGuardarRequest request) {

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "La petición no puede ser nula");
                }

                if (request.getPartidoId() == null
                                || request.getPartidoId() <= 0) {

                        throw new IllegalArgumentException(
                                        "El partido es obligatorio");
                }

                if (request.getHoraConvocatoria() == null
                                || request.getHoraConvocatoria().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "La hora de convocatoria es obligatoria");
                }

                if (request.getLugarConvocatoria() == null
                                || request.getLugarConvocatoria().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "El lugar de convocatoria es obligatorio");
                }

                if (request.getJugadoresIds() == null
                                || request.getJugadoresIds().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "Debes seleccionar al menos un jugador");
                }
        }

        private void validarRequestActualizacion(
                        ConvocatoriaGuardarRequest request) {

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "La petición no puede ser nula");
                }

                if (request.getPartidoId() == null
                                || request.getPartidoId() <= 0) {

                        throw new IllegalArgumentException(
                                        "El partido es obligatorio");
                }

                if (request.getHoraConvocatoria() == null
                                || request.getHoraConvocatoria().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "La hora de convocatoria es obligatoria");
                }

                if (request.getLugarConvocatoria() == null
                                || request.getLugarConvocatoria().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "El lugar de convocatoria es obligatorio");
                }
        }

        private void validarJugadoresSeleccionados(
                        List<Long> jugadoresSeleccionados,
                        List<Long> jugadoresEquipo) {

                Set<Long> jugadoresEquipoSet = new HashSet<>(jugadoresEquipo);

                Set<Long> jugadoresSeleccionadosSet = new HashSet<>(jugadoresSeleccionados);

                if (jugadoresSeleccionados.size() != jugadoresSeleccionadosSet.size()) {

                        throw new IllegalArgumentException(
                                        "No se puede repetir un jugador");
                }

                for (Long jugadorId : jugadoresSeleccionados) {

                        if (jugadorId == null
                                        || jugadorId <= 0) {

                                throw new IllegalArgumentException(
                                                "Hay un jugador no válido");
                        }

                        if (!jugadoresEquipoSet.contains(jugadorId)) {

                                throw new SecurityException(
                                                "Uno de los jugadores no pertenece al equipo");
                        }
                }
        }

        private void validarUsuario(Long usuarioAppId) {

                if (usuarioAppId == null || usuarioAppId <= 0) {
                        throw new SecurityException(
                                        "Usuario no válido");
                }
        }

        private String limpiar(String valor) {

                if (valor == null) {
                        return null;
                }

                String resultado = valor.trim();

                return resultado.isEmpty()
                                ? null
                                : resultado;
        }
}
