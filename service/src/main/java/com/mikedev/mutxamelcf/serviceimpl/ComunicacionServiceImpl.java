package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mikedev.mutxamelcf.dao.ComunicacionDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ComunicacionResponse;
import com.mikedev.mutxamelcf.model.DestinatarioComunicacion;
import com.mikedev.mutxamelcf.model.DestinatarioComunicacionResponse;
import com.mikedev.mutxamelcf.model.MensajeConversacionResponse;
import com.mikedev.mutxamelcf.model.NotificacionAppResponse;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.model.VinculoUsuarioApp;
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

        private final UsuarioAppVinculoDao usuarioAppVinculoDao;

        public ComunicacionServiceImpl(
                        ComunicacionDao comunicacionDao,
                        UsuarioAppService usuarioAppService,
                        NotificacionAppService notificacionAppService,
                        FcmPushService fcmPushService,
                        UsuarioAppVinculoDao usuarioAppVinculoDao) {

                this.comunicacionDao = comunicacionDao;
                this.usuarioAppService = usuarioAppService;
                this.notificacionAppService = notificacionAppService;
                this.fcmPushService = fcmPushService;
                this.usuarioAppVinculoDao = usuarioAppVinculoDao;
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

                if (destinatarios.size() > 1) {

                        throw new IllegalArgumentException(
                                        "Los mensajes privados solo pueden "
                                                        + "dirigirse a una persona");
                }

                /*
                 * Una comunicación se dirige a UN único tipo de
                 * destinatario: equipos, categorías o una persona en
                 * privado. No se pueden combinar entre sí.
                 */
                int modosSeleccionados = 0;

                if (!equipos.isEmpty()) {
                        modosSeleccionados++;
                }

                if (!categoriasNormalizadas.isEmpty()) {
                        modosSeleccionados++;
                }

                if (!destinatarios.isEmpty()) {
                        modosSeleccionados++;
                }

                if (modosSeleccionados == 0) {

                        throw new IllegalArgumentException(
                                        "Debe especificarse un equipo, una categoría "
                                                        + "o un destinatario");
                }

                if (modosSeleccionados > 1) {

                        throw new IllegalArgumentException(
                                        "Una comunicación solo puede dirigirse a "
                                                        + "equipos, a categorías o a una persona "
                                                        + "en privado, no a varios tipos a la vez");
                }

                boolean esPrivada = !destinatarios.isEmpty();

                if (esPrivada) {

                        /*
                         * Los mensajes privados no tienen título: se
                         * ignora lo que llegue en el request.
                         */
                        comunicacion.setTitulo(null);

                } else if (comunicacion.getTitulo() == null
                                || comunicacion.getTitulo().isBlank()) {

                        throw new IllegalArgumentException(
                                        "El título es obligatorio");
                }

                comunicacion.setTipo(esPrivada ? "PRIVADA" : "GRUPAL");

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

                /*
                 * Un mensaje privado no tiene título propio, pero la
                 * notificación push del sistema operativo necesita
                 * algo que mostrar: usamos el nombre de quien escribe.
                 */
                String tituloNotificacion = esPrivada
                                ? nombreParaNotificacion(usuarioId)
                                : comunicacion.getTitulo();

                generarNotificaciones(
                                comunicacionId,
                                tituloNotificacion,
                                comunicacion.getContenido(),
                                equipos,
                                categoriasNormalizadas,
                                destinatarios,
                                esPrivada ? usuarioId : null);

                return comunicacion;
        }

        private String nombreParaNotificacion(Long usuarioId) {

                List<VinculoUsuarioApp> vinculos = usuarioAppVinculoDao.obtenerVinculos(usuarioId.intValue());

                return vinculos.stream()
                                .map(VinculoUsuarioApp::getNombreCompleto)
                                .filter(nombre -> nombre != null && !nombre.isBlank())
                                .findFirst()
                                .orElse("Mensaje privado");
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

                if (comunicacion.getContenido() == null
                                || comunicacion.getContenido().trim().isEmpty()) {

                        throw new IllegalArgumentException(
                                        "El contenido es obligatorio");
                }

                /*
                 * El título solo es obligatorio para avisos de equipo
                 * o categoría; para mensajes privados se comprueba (y
                 * se fuerza a null) en crear(), una vez se sabe el
                 * modo.
                 */
                if (comunicacion.getTitulo() != null) {
                        comunicacion.setTitulo(
                                        comunicacion.getTitulo().trim());
                }

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
                        List<Long> destinatariosDirectos,
                        Long autorIdParaChatPrivado) {

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

                        /*
                         * "MENSAJE" es la categoría de preferencias que
                         * controla el único interruptor "Mensajes" que
                         * tiene la app (ver ajustes_page.dart); el tipo
                         * "COMUNICACION" de más abajo es el de enrutado
                         * en el cliente/lista de notificaciones y no debe
                         * tocarse.
                         */
                        if (!notificacionAppService.puedeRecibir(
                                        usuarioId,
                                        "MENSAJE")) {

                                continue;
                        }

                        notificacionAppService.crear(
                                        usuarioId,
                                        "COMUNICACION",
                                        titulo,
                                        contenido,
                                        comunicacionId);

                        if (autorIdParaChatPrivado != null) {

                                fcmPushService.enviarNotificacionAUsuario(
                                                usuarioId,
                                                "COMUNICACION",
                                                titulo,
                                                contenido,
                                                comunicacionId,
                                                Map.of(
                                                                "esPrivada", "true",
                                                                "autorId", autorIdParaChatPrivado.toString()));

                        } else {

                                fcmPushService.enviarNotificacionAUsuario(
                                                usuarioId,
                                                "COMUNICACION",
                                                titulo,
                                                contenido,
                                                comunicacionId);
                        }
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

                        /*
                         * "MENSAJE" es la categoría de preferencias que
                         * controla el único interruptor "Mensajes" que
                         * tiene la app (ver ajustes_page.dart); el tipo
                         * "COMUNICACION" de más abajo es el de enrutado
                         * en el cliente/lista de notificaciones y no debe
                         * tocarse.
                         */
                        if (!notificacionAppService.puedeRecibir(
                                        destinatarioId,
                                        "MENSAJE")) {

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

        @Override
        public List<ComunicacionResponse> listarParaUsuario(Long usuarioId) {

                if (usuarioId == null) {
                        throw new SecurityException("Usuario no autenticado");
                }

                List<ComunicacionResponse> resultado = obtenerParaUsuario(usuarioId)
                                .stream()
                                .map(this::construirResponseGrupal)
                                .collect(Collectors.toCollection(ArrayList::new));

                resultado.sort(
                                Comparator.comparing(
                                                ComunicacionResponse::getFecha,
                                                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())));

                return resultado;
        }

        @Override
        public List<ComunicacionResponse> listarConversacionesParaUsuario(Long usuarioId) {

                if (usuarioId == null) {
                        throw new SecurityException("Usuario no autenticado");
                }

                List<ComunicacionResponse> resultado = new ArrayList<>(
                                resumenConversacionesPrivadas(usuarioId));

                resultado.sort(
                                Comparator.comparing(
                                                ComunicacionResponse::getFecha,
                                                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())));

                return resultado;
        }

        @Override
        public List<ComunicacionResponse> listarEnviadasParaUsuario(Long usuarioId) {

                if (usuarioId == null) {
                        throw new SecurityException("Usuario no autenticado");
                }

                return obtenerEnviadasPorUsuario(usuarioId)
                                .stream()
                                .map(this::construirResponseGrupal)
                                .toList();
        }

        private static final int LIMITE_MENSAJES_POR_DEFECTO = 20;
        private static final int LIMITE_MENSAJES_MAXIMO = 50;

        @Override
        public List<MensajeConversacionResponse> obtenerConversacionPagina(
                        Long usuarioId,
                        Long otroUsuarioId,
                        Long antesDeId,
                        Integer limite) {

                if (usuarioId == null) {
                        throw new SecurityException("Usuario no autenticado");
                }

                if (otroUsuarioId == null) {
                        throw new IllegalArgumentException(
                                        "Falta el otro usuario de la conversación");
                }

                validarDestinatariosDirectos(usuarioId, List.of(otroUsuarioId));

                int limiteEfectivo = limite == null
                                ? LIMITE_MENSAJES_POR_DEFECTO
                                : Math.max(1, Math.min(LIMITE_MENSAJES_MAXIMO, limite));

                /*
                 * El DAO devuelve la página en orden descendente (más
                 * reciente primero, para poder limitar con
                 * FETCH FIRST/ID < ?); la invertimos para que el
                 * cliente pueda anteponerla directamente al hilo que ya
                 * tiene cargado (más antiguo primero).
                 */
                List<Comunicacion> mensajesDescendente = comunicacionDao.obtenerConversacionPagina(
                                usuarioId, otroUsuarioId, antesDeId, limiteEfectivo);

                List<Comunicacion> mensajesAscendente = new ArrayList<>(mensajesDescendente);
                Collections.reverse(mensajesAscendente);

                return mapearMensajes(mensajesAscendente, usuarioId);
        }

        private List<MensajeConversacionResponse> mapearMensajes(
                        List<Comunicacion> mensajes,
                        Long usuarioId) {

                Set<Long> idsNoLeidos = idsComunicacionNoLeidos(usuarioId);

                List<MensajeConversacionResponse> resultado = new ArrayList<>();

                for (Comunicacion mensaje : mensajes) {

                        MensajeConversacionResponse response = new MensajeConversacionResponse();

                        response.setId(mensaje.getId());
                        response.setContenido(mensaje.getContenido());
                        response.setFecha(fechaOrden(mensaje));
                        response.setAutorId(mensaje.getUsuarioAutorId());
                        response.setEsMia(usuarioId.equals(mensaje.getUsuarioAutorId()));
                        response.setLeida(!idsNoLeidos.contains(mensaje.getId()));

                        resultado.add(response);
                }

                return resultado;
        }

        @Override
        public void marcarConversacionLeida(
                        Long usuarioId,
                        Long otroUsuarioId) {

                if (usuarioId == null) {
                        throw new SecurityException("Usuario no autenticado");
                }

                if (otroUsuarioId == null) {
                        return;
                }

                List<Long> idsDelOtro = comunicacionDao.obtenerConversacion(usuarioId, otroUsuarioId)
                                .stream()
                                .filter(mensaje -> otroUsuarioId.equals(mensaje.getUsuarioAutorId()))
                                .map(Comunicacion::getId)
                                .toList();

                notificacionAppService.marcarLeidasPorReferencias(usuarioId, idsDelOtro);
        }

        private List<ComunicacionResponse> resumenConversacionesPrivadas(Long usuarioId) {

                List<Comunicacion> privadas = comunicacionDao.obtenerPrivadasDeUsuario(usuarioId);

                if (privadas.isEmpty()) {
                        return List.of();
                }

                /*
                 * Las privadas ya vienen ordenadas de más reciente a
                 * más antigua: nos quedamos con la primera aparición
                 * de cada contraparte (su último mensaje).
                 */
                Map<Long, Comunicacion> ultimoPorContraparte = new LinkedHashMap<>();

                for (Comunicacion mensaje : privadas) {
                        ultimoPorContraparte.putIfAbsent(mensaje.getContraparteId(), mensaje);
                }

                Set<Long> idsNoLeidos = idsComunicacionNoLeidos(usuarioId);

                Map<Long, Integer> noLeidosPorContraparte = new HashMap<>();

                for (Comunicacion mensaje : privadas) {

                        if (idsNoLeidos.contains(mensaje.getId())) {

                                noLeidosPorContraparte.merge(
                                                mensaje.getContraparteId(),
                                                1,
                                                Integer::sum);
                        }
                }

                List<ComunicacionResponse> resultado = new ArrayList<>();

                for (Comunicacion ultimo : ultimoPorContraparte.values()) {

                        ComunicacionResponse response = new ComunicacionResponse();

                        response.setId(ultimo.getId());
                        response.setTipo("PRIVADA");
                        response.setTitulo(null);
                        response.setContenido(ultimo.getContenido());
                        response.setFecha(fechaOrden(ultimo));
                        response.setAutorId(ultimo.getUsuarioAutorId());

                        NombreRol autor = resolverNombreYRol(ultimo.getUsuarioAutorId());
                        response.setAutorNombre(autor.nombre());
                        response.setAutorRol(autor.rol());

                        response.setContraparteId(ultimo.getContraparteId());

                        NombreRol contraparte = resolverNombreYRol(ultimo.getContraparteId());
                        response.setContraparteNombre(contraparte.nombre());
                        response.setContraparteRol(contraparte.rol());

                        int noLeidos = noLeidosPorContraparte.getOrDefault(ultimo.getContraparteId(), 0);
                        response.setNoLeidos(noLeidos);
                        response.setLeida(noLeidos == 0);

                        resultado.add(response);
                }

                return resultado;
        }

        private ComunicacionResponse construirResponseGrupal(Comunicacion grupal) {

                ComunicacionResponse response = new ComunicacionResponse();

                response.setId(grupal.getId());
                response.setTipo("GRUPAL");
                response.setTitulo(grupal.getTitulo());
                response.setContenido(grupal.getContenido());
                response.setFecha(fechaOrden(grupal));
                response.setAutorId(grupal.getUsuarioAutorId());

                NombreRol autor = resolverNombreYRol(grupal.getUsuarioAutorId());
                response.setAutorNombre(autor.nombre());
                response.setAutorRol(autor.rol());

                return response;
        }

        private LocalDateTime fechaOrden(Comunicacion comunicacion) {

                return comunicacion.getFechaPublicacion() != null
                                ? comunicacion.getFechaPublicacion()
                                : comunicacion.getFechaCreacion();
        }

        private Set<Long> idsComunicacionNoLeidos(Long usuarioId) {

                return notificacionAppService.obtenerNoLeidas(usuarioId)
                                .stream()
                                .filter(notificacion -> "COMUNICACION".equalsIgnoreCase(notificacion.getTipo()))
                                .map(NotificacionAppResponse::getReferenciaId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());
        }

        private record NombreRol(String nombre, String rol) {
        }

        private NombreRol resolverNombreYRol(Long usuarioId) {

                if (usuarioId == null) {
                        return new NombreRol(null, null);
                }

                List<VinculoUsuarioApp> vinculos = usuarioAppVinculoDao.obtenerVinculos(usuarioId.intValue());

                String nombre = vinculos.stream()
                                .map(VinculoUsuarioApp::getNombreCompleto)
                                .filter(n -> n != null && !n.isBlank())
                                .findFirst()
                                .orElse(null);

                if (nombre != null) {

                        String rol = vinculos.stream()
                                        .map(VinculoUsuarioApp::getTipo)
                                        .filter(tipo -> tipo != null && !tipo.isBlank())
                                        .distinct()
                                        .collect(Collectors.joining(", "));

                        return new NombreRol(nombre, rol.isBlank() ? null : rol);
                }

                if (usuarioAppService.tieneRol(usuarioId.intValue(), "ADMIN_APP")) {
                        return new NombreRol(nombreDesdeEmail(usuarioId), "ADMIN_APP");
                }

                if (usuarioAppService.tieneRol(usuarioId.intValue(), "COORDINADOR")) {
                        return new NombreRol(nombreDesdeEmail(usuarioId), "COORDINADOR");
                }

                return new NombreRol(nombreDesdeEmail(usuarioId), null);
        }

        private String nombreDesdeEmail(Long usuarioId) {

                UsuarioApp usuario = usuarioAppService.obtenerPorId(usuarioId.intValue());

                return usuario != null && usuario.getEmail() != null
                                ? usuario.getEmail()
                                : "Usuario";
        }

}