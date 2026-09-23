package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.ActivarCuentaAppRequest;
import com.mikedev.mutxamelcf.model.LoginAppRequest;
import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.mvc.config.LoginRateLimiter;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppAuthControllerTest {

    private UsuarioAppService usuarioAppService;
    private LoginRateLimiter rateLimiter;
    private AppAuthController controller;

    @BeforeEach
    void setUp() {
        usuarioAppService = mock(UsuarioAppService.class);
        rateLimiter = mock(LoginRateLimiter.class);
        controller = new AppAuthController(usuarioAppService, rateLimiter);
        when(rateLimiter.clave("127.0.0.1", "ana@example.com")).thenReturn("clave");
    }

    private static LoginAppRequest loginRequest() {
        LoginAppRequest request = new LoginAppRequest();
        request.setEmail("ana@example.com");
        request.setPassword("secreto123");
        return request;
    }

    private static MockHttpServletRequest httpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    void loginDevuelveTooManyRequestsSiEstaBloqueado() {
        when(rateLimiter.estaBloqueado("clave")).thenReturn(true);

        ResponseEntity<?> response = controller.login(loginRequest(), httpRequest());

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        verify(usuarioAppService, never()).login(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginDevuelveOkYRegistraExitoCuandoLasCredencialesSonValidas() {
        when(rateLimiter.estaBloqueado("clave")).thenReturn(false);
        LoginAppResponse loginResponse = new LoginAppResponse();
        when(usuarioAppService.login("ana@example.com", "secreto123")).thenReturn(loginResponse);

        ResponseEntity<?> response = controller.login(loginRequest(), httpRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(rateLimiter).registrarExito("clave");
        verify(rateLimiter, never()).registrarFallo("clave");
    }

    @Test
    void loginDevuelveForbiddenSiLaCuentaNoEstaActiva() {
        when(rateLimiter.estaBloqueado("clave")).thenReturn(false);
        when(usuarioAppService.login("ana@example.com", "secreto123"))
                .thenThrow(new IllegalStateException("La cuenta no esta activa"));

        ResponseEntity<?> response = controller.login(loginRequest(), httpRequest());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("La cuenta no esta activa", response.getBody());
        verify(rateLimiter, never()).registrarFallo("clave");
    }

    @Test
    void loginDevuelveUnauthorizedYRegistraFalloSiLasCredencialesSonInvalidas() {
        when(rateLimiter.estaBloqueado("clave")).thenReturn(false);
        when(usuarioAppService.login("ana@example.com", "secreto123"))
                .thenThrow(new IllegalArgumentException("Credenciales invalidas"));

        ResponseEntity<?> response = controller.login(loginRequest(), httpRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rateLimiter).registrarFallo("clave");
    }

    @Test
    void activarDevuelveOkConLoginResponseCuandoLaActivacionEsValida() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(1);
        usuario.setEmail("ana@example.com");
        when(usuarioAppService.activarCuenta("ana@example.com", "123456", "secreto123")).thenReturn(usuario);
        LoginAppResponse loginResponse = new LoginAppResponse();
        when(usuarioAppService.login("ana@example.com", "secreto123")).thenReturn(loginResponse);

        ActivarCuentaAppRequest request = new ActivarCuentaAppRequest();
        request.setEmail("ana@example.com");
        request.setCodigo("123456");
        request.setPassword("secreto123");

        ResponseEntity<?> response = controller.activar(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(loginResponse, response.getBody());
    }

    @Test
    void activarDevuelveConflictSiLaCuentaYaEstaActivada() {
        when(usuarioAppService.activarCuenta("ana@example.com", "123456", "secreto123"))
                .thenThrow(new IllegalStateException("Cuenta ya activada"));

        ActivarCuentaAppRequest request = new ActivarCuentaAppRequest();
        request.setEmail("ana@example.com");
        request.setCodigo("123456");
        request.setPassword("secreto123");

        ResponseEntity<?> response = controller.activar(request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void activarDevuelveBadRequestSiElCodigoEsInvalido() {
        when(usuarioAppService.activarCuenta("ana@example.com", "000000", "secreto123"))
                .thenThrow(new IllegalArgumentException("Codigo invalido"));

        ActivarCuentaAppRequest request = new ActivarCuentaAppRequest();
        request.setEmail("ana@example.com");
        request.setCodigo("000000");
        request.setPassword("secreto123");

        ResponseEntity<?> response = controller.activar(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void obtenerUsuarioActualDevuelveUnauthorizedSiElUsuarioNoExiste() {
        Authentication authentication = new TestingAuthenticationToken("1", null);
        when(usuarioAppService.obtenerPorId(1)).thenReturn(null);

        ResponseEntity<?> response = controller.obtenerUsuarioActual(authentication);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Usuario no encontrado", response.getBody());
    }

    @Test
    void obtenerUsuarioActualDevuelveElUsuarioConSusRoles() {
        Authentication authentication = new TestingAuthenticationToken("1", null);
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(1);
        usuario.setEmail("ana@example.com");
        when(usuarioAppService.obtenerPorId(1)).thenReturn(usuario);
        RolApp rol = new RolApp();
        rol.setCodigo("JUGADOR");
        when(usuarioAppService.obtenerRoles(1)).thenReturn(List.of(rol));

        ResponseEntity<?> response = controller.obtenerUsuarioActual(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void obtenerUsuarioActualDevuelveUnauthorizedSiLaAutenticacionNoEsValida() {
        Authentication authentication = new TestingAuthenticationToken("no-es-un-numero", null);

        ResponseEntity<?> response = controller.obtenerUsuarioActual(authentication);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Usuario no autenticado", response.getBody());
    }
}
