package com.mikedev.mutxamelcf.serviceimpl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.model.DispositivoApp;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceImplTest {

    @Mock
    private DispositivoAppDao dispositivoAppDao;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    private FcmPushServiceImpl service;
    private MockedStatic<FirebaseMessaging> firebaseMessagingStatic;

    @BeforeEach
    void setUp() {
        service = new FcmPushServiceImpl(dispositivoAppDao);
        firebaseMessagingStatic = mockStatic(FirebaseMessaging.class);
        firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
    }

    @AfterEach
    void tearDown() {
        firebaseMessagingStatic.close();
    }

    @Test
    void enviarNotificacionNoHaceNadaSiElTokenEsVacio() {
        service.enviarNotificacion("  ", "Titulo", "Mensaje");

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void enviarNotificacionEnviaElMensaje() throws Exception {
        when(firebaseMessaging.send(any(Message.class))).thenReturn("msg-id");

        service.enviarNotificacion("token-abc", "Titulo", "Mensaje");

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void enviarNotificacionNoPropagaLaExcepcionDeFirebase() throws Exception {
        FirebaseMessagingException excepcion = mock(FirebaseMessagingException.class);
        when(excepcion.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(excepcion);

        // No debe lanzar la excepcion, un fallo de push no debe romper la operacion
        service.enviarNotificacion("token-abc", "Titulo", "Mensaje");
    }

    @Test
    void enviarNotificacionAUsuarioNoHaceNadaSiElUsuarioEsNull() {
        service.enviarNotificacionAUsuario(null, "TIPO", "Titulo", "Mensaje", 1L);

        verifyNoInteractions(dispositivoAppDao);
    }

    @Test
    void enviarNotificacionAUsuarioNoHaceNadaSiNoHayDispositivos() {
        when(dispositivoAppDao.obtenerActivosPorUsuario(1L)).thenReturn(List.of());

        service.enviarNotificacionAUsuario(1L, "TIPO", "Titulo", "Mensaje", 2L);

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void enviarNotificacionAUsuarioEnviaATodosLosDispositivosActivos() throws Exception {
        DispositivoApp dispositivo1 = new DispositivoApp();
        dispositivo1.setId(1L);
        dispositivo1.setUsuarioAppId(1L);
        dispositivo1.setTokenFcm("token-1");

        DispositivoApp dispositivo2 = new DispositivoApp();
        dispositivo2.setId(2L);
        dispositivo2.setUsuarioAppId(1L);
        dispositivo2.setTokenFcm("token-2");

        when(dispositivoAppDao.obtenerActivosPorUsuario(1L)).thenReturn(List.of(dispositivo1, dispositivo2));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("msg-id");

        service.enviarNotificacionAUsuario(1L, "NOTICIA", "Titulo", "Mensaje", 5L, Map.of("extra", "dato"));

        verify(firebaseMessaging, times(2)).send(any(Message.class));
    }

    @Test
    void enviarNotificacionAUsuarioIgnoraDispositivosConTokenVacio() {
        DispositivoApp dispositivo = new DispositivoApp();
        dispositivo.setTokenFcm(" ");

        when(dispositivoAppDao.obtenerActivosPorUsuario(1L)).thenReturn(List.of(dispositivo));

        service.enviarNotificacionAUsuario(1L, "NOTICIA", "Titulo", "Mensaje", 5L);

        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void enviarNotificacionAUsuarioDesactivaElDispositivoSiElTokenNoEsValido() throws Exception {
        DispositivoApp dispositivo = new DispositivoApp();
        dispositivo.setId(1L);
        dispositivo.setUsuarioAppId(1L);
        dispositivo.setTokenFcm("token-1");

        when(dispositivoAppDao.obtenerActivosPorUsuario(1L)).thenReturn(List.of(dispositivo));

        FirebaseMessagingException excepcion = mock(FirebaseMessagingException.class);
        when(excepcion.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(excepcion);

        service.enviarNotificacionAUsuario(1L, "NOTICIA", "Titulo", "Mensaje", 5L);

        verify(dispositivoAppDao).desactivar(1L, "token-1");
    }

    @Test
    void enviarNotificacionAUsuarioNoDesactivaSiElErrorNoEsUnregistered() throws Exception {
        DispositivoApp dispositivo = new DispositivoApp();
        dispositivo.setId(1L);
        dispositivo.setUsuarioAppId(1L);
        dispositivo.setTokenFcm("token-1");

        when(dispositivoAppDao.obtenerActivosPorUsuario(1L)).thenReturn(List.of(dispositivo));

        FirebaseMessagingException excepcion = mock(FirebaseMessagingException.class);
        when(excepcion.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(excepcion);

        service.enviarNotificacionAUsuario(1L, "NOTICIA", "Titulo", "Mensaje", 5L);

        verify(dispositivoAppDao, never()).desactivar(any(), any());
    }
}
