package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.DispositivoAppRequest;
import com.mikedev.mutxamelcf.service.DispositivoAppService;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AppDispositivoControllerTest {

    @Test
    void registrarDevuelve401SiNoHayAutenticacion() {
        DispositivoAppService service = mock(DispositivoAppService.class);
        AppDispositivoController controller = new AppDispositivoController(service);

        ResponseEntity<Void> response = controller.registrar(new DispositivoAppRequest(), null);

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
    }

    @Test
    void registrarDelegaEnElServicioConElUsuarioAutenticado() {
        DispositivoAppService service = mock(DispositivoAppService.class);
        AppDispositivoController controller = new AppDispositivoController(service);

        DispositivoAppRequest request = new DispositivoAppRequest();
        Authentication auth = new TestingAuthenticationToken("1", null);
        auth.setAuthenticated(true);

        ResponseEntity<Void> response = controller.registrar(request, auth);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        verify(service).registrar(1L, request);
    }
}
