package com.mikedev.mutxamelcf.serviceimpl;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.ConvocatoriaGuardarRequest;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugadorResponse;
import com.mikedev.mutxamelcf.model.ConvocatoriaResponse;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.ConvocatoriaService;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConvocatoriaServiceImpl implements ConvocatoriaService {

        private final ConvocatoriaDao convocatoriaDao;
        private final ConvocatoriaJugadorDao convocatoriaJugadorDao;
        private final EquipoGestionDao equipoGestionDao;
        private final ComunicacionService comunicacionService;

        public ConvocatoriaServiceImpl(
                        ConvocatoriaDao convocatoriaDao,
                        ConvocatoriaJugadorDao convocatoriaJugadorDao,
                        EquipoGestionDao equipoGestionDao,
                        ComunicacionService comunicacionService) {

                this.convocatoriaDao = convocatoriaDao;
                this.convocatoriaJugadorDao = convocatoriaJugadorDao;
                this.equipoGestionDao = equipoGestionDao;
                this.comunicacionService = comunicacionService;
        }

        @Override
        public ConvocatoriaResponse crear(
                        Long usuarioAppId,
                        ConvocatoriaGuardarRequest request) {

                validarUsuario(usuarioAppId);
                validarRequest(request);

                Long equipoId = request.getEquipoId();

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

                if (convocatoriaDao.existePorEquipoYFecha(
                                equipoId,
                                request.getFechaPartido())) {

                        throw new IllegalArgumentException(
                                        "Ya existe una convocatoria para este equipo en ese día");
                }

                validarJugadoresSeleccionados(
                                request.getJugadoresIds(),
                                jugadoresEquipo);

                Convocatoria convocatoria = new Convocatoria();

                convocatoria.setEquipoId(equipoId);
                convocatoria.setRival(
                                limpiar(request.getRival()));
                convocatoria.setCampo(
                                limpiar(request.getCampo()));
                convocatoria.setFechaPartido(
                                request.getFechaPartido());
                convocatoria.setHoraPartido(
                                limpiar(request.getHoraPartido()));
                convocatoria.setHoraConvocatoria(
                                limpiar(request.getHoraConvocatoria()));
                convocatoria.setLugarConvocatoria(
                                limpiar(request.getLugarConvocatoria()));
                convocatoria.setUsuarioEntrenadorId(
                                usuarioAppId);

                Convocatoria guardada = convocatoriaDao.guardar(convocatoria);

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

                for (Long jugadorId : jugadoresIds) {

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

                                comunicacionService.crearPrivada(
                                                comunicacion,
                                                List.of(usuarioId),
                                                convocatoria.getUsuarioEntrenadorId());
                        }
                }
        }

        @Override
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

                if (convocatoriaDao.existePorEquipoYFecha(
                                convocatoria.getEquipoId(),
                                request.getFechaPartido())) {

                        throw new IllegalArgumentException(
                                        "Ya existe una convocatoria para este equipo en ese día");
                }

                /*
                 * Comprobamos que el usuario puede gestionar
                 * el equipo de la convocatoria.
                 */
                if (!equipoGestionDao.puedeGestionarEquipo(
                                usuarioAppId,
                                convocatoria.getEquipoId())) {

                        throw new SecurityException(
                                        "No tienes permiso para actualizar esta convocatoria");
                }

                /*
                 * No permitimos cambiar el equipo.
                 */
                if (!convocatoria.getEquipoId().equals(request.getEquipoId())) {
                        throw new IllegalArgumentException(
                                        "No se puede cambiar el equipo de la convocatoria");
                }

                /*
                 * Los jugadores convocados NO se modifican.
                 * Se mantienen los jugadores asociados actualmente
                 * a la convocatoria.
                 */

                /*
                 * Actualizamos únicamente los datos del partido.
                 */
                convocatoria.setRival(
                                limpiar(request.getRival()));

                convocatoria.setCampo(
                                limpiar(request.getCampo()));

                convocatoria.setFechaPartido(
                                request.getFechaPartido());

                convocatoria.setHoraPartido(
                                limpiar(request.getHoraPartido()));

                convocatoria.setHoraConvocatoria(
                                limpiar(request.getHoraConvocatoria()));

                convocatoria.setLugarConvocatoria(
                                limpiar(request.getLugarConvocatoria()));

                convocatoriaDao.actualizar(convocatoria);

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

                for (ConvocatoriaJugador jugador : jugadores) {

                        String nombre = equipoGestionDao.obtenerNombreJugador(
                                        jugador.getJugadorId());

                        jugadoresResponse.add(
                                        new ConvocatoriaJugadorResponse(
                                                        jugador.getJugadorId(),
                                                        nombre));
                }

                response.setJugadores(jugadoresResponse);

                return response;
        }

        private void validarRequest(
                        ConvocatoriaGuardarRequest request) {

                if (request == null) {
                        throw new IllegalArgumentException(
                                        "La petición no puede ser nula");
                }

                if (request.getEquipoId() == null
                                || request.getEquipoId() <= 0) {

                        throw new IllegalArgumentException(
                                        "El equipo es obligatorio");
                }

                if (request.getRival() == null
                                || request.getRival().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "El rival es obligatorio");
                }

                if (request.getFechaPartido() == null) {
                        throw new IllegalArgumentException(
                                        "La fecha del partido es obligatoria");
                }

                if (request.getHoraPartido() == null
                                || request.getHoraPartido().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "La hora del partido es obligatoria");
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

                if (request.getEquipoId() == null
                                || request.getEquipoId() <= 0) {

                        throw new IllegalArgumentException(
                                        "El equipo es obligatorio");
                }

                if (request.getRival() == null
                                || request.getRival().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "El rival es obligatorio");
                }

                if (request.getFechaPartido() == null) {
                        throw new IllegalArgumentException(
                                        "La fecha del partido es obligatoria");
                }

                if (request.getHoraPartido() == null
                                || request.getHoraPartido().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "La hora del partido es obligatoria");
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