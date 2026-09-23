package com.mikedev.mutxamelcf.serviceimpl;

import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.model.DispositivoApp;
import com.mikedev.mutxamelcf.service.FcmPushService;

import java.util.List;
import java.util.Map;

@Service
public class FcmPushServiceImpl implements FcmPushService {

        private final DispositivoAppDao dispositivoAppDao;

        public FcmPushServiceImpl(
                        DispositivoAppDao dispositivoAppDao) {
                this.dispositivoAppDao = dispositivoAppDao;
        }

        /**
         * Envío directo a un token FCM.
         *
         * Se mantiene temporalmente para las pruebas del endpoint
         * /api/app/fcm-test.
         */
        @Override
        public void enviarNotificacion(
                        String tokenFcm,
                        String titulo,
                        String mensaje) {

                if (tokenFcm == null || tokenFcm.isBlank()) {
                        return;
                }

                Message message = Message.builder()
                                .setToken(tokenFcm)
                                .setNotification(
                                                Notification.builder()
                                                                .setTitle(titulo)
                                                                .setBody(mensaje)
                                                                .build())
                                .build();

                try {
                        String response = FirebaseMessaging
                                        .getInstance()
                                        .send(message);

                        System.out.println(
                                        "NOTIFICACIÓN FCM ENVIADA CORRECTAMENTE: "
                                                        + response);

                } catch (FirebaseMessagingException e) {

                        System.err.println(
                                        "ERROR FCM: " + e.getMessage());

                        // Si el token ya no existe o ha sido invalidado,
                        // lo desactivamos para no volver a intentar enviarlo.
                        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {

                                System.err.println(
                                                "TOKEN FCM NO VÁLIDO. "
                                                                + "Debe desactivarse: "
                                                                + tokenFcm);
                        }

                        // No propagamos la excepción.
                        // Un fallo de FCM no debe romper la operación
                        // principal que está realizando el usuario.

                } catch (Exception e) {

                        System.err.println(
                                        "ERROR AL ENVIAR NOTIFICACIÓN FCM: "
                                                        + e.getMessage());

                        // Tampoco propagamos el error.
                }
        }

        /**
         * Envía una notificación a todos los dispositivos activos
         * de un usuario.
         */
        @Override
        public void enviarNotificacionAUsuario(
                        Long usuarioId,
                        String tipo,
                        String titulo,
                        String mensaje,
                        Long referenciaId) {

                enviarNotificacionAUsuario(
                                usuarioId,
                                tipo,
                                titulo,
                                mensaje,
                                referenciaId,
                                Map.of());
        }

        @Override
        public void enviarNotificacionAUsuario(
                        Long usuarioId,
                        String tipo,
                        String titulo,
                        String mensaje,
                        Long referenciaId,
                        Map<String, String> datosExtra) {

                if (usuarioId == null) {
                        return;
                }

                List<DispositivoApp> dispositivos = dispositivoAppDao.obtenerActivosPorUsuario(usuarioId);

                if (dispositivos == null || dispositivos.isEmpty()) {
                        System.out.println(
                                        "USUARIO " + usuarioId
                                                        + " SIN DISPOSITIVOS FCM ACTIVOS");

                        return;
                }

                for (DispositivoApp dispositivo : dispositivos) {
                        enviarADispositivo(dispositivo, tipo, titulo, mensaje, referenciaId, datosExtra);
                }
        }

        /**
         * Construye y envía el mensaje a un único dispositivo, capturando
         * cualquier error de FCM sin propagarlo (un fallo de push no debe
         * romper la operación principal), y desactivando el dispositivo si
         * Firebase informa de que su token ya no es válido.
         */
        private void enviarADispositivo(
                        DispositivoApp dispositivo,
                        String tipo,
                        String titulo,
                        String mensaje,
                        Long referenciaId,
                        Map<String, String> datosExtra) {

                String tokenFcm = dispositivo.getTokenFcm();

                if (tokenFcm == null || tokenFcm.isBlank()) {
                        return;
                }

                Message message = Message.builder()
                                .setToken(tokenFcm)
                                .setNotification(
                                                Notification.builder()
                                                                .setTitle(titulo)
                                                                .setBody(mensaje)
                                                                .build())
                                .putData(
                                                "tipo",
                                                tipo != null ? tipo : "")
                                .putData(
                                                "referenciaId",
                                                referenciaId != null
                                                                ? referenciaId.toString()
                                                                : "")
                                .putAllData(
                                                datosExtra != null ? datosExtra : Map.of())
                                .build();

                try {

                        String response = FirebaseMessaging
                                        .getInstance()
                                        .send(message);

                        System.out.println(
                                        "NOTIFICACIÓN FCM ENVIADA: "
                                                        + "usuario=" + dispositivo.getUsuarioAppId()
                                                        + ", dispositivo=" + dispositivo.getId()
                                                        + ", response=" + response);

                } catch (FirebaseMessagingException e) {

                        System.err.println(
                                        "ERROR FCM PARA USUARIO "
                                                        + dispositivo.getUsuarioAppId()
                                                        + ", dispositivo="
                                                        + dispositivo.getId()
                                                        + ": "
                                                        + e.getMessage());

                        /*
                         * Firebase informa de que el token ya no es válido.
                         * Lo desactivamos para evitar futuros intentos.
                         */
                        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {

                                System.err.println(
                                                "TOKEN FCM NO VÁLIDO. "
                                                                + "Desactivando dispositivo "
                                                                + dispositivo.getId());

                                dispositivoAppDao.desactivar(
                                                dispositivo.getUsuarioAppId(),
                                                tokenFcm);
                        }

                        /*
                         * IMPORTANTE:
                         *
                         * No lanzamos la excepción.
                         *
                         * Si FCM falla, la comunicación y la notificación
                         * interna siguen siendo válidas.
                         */

                } catch (Exception e) {

                        System.err.println(
                                        "ERROR INESPERADO FCM PARA USUARIO "
                                                        + dispositivo.getUsuarioAppId()
                                                        + ": "
                                                        + e.getMessage());

                        // Continuamos con el siguiente dispositivo.
                }
        }
}