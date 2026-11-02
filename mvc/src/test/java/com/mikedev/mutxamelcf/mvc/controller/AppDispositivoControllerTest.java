package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.DispositivoAppRequest;
import com.mikedev.mutxamelcf.service.DispositivoAppService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AppDispositivoControllerTest {

    @Test
    void registrarDevuelve401SiNoHayAutenticacion() {
        DispositivoAppService service = mock(DispositivoAppService.class);
        AppDispositivoController controller = new AppDispositivoController(service, mock(com.mikedev.mutxamelcf.service.UsuarioAppService.class));

        ResponseEntity<Void> response = controller.registrar(new DispositivoAppRequest(), null);

        assertThat(response.getStatusCodeValue()).isEqualTo(401);
    }

    @Test
    void registrarDelegaEnElServicioConElUsuarioAutenticado() {
        DispositivoAppService service = mock(DispositivoAppService.class);
        AppDispositivoController controller = new AppDispositivoController(service, mock(com.mikedev.mutxamelcf.service.UsuarioAppService.class));

        DispositivoAppRequest request = new DispositivoAppRequest();
        Authentication auth = new TestingAuthenticationToken("1", null);
        auth.setAuthenticated(true);

        ResponseEntity<Void> response = controller.registrar(request, auth);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        verify(service).registrar(1L, request);
    }

    @Test
    void desregistrarDevuelve204SiNoHayAutenticacion() {
        // N-01: una peticion sin token valido no debe recibir 401 aqui,
        // o la app entra en un bucle de logout -> DELETE -> 401 -> logout.
        DispositivoAppService service = mock(DispositivoAppService.class);
        AppDispositivoController controller = new AppDispositivoController(service, mock(com.mikedev.mutxamelcf.service.UsuarioAppService.class));

        ResponseEntity<Void> response = controller.desregistrar("token-fcm", null);

        assertThat(response.getStatusCodeValue()).isEqualTo(HttpStatus.NO_CONTENT.value());
        verify(service, never()).desactivar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void desregistrarDevuelve204SiLaAutenticacionEsAnonima() {
        DispositivoAppService service = mock(DispositivoAppService.class);
        AppDispositivoController controller = new AppDispositivoController(service, mock(com.mikedev.mutxamelcf.service.UsuarioAppService.class));

        Authentication anonimo = new AnonymousAuthenticationToken(
                "clave", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        ResponseEntity<Void> response = controller.desregistrar("token-fcm", anonimo);

        assertThat(response.getStatusCodeValue()).isEqualTo(HttpStatus.NO_CONTENT.value());
        verify(service, never()).desactivar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void desregistrarDelegaEnElServicioConElUsuarioAutenticado() {
        DispositivoAppService service = mock(DispositivoAppService.class);
        AppDispositivoController controller = new AppDispositivoController(service, mock(com.mikedev.mutxamelcf.service.UsuarioAppService.class));

        Authentication auth = new TestingAuthenticationToken("1", null);
        auth.setAuthenticated(true);

        ResponseEntity<Void> response = controller.desregistrar("token-fcm", auth);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        verify(service).desactivar(1L, "token-fcm");
    }

    @Test
    void registrarGuardaElIdiomaQueMandaLaApp() {
        DispositivoAppService service = mock(DispositivoAppService.class);
        com.mikedev.mutxamelcf.service.UsuarioAppService usuarios = mock(com.mikedev.mutxamelcf.service.UsuarioAppService.class);
        AppDispositivoController controller = new AppDispositivoController(service, usuarios);

        DispositivoAppRequest request = new DispositivoAppRequest();
        request.setIdioma("ca");
        Authentication auth = new TestingAuthenticationToken("7", null);
        auth.setAuthenticated(true);

        controller.registrar(request, auth);

        verify(usuarios).actualizarIdioma(7, "ca");
    }

    @Test
    void registrarSinIdiomaDejaElDeLaCuentaComoEstaba() {
        DispositivoAppService service = mock(DispositivoAppService.class);
        com.mikedev.mutxamelcf.service.UsuarioAppService usuarios = mock(com.mikedev.mutxamelcf.service.UsuarioAppService.class);
        AppDispositivoController controller = new AppDispositivoController(service, usuarios);

        Authentication auth = new TestingAuthenticationToken("7", null);
        auth.setAuthenticated(true);

        controller.registrar(new DispositivoAppRequest(), auth);

        verify(usuarios, never()).actualizarIdioma(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }
}
