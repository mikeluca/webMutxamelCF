package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ComunicacionDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;
import com.mikedev.mutxamelcf.service.FcmPushService;

@Service
public class ComunicacionServiceImpl
                implements ComunicacionService {

        private final ComunicacionDao comunicacionDao;

        private final UsuarioAppService usuarioAppService;

        private final NotificacionAppService notificacionAppService;

        private final FcmPushService fcmPushService;

        public ComunicacionServiceImpl(
                        ComunicacionDao comunicacionDao,
                        UsuarioAppService usuarioAppService,
                        NotificacionAppService notificacionAppService,
                        FcmPushService fcmPushService) {

                this.comunicacionDao = comunicacionDao;
                this.usuarioAppService = usuarioAppService;
                this.notificacionAppService = notificacionAppService;
                this.fcmPushService = fcmPushService;
        }

        @Override
        @Transactional
        public Comunicacion crear(
                        Comunicacion comunicacion,
                        List<Long> equipoIds,
                        List<String> categorias,
                        Long usuarioId) {

                validarDatosBasicos(comunicacion);

                List<Long> equipos = normalizarEquipos(equipoIds);

                List<String> categoriasNormalizadas = normalizarCategorias(categorias);

                if (equipos.isEmpty() && categoriasNormalizadas.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "Debe especificarse al menos un equipo o una categoría");
                }

                /*
                 * Comprobamos que todos los equipos existen.
                 */
                for (Long equipoId : equipos) {

                        if (!comunicacionDao.existeEquipo(equipoId)) {
                                throw new IllegalArgumentException(
                                                "El equipo con ID "
                                                                + equipoId
                                                                + " no existe");
                        }
                }

                /*
                 * Comprobamos que todas las categorías existen.
                 */
                for (String categoria : categoriasNormalizadas) {

                        if (!comunicacionDao.existeCategoria(categoria)) {
                                throw new IllegalArgumentException(
                                                "La categoría '"
                                                                + categoria
                                                                + "' no existe");
                        }
                }

                /*
                 * Comprobación de permisos.
                 */
                validarPermisos(
                                usuarioId,
                                equipos,
                                categoriasNormalizadas);

                /*
                 * Datos automáticos de la comunicación.
                 */
                comunicacion.setUsuarioAutorId(usuarioId);

                if (comunicacion.getFechaPublicacion() == null) {
                        comunicacion.setFechaPublicacion(
                                        LocalDateTime.now());
                }

                comunicacion.setActiva(1);

                /*
                 * Guardamos la comunicación principal.
                 */
                Long comunicacionId = comunicacionDao.guardar(comunicacion);

                /*
                 * Guardamos los equipos destinatarios.
                 */
                for (Long equipoId : equipos) {

                        comunicacionDao.guardarEquipo(
                                        comunicacionId,
                                        equipoId);
                }

                /*
                 * Guardamos las categorías destinatarias.
                 */
                for (String categoria : categoriasNormalizadas) {

                        comunicacionDao.guardarCategoria(
                                        comunicacionId,
                                        categoria);
                }

                comunicacion.setId(comunicacionId);

                generarNotificaciones(
                                comunicacionId,
                                comunicacion.getTitulo(),
                                comunicacion.getContenido(),
                                equipos,
                                categoriasNormalizadas);

                return comunicacion;
        }

        @Override
        public Comunicacion obtenerPorId(Long id) {

                if (id == null) {
                        throw new IllegalArgumentException(
                                        "El ID de la comunicación es obligatorio");
                }

                Comunicacion comunicacion = comunicacionDao.obtenerPorId(id);

                if (comunicacion == null) {
                        throw new IllegalArgumentException(
                                        "La comunicación no existe");
                }

                return comunicacion;
        }

        @Override
        public List<Comunicacion> obtenerTodas() {

                return comunicacionDao.obtenerTodas();
        }

        @Override
        @Transactional
        public void eliminar(
                        Long id,
                        Long usuarioId) {

                if (id == null) {
                        throw new IllegalArgumentException(
                                        "El ID de la comunicación es obligatorio");
                }

                if (usuarioId == null) {
                        throw new SecurityException(
                                        "Usuario no autenticado");
                }

                Comunicacion comunicacion = comunicacionDao.obtenerPorId(id);

                if (comunicacion == null) {
                        throw new IllegalArgumentException(
                                        "La comunicación no existe");
                }

                boolean esAdmin = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "ADMIN_APP");

                boolean esCoordinador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "COORDINADOR");

                boolean esAutor = comunicacion.getUsuarioAutorId() != null
                                && comunicacion.getUsuarioAutorId()
                                                .equals(usuarioId);

                /*
                 * ADMIN_APP y COORDINADOR pueden eliminar
                 * cualquier comunicación.
                 *
                 * El resto únicamente sus propias comunicaciones.
                 */
                if (!esAdmin && !esCoordinador && !esAutor) {
                        throw new SecurityException(
                                        "No tienes permiso para eliminar esta comunicación");
                }

                comunicacionDao.eliminar(id);
        }

        @Override
        public List<Comunicacion> obtenerParaUsuario(
                        Long usuarioId) {

                if (usuarioId == null) {
                        throw new SecurityException(
                                        "Usuario no autenticado");
                }

                /*
                 * ADMIN_APP:
                 * puede ver todas las comunicaciones.
                 */
                boolean esAdmin = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "ADMIN_APP");

                if (esAdmin) {
                        return comunicacionDao.obtenerTodas();
                }

                /*
                 * COORDINADOR:
                 * puede ver todas las comunicaciones.
                 */
                boolean esCoordinador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "COORDINADOR");

                if (esCoordinador) {
                        return comunicacionDao.obtenerTodas();
                }

                /*
                 * ENTRENADOR:
                 * recibe comunicaciones de sus equipos.
                 */
                boolean esEntrenador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "ENTRENADOR");

                if (esEntrenador) {

                        List<Long> equipos = comunicacionDao.obtenerEquiposDeEntrenador(
                                        usuarioId);

                        if (equipos.isEmpty()) {
                                return Collections.emptyList();
                        }

                        return comunicacionDao.obtenerPorEquiposYCategorias(
                                        equipos);
                }

                /*
                 * JUGADOR:
                 * recibe comunicaciones de sus equipos.
                 */
                boolean esJugador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "JUGADOR");

                if (esJugador) {

                        List<Long> equipos = comunicacionDao.obtenerEquiposDeJugador(
                                        usuarioId);

                        if (equipos.isEmpty()) {
                                return Collections.emptyList();
                        }

                        return comunicacionDao.obtenerPorEquiposYCategorias(
                                        equipos);
                }

                /*
                 * FAMILIAR:
                 * recibe comunicaciones de los equipos
                 * de sus jugadores.
                 */
                boolean esFamiliar = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "FAMILIAR");

                if (esFamiliar) {

                        List<Long> equipos = comunicacionDao.obtenerEquiposDeFamiliar(
                                        usuarioId);

                        if (equipos.isEmpty()) {
                                return Collections.emptyList();
                        }

                        return comunicacionDao.obtenerPorEquiposYCategorias(
                                        equipos);
                }

                /*
                 * Cualquier otro usuario:
                 * no recibe comunicaciones.
                 */
                return Collections.emptyList();
        }

        private void validarDatosBasicos(
                        Comunicacion comunicacion) {

                if (comunicacion == null) {
                        throw new IllegalArgumentException(
                                        "La comunicación es obligatoria");
                }

                if (comunicacion.getTitulo() == null
                                || comunicacion.getTitulo().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "El título es obligatorio");
                }

                if (comunicacion.getContenido() == null
                                || comunicacion.getContenido().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "El contenido es obligatorio");
                }

                comunicacion.setTitulo(
                                comunicacion.getTitulo().trim());

                comunicacion.setContenido(
                                comunicacion.getContenido().trim());
        }

        private void validarPermisos(
                        Long usuarioId,
                        List<Long> equipos,
                        List<String> categorias) {

                if (usuarioId == null) {
                        throw new SecurityException(
                                        "Usuario no autenticado");
                }

                int id = usuarioId.intValue();

                boolean esAdmin = usuarioAppService.tieneRol(
                                id,
                                "ADMIN_APP");

                boolean esCoordinador = usuarioAppService.tieneRol(
                                id,
                                "COORDINADOR");

                boolean esEntrenador = usuarioAppService.tieneRol(
                                id,
                                "ENTRENADOR");

                /*
                 * ADMIN_APP:
                 * puede crear cualquier comunicación.
                 */
                if (esAdmin) {
                        return;
                }

                /*
                 * COORDINADOR:
                 * puede crear cualquier comunicación.
                 */
                if (esCoordinador) {
                        return;
                }

                /*
                 * ENTRENADOR:
                 * únicamente puede enviar a sus propios equipos.
                 *
                 * No puede utilizar categorías como destinatario.
                 */
                if (esEntrenador) {

                        if (!categorias.isEmpty()) {
                                throw new SecurityException(
                                                "Los entrenadores no pueden crear "
                                                                + "comunicaciones por categoría");
                        }

                        validarEquiposDelEntrenador(
                                        usuarioId,
                                        equipos);

                        return;
                }

                /*
                 * Ningún otro rol puede crear comunicaciones.
                 */
                throw new SecurityException(
                                "No tienes permiso para crear comunicaciones");
        }

        private void validarEquiposDelEntrenador(
                        Long usuarioId,
                        List<Long> equiposSolicitados) {

                List<Long> equiposPermitidos = comunicacionDao.obtenerEquiposDeEntrenador(
                                usuarioId);

                Set<Long> permitidos = new HashSet<>(equiposPermitidos);

                for (Long equipoId : equiposSolicitados) {

                        if (!permitidos.contains(equipoId)) {

                                throw new SecurityException(
                                                "No tienes permiso para gestionar "
                                                                + "el equipo con ID "
                                                                + equipoId);
                        }
                }
        }

        private List<Long> normalizarEquipos(
                        List<Long> equipoIds) {

                if (equipoIds == null || equipoIds.isEmpty()) {
                        return Collections.emptyList();
                }

                return equipoIds.stream()
                                .filter(id -> id != null)
                                .distinct()
                                .collect(Collectors.toList());
        }

        private List<String> normalizarCategorias(
                        List<String> categorias) {

                if (categorias == null || categorias.isEmpty()) {
                        return Collections.emptyList();
                }

                List<String> resultado = new ArrayList<>();

                for (String categoria : categorias) {

                        if (categoria == null) {
                                continue;
                        }

                        String categoriaNormalizada = categoria.trim();

                        if (categoriaNormalizada.isEmpty()) {
                                continue;
                        }

                        boolean existe = resultado.stream()
                                        .anyMatch(c -> c.equalsIgnoreCase(categoriaNormalizada));
                        if (!existe) {
                                resultado.add(categoriaNormalizada);
                        }
                }

                return resultado;
        }

        private void generarNotificaciones(
                        Long comunicacionId,
                        String titulo,
                        String contenido,
                        List<Long> equipoIds,
                        List<String> categorias) {

                Set<Long> usuariosDestinatarios = new HashSet<>();

                /*
                 * Destinatarios por equipo
                 */
                for (Long equipoId : equipoIds) {

                        usuariosDestinatarios.addAll(
                                        comunicacionDao.obtenerUsuariosDelEquipo(equipoId));
                }

                /*
                 * Destinatarios por categoría
                 */
                for (String categoria : categorias) {

                        usuariosDestinatarios.addAll(
                                        comunicacionDao.obtenerUsuariosDeCategoria(categoria));
                }

                /*
                 * Creamos una única notificación por usuario y enviamos el push.
                 */
                for (Long usuarioId : usuariosDestinatarios) {

                        if (!notificacionAppService.puedeRecibir(
                                        usuarioId,
                                        "COMUNICACION")) {
                                continue;
                        }

                        notificacionAppService.crear(
                                        usuarioId,
                                        "COMUNICACION",
                                        titulo,
                                        contenido,
                                        comunicacionId);

                        fcmPushService.enviarNotificacionAUsuario(
                                        usuarioId,
                                        "COMUNICACION",
                                        titulo,
                                        contenido,
                                        comunicacionId);
                }
        }

        @Override
        public boolean puedeVer(
                        Long comunicacionId,
                        Long usuarioId) {

                if (comunicacionId == null || usuarioId == null) {
                        return false;
                }

                /*
                 * ADMIN_APP puede ver cualquier comunicación.
                 */
                boolean esAdmin = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "ADMIN_APP");

                if (esAdmin) {
                        return true;
                }

                /*
                 * COORDINADOR puede ver cualquier comunicación.
                 */
                boolean esCoordinador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "COORDINADOR");

                if (esCoordinador) {
                        return true;
                }

                /*
                 * Obtenemos la comunicación.
                 */
                Comunicacion comunicacion = comunicacionDao.obtenerPorId(comunicacionId);

                if (comunicacion == null
                                || comunicacion.getActiva() == null
                                || comunicacion.getActiva() != 1) {
                        return false;
                }

                /*
                 * ENTRENADOR:
                 * puede ver comunicaciones dirigidas a sus equipos.
                 */
                boolean esEntrenador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "ENTRENADOR");

                if (esEntrenador) {

                        List<Long> equipos = comunicacionDao.obtenerEquiposDeEntrenador(
                                        usuarioId);

                        if (equipos.isEmpty()) {
                                return false;
                        }

                        List<Comunicacion> comunicaciones = comunicacionDao.obtenerPorEquiposYCategorias(
                                        equipos);

                        return comunicaciones.stream()
                                        .anyMatch(c -> c.getId().equals(comunicacionId));
                }

                /*
                 * JUGADOR:
                 * puede ver comunicaciones dirigidas a sus equipos.
                 */
                boolean esJugador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "JUGADOR");

                if (esJugador) {

                        List<Long> equipos = comunicacionDao.obtenerEquiposDeJugador(
                                        usuarioId);

                        if (equipos.isEmpty()) {
                                return false;
                        }

                        List<Comunicacion> comunicaciones = comunicacionDao.obtenerPorEquiposYCategorias(
                                        equipos);

                        return comunicaciones.stream()
                                        .anyMatch(c -> c.getId().equals(comunicacionId));
                }

                /*
                 * FAMILIAR:
                 * puede ver comunicaciones dirigidas a los equipos
                 * de sus jugadores.
                 */
                boolean esFamiliar = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "FAMILIAR");

                if (esFamiliar) {

                        List<Long> equipos = comunicacionDao.obtenerEquiposDeFamiliar(
                                        usuarioId);

                        if (equipos.isEmpty()) {
                                return false;
                        }

                        List<Comunicacion> comunicaciones = comunicacionDao.obtenerPorEquiposYCategorias(
                                        equipos);

                        return comunicaciones.stream()
                                        .anyMatch(c -> c.getId().equals(comunicacionId));
                }

                /*
                 * Cualquier otro rol no puede ver comunicaciones.
                 */
                return false;
        }

}