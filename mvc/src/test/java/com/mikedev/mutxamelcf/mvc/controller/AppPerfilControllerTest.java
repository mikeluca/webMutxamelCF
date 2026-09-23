package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.PerfilAppResponse;
import com.mikedev.mutxamelcf.service.PerfilAppService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppPerfilControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void obtenerPerfilDevuelve401SinAutenticacion() {
        PerfilAppService service = mock(PerfilAppService.class);
        AppPerfilController controller = new AppPerfilController(service);

        assertThat(controller.obtenerPerfil(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPerfilDevuelve401SiElNombreNoEsUnIdValido() {
        PerfilAppService service = mock(PerfilAppService.class);
        AppPerfilController controller = new AppPerfilController(service);

        assertThat(controller.obtenerPerfil(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPerfilDevuelve404SiElUsuarioNoExiste() {
        PerfilAppService service = mock(PerfilAppService.class);
        AppPerfilController controller = new AppPerfilController(service);

        when(service.obtenerPerfil(1)).thenThrow(new IllegalArgumentException("no existe"));

        assertThat(controller.obtenerPerfil(autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerPerfilDevuelve500AnteUnErrorInesperado() {
        PerfilAppService service = mock(PerfilAppService.class);
        AppPerfilController controller = new AppPerfilController(service);

        when(service.obtenerPerfil(1)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerPerfil(autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerPerfilDevuelveElPerfil() {
        PerfilAppService service = mock(PerfilAppService.class);
        AppPerfilController controller = new AppPerfilController(service);

        when(service.obtenerPerfil(1)).thenReturn(new PerfilAppResponse());

        ResponseEntity<?> response = controller.obtenerPerfil(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
