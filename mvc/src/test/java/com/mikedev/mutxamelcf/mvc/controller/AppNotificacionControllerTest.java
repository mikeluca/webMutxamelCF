package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.NotificacionAppResponse;
import com.mikedev.mutxamelcf.model.NotificacionesNoLeidasResponse;
import com.mikedev.mutxamelcf.service.NotificacionAppService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppNotificacionControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void obtenerDevuelve401SinAutenticacion() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.obtener(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerDevuelveLasNotificaciones() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        when(service.obtenerPorUsuario(1L)).thenReturn(List.of(new NotificacionAppResponse()));

        ResponseEntity<?> response = controller.obtener(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void obtenerDevuelve500AnteUnErrorInesperado() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        when(service.obtenerPorUsuario(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtener(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerNoLeidasDevuelveLaLista() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        when(service.obtenerNoLeidas(1L)).thenReturn(List.of(new NotificacionAppResponse()));

        assertThat(controller.obtenerNoLeidas(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void contarNoLeidasDevuelveElConteo() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        when(service.contarNoLeidas(1L)).thenReturn(new NotificacionesNoLeidasResponse());

        assertThat(controller.contarNoLeidas(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void marcarComoLeidaDevuelve400SiElServicioLanzaIllegalArgument() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        doThrow(new IllegalArgumentException("no existe")).when(service).marcarComoLeida(5L, 1L);

        assertThat(controller.marcarComoLeida(autenticado("1"), 5L).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void marcarComoLeidaDelegaEnElServicio() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        ResponseEntity<?> response = controller.marcarComoLeida(autenticado("1"), 5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).marcarComoLeida(5L, 1L);
    }

    @Test
    void marcarTodasComoLeidasDelegaEnElServicio() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        controller.marcarTodasComoLeidas(autenticado("1"));

        verify(service).marcarTodasComoLeidas(1L);
    }

    @Test
    void contarComunicacionesNoLeidasDevuelveElConteo() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        when(service.contarComunicacionesNoLeidas(1L)).thenReturn(3);

        ResponseEntity<?> response = controller.contarComunicacionesNoLeidas(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(3);
    }

    @Test
    void contarComunicacionesNoLeidasDevuelve401SinAutenticacion() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.contarComunicacionesNoLeidas(null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.obtener(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerNoLeidasDevuelve401SinAutenticacion() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.obtenerNoLeidas(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerNoLeidasDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.obtenerNoLeidas(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerNoLeidasDevuelve500AnteUnErrorInesperado() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);
        when(service.obtenerNoLeidas(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerNoLeidas(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void contarNoLeidasDevuelve401SinAutenticacion() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.contarNoLeidas(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void contarNoLeidasDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.contarNoLeidas(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void contarNoLeidasDevuelve500AnteUnErrorInesperado() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);
        when(service.contarNoLeidas(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.contarNoLeidas(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void marcarComoLeidaDevuelve401SinAutenticacion() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.marcarComoLeida(null, 5L).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void marcarComoLeidaDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.marcarComoLeida(autenticado("no-numero"), 5L).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void marcarComoLeidaDevuelve500AnteUnErrorInesperado() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);
        doThrow(new RuntimeException("fallo")).when(service).marcarComoLeida(5L, 1L);

        assertThat(controller.marcarComoLeida(autenticado("1"), 5L).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void marcarTodasComoLeidasDevuelve401SinAutenticacion() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.marcarTodasComoLeidas(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void marcarTodasComoLeidasDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.marcarTodasComoLeidas(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void marcarTodasComoLeidasDevuelve500AnteUnErrorInesperado() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);
        doThrow(new RuntimeException("fallo")).when(service).marcarTodasComoLeidas(1L);

        assertThat(controller.marcarTodasComoLeidas(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void contarComunicacionesNoLeidasDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);

        assertThat(controller.contarComunicacionesNoLeidas(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void contarComunicacionesNoLeidasDevuelve500AnteUnErrorInesperado() {
        NotificacionAppService service = mock(NotificacionAppService.class);
        AppNotificacionController controller = new AppNotificacionController(service);
        when(service.contarComunicacionesNoLeidas(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.contarComunicacionesNoLeidas(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
