package com.mikedev.mutxamelcf.serviceimpl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.NotificacionAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.PreferenciasNotificacionService;

@ExtendWith(MockitoExtension.class)
class NotificacionAppServiceImplTest {

    @Mock
    private NotificacionAppDao notificacionAppDao;

    @Mock
    private PreferenciasNotificacionService preferenciasService;

    @Mock
    private UsuarioAppDao usuarioAppDao;

    @Mock
    private FcmPushService fcmPushService;

    private NotificacionAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificacionAppServiceImpl(
                notificacionAppDao, preferenciasService, usuarioAppDao, fcmPushService);
    }

    private UsuarioApp usuario(int id, boolean activo) {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(id);
        usuario.setActivo(activo);
        return usuario;
    }

    @Test
    void difundirATodosSoloAvisaAQuienEstaActivoYTieneLaPreferenciaEncendida() {

        when(usuarioAppDao.listarTodos()).thenReturn(List.of(
                usuario(1, true),
                usuario(2, true),
                usuario(3, false)));

        when(preferenciasService.puedeRecibir(1L, "NOTICIA")).thenReturn(true);
        when(preferenciasService.puedeRecibir(2L, "NOTICIA")).thenReturn(false);

        service.difundirATodos("NOTICIA", "Título", "Mensaje", 42L);

        verify(notificacionAppDao, times(1)).guardar(any());
        verify(fcmPushService).enviarNotificacionAUsuario(1L, "NOTICIA", "Título", "Mensaje", 42L);
        verify(fcmPushService, never()).enviarNotificacionAUsuario(eq(2L), anyString(), anyString(), anyString(), any());
        verify(fcmPushService, never()).enviarNotificacionAUsuario(eq(3L), anyString(), anyString(), anyString(), any());
        verify(preferenciasService, never()).puedeRecibir(eq(3L), anyString());
    }

    @Test
    void difundirATodosSinNadieElegibleNoEnviaNada() {

        when(usuarioAppDao.listarTodos()).thenReturn(List.of(usuario(1, true)));
        when(preferenciasService.puedeRecibir(1L, "RESULTADO")).thenReturn(false);

        service.difundirATodos("RESULTADO", "Título", "Mensaje", null);

        verify(notificacionAppDao, never()).guardar(any());
        verify(fcmPushService, never())
                .enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(), any());
    }
}
