package com.mikedev.mutxamelcf.serviceimpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.mikedev.mutxamelcf.dao.NotificacionAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.model.NotificacionApp;
import com.mikedev.mutxamelcf.model.NotificacionAppResponse;
import com.mikedev.mutxamelcf.model.NotificacionesNoLeidasResponse;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.PreferenciasNotificacionService;

@Service
public class NotificacionAppServiceImpl
                implements NotificacionAppService {

        private final NotificacionAppDao notificacionAppDao;

        private final PreferenciasNotificacionService preferenciasService;

        private final UsuarioAppDao usuarioAppDao;

        private final FcmPushService fcmPushService;

        public NotificacionAppServiceImpl(
                        NotificacionAppDao notificacionAppDao,
                        PreferenciasNotificacionService preferenciasService,
                        UsuarioAppDao usuarioAppDao,
                        FcmPushService fcmPushService) {

                this.notificacionAppDao = notificacionAppDao;
                this.preferenciasService = preferenciasService;
                this.usuarioAppDao = usuarioAppDao;
                this.fcmPushService = fcmPushService;
        }

        @Override
        public NotificacionApp crear(
                        Long usuarioId,
                        String tipo,
                        String titulo,
                        String mensaje,
                        Long referenciaId) {

                if (usuarioId == null) {
                        throw new IllegalArgumentException(
                                        "El usuario es obligatorio");
                }

                if (tipo == null || tipo.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                        "El tipo de notificación es obligatorio");
                }

                if (titulo == null || titulo.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                        "El título es obligatorio");
                }

                if (mensaje == null || mensaje.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                        "El mensaje es obligatorio");
                }

                NotificacionApp notificacion = new NotificacionApp();

                notificacion.setUsuarioAppId(usuarioId);
                notificacion.setTipo(tipo.trim().toUpperCase());
                notificacion.setTitulo(titulo.trim());
                notificacion.setMensaje(mensaje.trim());
                notificacion.setReferenciaId(referenciaId);
                notificacion.setFecha(LocalDateTime.now());
                notificacion.setLeida(0);

                notificacionAppDao.guardar(notificacion);

                return notificacion;
        }

        @Override
        public List<NotificacionAppResponse> obtenerPorUsuario(
                        Long usuarioId) {

                return notificacionAppDao
                                .obtenerPorUsuario(usuarioId)
                                .stream()
                                .map(this::convertirAResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public List<NotificacionAppResponse> obtenerNoLeidas(
                        Long usuarioId) {

                return notificacionAppDao
                                .obtenerNoLeidas(usuarioId)
                                .stream()
                                .map(this::convertirAResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public void marcarComoLeida(
                        Long id,
                        Long usuarioId) {

                if (id == null || usuarioId == null) {
                        throw new IllegalArgumentException(
                                        "Los datos de la notificación son obligatorios");
                }

                notificacionAppDao.marcarComoLeida(
                                id,
                                usuarioId);
        }

        @Override
        public void marcarTodasComoLeidas(
                        Long usuarioId) {

                if (usuarioId == null) {
                        throw new IllegalArgumentException(
                                        "El usuario es obligatorio");
                }

                notificacionAppDao.marcarTodasComoLeidas(
                                usuarioId);
        }

        @Override
        public void marcarLeidasPorReferencias(
                        Long usuarioId,
                        List<Long> referenciaIds) {

                if (usuarioId == null) {
                        throw new IllegalArgumentException(
                                        "El usuario es obligatorio");
                }

                notificacionAppDao.marcarLeidasPorReferencias(
                                usuarioId,
                                referenciaIds);
        }

        @Override
        public NotificacionesNoLeidasResponse contarNoLeidas(
                        Long usuarioId) {

                int cantidad = notificacionAppDao.contarNoLeidas(usuarioId);

                return new NotificacionesNoLeidasResponse(cantidad);
        }

        private NotificacionAppResponse convertirAResponse(
                        NotificacionApp notificacion) {

                NotificacionAppResponse response = new NotificacionAppResponse();

                response.setId(notificacion.getId());
                response.setTipo(notificacion.getTipo());
                response.setTitulo(notificacion.getTitulo());
                response.setMensaje(notificacion.getMensaje());
                response.setReferenciaId(notificacion.getReferenciaId());
                response.setFecha(notificacion.getFecha());
                response.setLeida(
                                notificacion.getLeida() != null
                                                && notificacion.getLeida() == 1);

                return response;
        }

        @Override
        public boolean puedeRecibir(
                        Long usuarioId,
                        String tipo) {

                return preferenciasService.puedeRecibir(
                                usuarioId,
                                tipo);
        }

        @Override
        public int contarComunicacionesNoLeidas(Long usuarioId) {
                return notificacionAppDao.contarComunicacionesNoLeidas(usuarioId);
        }

        @Override
        public void difundirATodos(
                        String tipo,
                        String titulo,
                        String mensaje,
                        Long referenciaId) {

                List<UsuarioApp> usuarios = usuarioAppDao.listarTodos();

                for (UsuarioApp usuario : usuarios) {

                        if (!usuario.isActivo()) {
                                continue;
                        }

                        Long usuarioId = (long) usuario.getId();

                        if (!puedeRecibir(usuarioId, tipo)) {
                                continue;
                        }

                        crear(usuarioId, tipo, titulo, mensaje, referenciaId);

                        fcmPushService.enviarNotificacionAUsuario(
                                        usuarioId,
                                        tipo,
                                        titulo,
                                        mensaje,
                                        referenciaId);
                }
        }
}