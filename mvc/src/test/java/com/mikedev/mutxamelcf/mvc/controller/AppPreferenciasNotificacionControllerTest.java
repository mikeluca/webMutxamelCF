package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.PreferenciasNotificacionRequest;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacionResponse;
import com.mikedev.mutxamelcf.service.PreferenciasNotificacionService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppPreferenciasNotificacionControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void obtenerDevuelve401SiNoHayAutenticacion() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);

        assertThat(controller.obtener(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerDevuelve401SiElNombreNoEsUnIdValido() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);

        assertThat(controller.obtener(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerDevuelveLasPreferencias() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);

        when(service.obtenerPorUsuario(1L)).thenReturn(new PreferenciasNotificacionResponse());

        ResponseEntity<?> response = controller.obtener(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void obtenerDevuelve400SiElServicioLanzaIllegalArgument() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);

        when(service.obtenerPorUsuario(1L)).thenThrow(new IllegalArgumentException("dato invalido"));

        ResponseEntity<?> response = controller.obtener(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerDevuelve500AnteUnErrorInesperado() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);

        when(service.obtenerPorUsuario(1L)).thenThrow(new RuntimeException("fallo"));

        ResponseEntity<?> response = controller.obtener(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void actualizarDevuelve401SiNoHayAutenticacion() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);

        assertThat(controller.actualizar(null, new PreferenciasNotificacionRequest()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actualizarDevuelveLasPreferenciasActualizadas() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);

        PreferenciasNotificacionRequest request = new PreferenciasNotificacionRequest();
        when(service.actualizar(1L, request)).thenReturn(new PreferenciasNotificacionResponse());

        ResponseEntity<?> response = controller.actualizar(autenticado("1"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void actualizarDevuelve401SiElNombreNoEsUnIdValido() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);

        assertThat(controller.actualizar(autenticado("no-numero"), new PreferenciasNotificacionRequest())
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actualizarDevuelve400SiElServicioLanzaIllegalArgument() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);
        PreferenciasNotificacionRequest request = new PreferenciasNotificacionRequest();
        when(service.actualizar(1L, request)).thenThrow(new IllegalArgumentException("dato invalido"));

        assertThat(controller.actualizar(autenticado("1"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void actualizarDevuelve500AnteUnErrorInesperado() {
        PreferenciasNotificacionService service = mock(PreferenciasNotificacionService.class);
        AppPreferenciasNotificacionController controller = new AppPreferenciasNotificacionController(service);
        PreferenciasNotificacionRequest request = new PreferenciasNotificacionRequest();
        when(service.actualizar(1L, request)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.actualizar(autenticado("1"), request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
