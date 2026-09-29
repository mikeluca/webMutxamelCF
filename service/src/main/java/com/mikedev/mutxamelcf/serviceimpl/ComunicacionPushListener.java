package com.mikedev.mutxamelcf.serviceimpl;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mikedev.mutxamelcf.service.FcmPushService;

/**
 * BE-02: envia los push de una comunicacion solo despues de que la
 * transaccion de {@link ComunicacionServiceImpl#crear} haya confirmado.
 * No hace falta @Async aqui: FcmPushServiceImpl ya lo es, asi que este
 * metodo solo reparte el trabajo y vuelve de inmediato.
 */
@Component
public class ComunicacionPushListener {

    private final FcmPushService fcmPushService;

    public ComunicacionPushListener(FcmPushService fcmPushService) {
        this.fcmPushService = fcmPushService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComunicacionCreada(ComunicacionPushEvent event) {

        for (Long usuarioId : event.getUsuarioIds()) {

            if (event.getAutorIdParaChatPrivado() != null) {

                fcmPushService.enviarNotificacionAUsuario(
                        usuarioId,
                        "COMUNICACION",
                        event.getTitulo(),
                        event.getContenido(),
                        event.getComunicacionId(),
                        Map.of(
                                "esPrivada", "true",
                                "autorId", event.getAutorIdParaChatPrivado().toString()));

            } else {

                fcmPushService.enviarNotificacionAUsuario(
                        usuarioId,
                        "COMUNICACION",
                        event.getTitulo(),
                        event.getContenido(),
                        event.getComunicacionId());
            }
        }
    }
}
