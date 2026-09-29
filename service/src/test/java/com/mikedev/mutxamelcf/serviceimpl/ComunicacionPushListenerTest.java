package com.mikedev.mutxamelcf.serviceimpl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.service.FcmPushService;

class ComunicacionPushListenerTest {

    @Test
    void enviaUnPushPorCadaUsuarioSinDatosExtraCuandoNoEsChatPrivado() {
        FcmPushService fcmPushService = mock(FcmPushService.class);
        ComunicacionPushListener listener = new ComunicacionPushListener(fcmPushService);

        ComunicacionPushEvent event = new ComunicacionPushEvent(
                10L, "Titulo", "Contenido", Set.of(1L, 2L), null);

        listener.onComunicacionCreada(event);

        verify(fcmPushService).enviarNotificacionAUsuario(1L, "COMUNICACION", "Titulo", "Contenido", 10L);
        verify(fcmPushService).enviarNotificacionAUsuario(2L, "COMUNICACION", "Titulo", "Contenido", 10L);
        verify(fcmPushService, never()).enviarNotificacionAUsuario(any(), any(), any(), any(), any(), any());
    }

    @Test
    void incluyeLosDatosExtraDeChatPrivadoCuandoHayAutor() {
        FcmPushService fcmPushService = mock(FcmPushService.class);
        ComunicacionPushListener listener = new ComunicacionPushListener(fcmPushService);

        ComunicacionPushEvent event = new ComunicacionPushEvent(
                10L, "Ana", "Hola", Set.of(1L), 99L);

        listener.onComunicacionCreada(event);

        verify(fcmPushService).enviarNotificacionAUsuario(
                eq(1L), eq("COMUNICACION"), eq("Ana"), eq("Hola"), eq(10L),
                eq(Map.of("esPrivada", "true", "autorId", "99")));
    }
}
