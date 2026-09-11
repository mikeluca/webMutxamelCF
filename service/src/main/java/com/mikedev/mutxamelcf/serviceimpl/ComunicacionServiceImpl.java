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
import com.mikedev.mutxamelcf.model.DestinatarioComunicacion;
import com.mikedev.mutxamelcf.model.DestinatarioComunicacionResponse;
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
                        List<Long> destinatariosIds,
                        Long usuarioId) {

                validarDatosBasicos(comunicacion);

                List<Long> equipos = normalizarEquipos(equipoIds);

                List<String> categoriasNormalizadas = normalizarCategorias(categorias);

                List<Long> destinatarios = normalizarDestinatarios(destinatariosIds);

                validarDestinatariosDirectos(
                                usuarioId,
                                destinatarios);

                if (equipos.isEmpty()
                                && categoriasNormalizadas.isEmpty()
                                && destinatarios.isEmpty()) {

                        throw new IllegalArgumentException(
                                        "Debe especificarse al menos un equipo, "
                                                        + "una categoría o un destinatario");
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
                                categoriasNormalizadas,
                                destinatarios);

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

                for (Long destinatarioId : destinatarios) {
                        comunicacionDao.guardarUsuario(
                                        comunicacionId,
                                        destinatarioId);
                }

                comunicacion.setId(comunicacionId);

                generarNotificaciones(
                                comunicacionId,
                                comunicacion.getTitulo(),
                                comunicacion.getContenido(),
                                equipos,
                                categoriasNormalizadas,
                                destinatarios);

                return comunicacion;
        }

        private void validarDestinatariosDirectos(
                        Long usuarioId,
                        List<Long> destinatarios) {

                if (destinatarios == null || destinatarios.isEmpty()) {
                        return;
                }

                List<Long> permitidos = comunicacionDao.obtenerDestinatariosDirectosPermitidos(
                                usuarioId);

                Set<Long> permitidosSet = new HashSet<>(permitidos);

                for (Long destinatarioId : destinatarios) {

                        if (!permitidosSet.contains(destinatarioId)) {

                                throw new SecurityException(
                                                "No tienes permiso para enviar una comunicación "
                                                                + "al usuario con ID "
                                                                + destinatarioId);
                        }
                }
        }

        @Override
        public Comunicacion obtenerPorId(
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

                /*
                 * ADMIN_APP puede ver cualquier comunicación.
                 */
                boolean esAdmin = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "ADMIN_APP");

                if (esAdmin) {
                        return comunicacion;
                }

                /*
                 * COORDINADOR puede ver cualquier comunicación.
                 */
                boolean esCoordinador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "COORDINADOR");

                if (esCoordinador) {
                        return comunicacion;
                }

                /*
                 * El autor puede ver su propia comunicación.
                 */
                if (comunicacion.getUsuarioAutorId() != null
                                && comunicacion.getUsuarioAutorId().equals(usuarioId)) {

                        return comunicacion;
                }

                /*
                 * Comprobamos si es destinatario directo.
                 */
                if (comunicacionDao.usuarioPuedeVerDirectamente(
                                id,
                                usuarioId)) {

                        return comunicacion;
                }

                /*
                 * Si no es destinatario directo, comprobamos
                 * si pertenece a uno de los equipos destinatarios.
                 */
                List<Long> equiposUsuario = new ArrayList<>();

                equiposUsuario.addAll(
                                comunicacionDao.obtenerEquiposDeEntrenador(
                                                usuarioId));

                equiposUsuario.addAll(
                                comunicacionDao.obtenerEquiposDeJugador(
                                                usuarioId));

                equiposUsuario.addAll(
                                comunicacionDao.obtenerEquiposDeFamiliar(
                                                usuarioId));

                equiposUsuario = equiposUsuario.stream()
                                .filter(e -> e != null)
                                .distinct()
                                .collect(Collectors.toList());

                if (!equiposUsuario.isEmpty()) {

                        List<Comunicacion> comunicaciones = comunicacionDao.obtenerPorEquiposYCategorias(
                                        equiposUsuario);

                        boolean puedeVer = comunicaciones.stream()
                                        .anyMatch(c -> c.getId().equals(id));

                        if (puedeVer) {
                                return comunicacion;
                        }
                }

                throw new SecurityException(
                                "No tienes permiso para ver esta comunicación");
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
                 * ============================================================
                 * ADMIN_APP
                 *
                 * Puede ver todas las comunicaciones.
                 * ============================================================
                 */
                boolean esAdmin = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "ADMIN_APP");

                if (esAdmin) {
                        return comunicacionDao.obtenerTodas();
                }

                /*
                 * ============================================================
                 * COORDINADOR
                 *
                 * Solo puede ver comunicaciones dirigidas directamente
                 * a él.
                 *
                 * NO puede ver las comunicaciones de todos los equipos.
                 * ============================================================
                 */
                boolean esCoordinador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "COORDINADOR");

                if (esCoordinador) {
                        return comunicacionDao.obtenerPorUsuarioDirecto(
                                        usuarioId);
                }

                /*
                 * ============================================================
                 * Comunicaciones recibidas por equipo/categoría.
                 * ============================================================
                 */
                List<Comunicacion> comunicacionesEquipo = new ArrayList<>();

                /*
                 * ============================================================
                 * ENTRENADOR
                 * ============================================================
                 */
                boolean esEntrenador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "ENTRENADOR");

                if (esEntrenador) {

                        List<Long> equipos = comunicacionDao.obtenerEquiposDeEntrenador(
                                        usuarioId);

                        if (!equipos.isEmpty()) {

                                comunicacionesEquipo.addAll(
                                                comunicacionDao.obtenerPorEquiposYCategorias(
                                                                equipos));
                        }
                }

                /*
                 * ============================================================
                 * JUGADOR
                 * ============================================================
                 */
                boolean esJugador = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "JUGADOR");

                if (esJugador) {

                        List<Long> equipos = comunicacionDao.obtenerEquiposDeJugador(
                                        usuarioId);

                        if (!equipos.isEmpty()) {

                                comunicacionesEquipo.addAll(
                                                comunicacionDao.obtenerPorEquiposYCategorias(
                                                                equipos));
                        }
                }

                /*
                 * ============================================================
                 * FAMILIAR
                 * ============================================================
                 */
                boolean esFamiliar = usuarioAppService.tieneRol(
                                usuarioId.intValue(),
                                "FAMILIAR");

                if (esFamiliar) {

                        List<Long> equipos = comunicacionDao.obtenerEquiposDeFamiliar(
                                        usuarioId);

                        if (!equipos.isEmpty()) {

                                comunicacionesEquipo.addAll(
                                                comunicacionDao.obtenerPorEquiposYCategorias(
                                                                equipos));
                        }
                }

                /*
                 * ============================================================
                 * Comunicaciones enviadas directamente a este usuario.
                 *
                 * Se consultan siempre para los roles receptores.
                 * ============================================================
                 */
                List<Comunicacion> comunicacionesDirectas = comunicacionDao.obtenerPorUsuarioDirecto(
                                usuarioId);

                /*
                 * Si no tiene ningún rol receptor válido,
                 * solo devolvemos las comunicaciones directas.
                 */
                if (!esEntrenador
                                && !esJugador
                                && !esFamiliar) {

                        return comunicacionesDirectas;
                }

                return combinarComunicaciones(
                                comunicacionesEquipo,
                                comunicacionesDirectas);
        }

        private List<Comunicacion> combinarComunicaciones(
                        List<Comunicacion> comunicacionesNormales,
                        List<Comunicacion> comunicacionesDirectas) {

                List<Comunicacion> resultado = new ArrayList<>();

                Set<Long> ids = new HashSet<>();

                for (Comunicacion comunicacion : comunicacionesNormales) {

                        if (ids.add(comunicacion.getId())) {
                                resultado.add(comunicacion);
                        }
                }

                for (Comunicacion comunicacion : comunicacionesDirectas) {

                        if (ids.add(comunicacion.getId())) {
                                resultado.add(comunicacion);
                        }
                }

                resultado.sort(
                                (a, b) -> {

                                        if (a.getFechaPublicacion() == null
                                                        && b.getFechaPublicacion() == null) {
                                                return 0;
                                        }

                                        if (a.getFechaPublicacion() == null) {
                                                return 1;
                                        }

                                        if (b.getFechaPublicacion() == null) {
                                                return -1;
                                        }

                                        return b.getFechaPublicacion()
                                                        .compareTo(a.getFechaPublicacion());
                                });

                return resultado;
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
                        List<String> categorias,
                        List<Long> destinatarios) {

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

                boolean esJugador = usuarioAppService.tieneRol(
                                id,
                                "JUGADOR");

                boolean esFamiliar = usuarioAppService.tieneRol(
                                id,
                                "FAMILIAR");

                /*
                 * ============================================================
                 * ADMIN_APP
                 *
                 * Puede crear cualquier comunicación.
                 * ============================================================
                 */
                if (esAdmin) {
                        return;
                }

                /*
                 * ============================================================
                 * COORDINADOR
                 *
                 * Puede crear cualquier comunicación.
                 * ============================================================
                 */
                if (esCoordinador) {
                        return;
                }

                /*
                 * ============================================================
                 * ENTRENADOR
                 *
                 * Puede enviar a sus propios equipos.
                 * No puede utilizar categorías.
                 *
                 * También puede enviar comunicaciones directas a los
                 * destinatarios que el DAO haya autorizado.
                 * ============================================================
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
                 * ============================================================
                 * JUGADOR
                 *
                 * Solo puede crear comunicaciones directas.
                 *
                 * No puede enviar comunicaciones por equipo ni categoría.
                 * Los destinatarios directos ya han sido validados mediante
                 * validarDestinatariosDirectos().
                 * ============================================================
                 */
                if (esJugador) {

                        if (!equipos.isEmpty()
                                        || !categorias.isEmpty()) {

                                throw new SecurityException(
                                                "Los jugadores solo pueden "
                                                                + "enviar comunicaciones directas");
                        }

                        if (destinatarios.isEmpty()) {
                                throw new IllegalArgumentException(
                                                "Debes seleccionar al menos un destinatario");
                        }

                        return;
                }

                /*
                 * ============================================================
                 * FAMILIAR
                 *
                 * Solo puede crear comunicaciones directas.
                 *
                 * No puede enviar comunicaciones por equipo ni categoría.
                 * Los destinatarios directos ya han sido validados mediante
                 * validarDestinatariosDirectos().
                 * ============================================================
                 */
                if (esFamiliar) {

                        if (!equipos.isEmpty()
                                        || !categorias.isEmpty()) {

                                throw new SecurityException(
                                                "Los familiares solo pueden "
                                                                + "enviar comunicaciones directas");
                        }

                        if (destinatarios.isEmpty()) {
                                throw new IllegalArgumentException(
                                                "Debes seleccionar al menos un destinatario");
                        }

                        return;
                }

                /*
                 * ============================================================
                 * Cualquier otro rol no puede crear comunicaciones.
                 * ============================================================
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

        private List<Long> normalizarDestinatarios(
                        List<Long> destinatariosIds) {

                if (destinatariosIds == null || destinatariosIds.isEmpty()) {
                        return Collections.emptyList();
                }

                return destinatariosIds.stream()
                                .filter(id -> id != null)
                                .distinct()
                                .collect(Collectors.toList());
        }

        private void generarNotificaciones(
                        Long comunicacionId,
                        String titulo,
                        String contenido,
                        List<Long> equipoIds,
                        List<String> categorias,
                        List<Long> destinatariosDirectos) {

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
                 * Destinatarios directos
                 */
                if (destinatariosDirectos != null) {

                        usuariosDestinatarios.addAll(
                                        destinatariosDirectos);
                }

                /*
                 * Creamos una única notificación por usuario
                 * y enviamos el push.
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
                 * El destinatario directo puede ver la comunicación.
                 */
                if (comunicacionDao.usuarioPuedeVerDirectamente(
                                comunicacionId,
                                usuarioId)) {

                        return true;
                }

                /*
                 * El autor puede ver su propia comunicación.
                 */
                if (comunicacion.getUsuarioAutorId() != null
                                && comunicacion.getUsuarioAutorId().equals(usuarioId)) {

                        return true;
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

        @Override
        @Transactional
        public Comunicacion crearPrivada(
                        Comunicacion comunicacion,
                        List<Long> usuariosDestino,
                        Long usuarioId) {

                validarDatosBasicos(comunicacion);

                if (usuarioId == null) {
                        throw new SecurityException(
                                        "Usuario no autenticado");
                }

                if (usuariosDestino == null
                                || usuariosDestino.isEmpty()) {

                        throw new IllegalArgumentException(
                                        "Debe existir al menos un destinatario");
                }

                List<Long> destinatarios = usuariosDestino.stream()
                                .filter(id -> id != null)
                                .distinct()
                                .collect(Collectors.toList());

                if (destinatarios.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "Debe existir al menos un destinatario");
                }

                comunicacion.setUsuarioAutorId(usuarioId);

                if (comunicacion.getFechaPublicacion() == null) {
                        comunicacion.setFechaPublicacion(
                                        LocalDateTime.now());
                }

                comunicacion.setActiva(1);

                /*
                 * 1. Crear COMUNICACION.
                 */
                Long comunicacionId = comunicacionDao.guardar(
                                comunicacion);

                /*
                 * 2. Asociarla directamente a cada usuario.
                 */
                for (Long destinatarioId : destinatarios) {

                        comunicacionDao.guardarUsuario(
                                        comunicacionId,
                                        destinatarioId);
                }

                /*
                 * 3. Crear NOTIFICACION_APP y enviar FCM.
                 */
                for (Long destinatarioId : destinatarios) {

                        if (!notificacionAppService.puedeRecibir(
                                        destinatarioId,
                                        "COMUNICACION")) {

                                continue;
                        }

                        notificacionAppService.crear(
                                        destinatarioId,
                                        "COMUNICACION",
                                        comunicacion.getTitulo(),
                                        comunicacion.getContenido(),
                                        comunicacionId);

                        fcmPushService.enviarNotificacionAUsuario(
                                        destinatarioId,
                                        "COMUNICACION",
                                        comunicacion.getTitulo(),
                                        comunicacion.getContenido(),
                                        comunicacionId);
                }

                comunicacion.setId(comunicacionId);

                return comunicacion;
        }

        @Override
        public List<Comunicacion> obtenerEnviadasPorUsuario(
                        Long usuarioId) {

                if (usuarioId == null) {
                        throw new SecurityException(
                                        "Usuario no autenticado");
                }

                return comunicacionDao.obtenerEnviadasPorUsuario(
                                usuarioId);
        }

        @Override
        public List<DestinatarioComunicacionResponse> obtenerDestinatariosDirectos(
                        Long usuarioId) {

                if (usuarioId == null) {
                        throw new SecurityException("Usuario no autenticado");
                }

                List<DestinatarioComunicacion> destinatarios = comunicacionDao.obtenerDestinatariosDirectos(usuarioId);

                return destinatarios.stream()
                                .map(destinatario -> {
                                        DestinatarioComunicacionResponse response = new DestinatarioComunicacionResponse();

                                        response.setId(destinatario.getId());
                                        response.setNombre(destinatario.getNombre());
                                        response.setApellidos(destinatario.getApellidos());
                                        response.setRol(destinatario.getRol());

                                        return response;
                                })
                                .toList();
        }

}