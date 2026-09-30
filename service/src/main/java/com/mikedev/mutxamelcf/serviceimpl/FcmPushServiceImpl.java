package com.mikedev.mutxamelcf.serviceimpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
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

        // INF-07 (2.ª auditoría): estos envíos corren en hilos @Async desde
        // BE-02; con System.out/err en vez de un logger, un fallo de push
        // en esos hilos no queda asociado al resto de logs de la petición
        // (formato, nivel, timestamp) y es mucho más difícil de rastrear.
        private static final Logger logger = LoggerFactory.getLogger(FcmPushServiceImpl.class);

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

                        logger.info("Notificación FCM enviada correctamente: response={}", response);

                } catch (FirebaseMessagingException e) {

                        logger.error("Error FCM: {}", e.getMessage());

                        // Si el token ya no existe o ha sido invalidado,
                        // lo desactivamos para no volver a intentar enviarlo.
                        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {

                                logger.warn("Token FCM no válido, debe desactivarse: tokenFcm={}",
                                                truncarToken(tokenFcm));
                        }

                        // No propagamos la excepción.
                        // Un fallo de FCM no debe romper la operación
                        // principal que está realizando el usuario.

                } catch (Exception e) {

                        logger.error("Error al enviar notificación FCM: {}", e.getMessage());

                        // Tampoco propagamos el error.
                }
        }

        /**
         * Envía una notificación a todos los dispositivos activos
         * de un usuario.
         *
         * BE-02: @Async porque el envío hace una llamada de red a FCM por
         * cada dispositivo; ejecutarlo en el hilo de la petición retenía
         * la conexión de BD (Hikari) durante ese tiempo cuando se llamaba
         * dentro de una transacción, y en el partido en directo alargaba
         * la respuesta lo bastante como para que la app diera timeout y
         * el retransmisor reintentase la acción.
         */
        @Override
        @Async
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
        @Async
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
                        logger.debug("Usuario {} sin dispositivos FCM activos", usuarioId);

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

                        logger.info("Notificación FCM enviada: usuario={}, dispositivo={}, response={}",
                                        dispositivo.getUsuarioAppId(), dispositivo.getId(), response);

                } catch (FirebaseMessagingException e) {

                        logger.error("Error FCM para usuario={}, dispositivo={}: {}",
                                        dispositivo.getUsuarioAppId(), dispositivo.getId(), e.getMessage());

                        /*
                         * Firebase informa de que el token ya no es válido.
                         * Lo desactivamos para evitar futuros intentos.
                         */
                        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {

                                logger.warn("Token FCM no válido, desactivando dispositivo {}", dispositivo.getId());

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

                        logger.error("Error inesperado FCM para usuario={}: {}",
                                        dispositivo.getUsuarioAppId(), e.getMessage());

                        // Continuamos con el siguiente dispositivo.
                }
        }

        @Override
        @Async
        public void enviarATopic(
                        String topic,
                        String titulo,
                        String mensaje) {

                enviarATopic(topic, titulo, mensaje, Map.of());
        }

        /**
         * Publica una notificación en un topic de FCM (suscripción
         * anónima, gestionada por la propia app), capturando cualquier
         * error de FCM sin propagarlo, igual que el resto de envíos de
         * esta clase. @Async por el mismo motivo que enviarNotificacionAUsuario.
         */
        @Override
        @Async
        public void enviarATopic(
                        String topic,
                        String titulo,
                        String mensaje,
                        Map<String, String> datosExtra) {

                if (topic == null || topic.isBlank()) {
                        return;
                }

                Message message = Message.builder()
                                .setTopic(topic)
                                .setNotification(
                                                Notification.builder()
                                                                .setTitle(titulo)
                                                                .setBody(mensaje)
                                                                .build())
                                .putAllData(
                                                datosExtra != null ? datosExtra : Map.of())
                                .build();

                try {

                        String response = FirebaseMessaging
                                        .getInstance()
                                        .send(message);

                        logger.info("Notificación FCM enviada al topic {}: response={}", topic, response);

                } catch (FirebaseMessagingException e) {

                        logger.error("Error FCM al publicar en el topic {}: {}", topic, e.getMessage());

                        // No propagamos la excepción: un fallo de FCM no
                        // debe romper la operación principal (publicar
                        // una noticia, marcar un gol, etc.).

                } catch (Exception e) {

                        logger.error("Error inesperado FCM al publicar en el topic {}: {}", topic, e.getMessage());

                        // Tampoco propagamos el error.
                }
        }

        /**
         * Un token FCM no es una credencial de envío, pero sí identifica
         * un dispositivo concreto; se trunca antes de volcarlo a los
         * logs (los primeros caracteres bastan para correlacionar
         * incidencias sin registrar el identificador completo).
         */
        private static String truncarToken(String tokenFcm) {
                if (tokenFcm == null || tokenFcm.length() <= 12) {
                        return tokenFcm;
                }
                return tokenFcm.substring(0, 12) + "…";
        }
}
