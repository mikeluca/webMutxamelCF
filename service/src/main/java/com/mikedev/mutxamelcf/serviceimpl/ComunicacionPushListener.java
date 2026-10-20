package com.mikedev.mutxamelcf.serviceimpl;

import java.util.HashMap;
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

    private static final String TIPO_COMUNICACION = "COMUNICACION";

    private final FcmPushService fcmPushService;

    public ComunicacionPushListener(FcmPushService fcmPushService) {
        this.fcmPushService = fcmPushService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComunicacionCreada(ComunicacionPushEvent event) {

        // Un "tipo" en los datos extra sustituye al de comunicación (la app
        // navega por él); el resto va en el payload de datos.
        Map<String, String> datos = new HashMap<>(event.getDatosExtra());
        String tipo = datos.remove("tipo");

        if (tipo == null || tipo.isBlank()) {
            tipo = TIPO_COMUNICACION;
        }

        if (event.getAutorIdParaChatPrivado() != null) {
            datos.put("esPrivada", "true");
            datos.put("autorId", event.getAutorIdParaChatPrivado().toString());
        }

        for (Long usuarioId : event.getUsuarioIds()) {

            if (datos.isEmpty()) {

                fcmPushService.enviarNotificacionAUsuario(
                        usuarioId,
                        tipo,
                        event.getTitulo(),
                        event.getContenidoPush(),
                        event.getComunicacionId());

            } else {

                fcmPushService.enviarNotificacionAUsuario(
                        usuarioId,
                        tipo,
                        event.getTitulo(),
                        event.getContenidoPush(),
                        event.getComunicacionId(),
                        Map.copyOf(datos));
            }
        }
    }
}
