package com.mikedev.mutxamelcf.mvc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.ActivarCuentaAppRequest;
import com.mikedev.mutxamelcf.model.IdiomaRequest;
import com.mikedev.mutxamelcf.model.LoginAppRequest;
import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.mvc.communication.CuentaActivadaNotifier;
import com.mikedev.mutxamelcf.mvc.communication.RecuperacionContrasena;
import com.mikedev.mutxamelcf.mvc.config.LoginRateLimiter;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/** El idioma de la cuenta llega de la app al iniciar sesión, al activar y al cambiarlo en Ajustes. */
class AppIdiomaControllerTest {

    private UsuarioAppService usuarioAppService;
    private AppIdiomaController idiomaController;
    private AppAuthController authController;
    private CuentaActivadaNotifier notifier;

    @BeforeEach
    void setUp() {
        usuarioAppService = mock(UsuarioAppService.class);
        notifier = mock(CuentaActivadaNotifier.class);
        idiomaController = new AppIdiomaController(usuarioAppService);

        LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
        when(rateLimiter.clave(any(), any(), any())).thenReturn("clave");
        authController = new AppAuthController(usuarioAppService, rateLimiter, notifier,
                mock(RecuperacionContrasena.class));
    }

    private static Authentication usuario(String id) {
        Authentication auth = new TestingAuthenticationToken(id, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private static IdiomaRequest idioma(String valor) {
        IdiomaRequest request = new IdiomaRequest();
        request.setIdioma(valor);
        return request;
    }

    private static MockHttpServletRequest http() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    // ---- PUT /api/app/preferencias/idioma ----

    @Test
    void elUsuarioCambiaElIdiomaEnAjustes() {
        ResponseEntity<?> respuesta = idiomaController.actualizar(idioma("ca"), usuario("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(Map.of("idioma", "ca"));
        verify(usuarioAppService).actualizarIdioma(7, "ca");
    }

    @Test
    void cualquierOtroValorSeGuardaComoCastellano() {
        for (String valor : new String[] { "fr", "", "valenciano", null }) {
            ResponseEntity<?> respuesta = idiomaController.actualizar(idioma(valor), usuario("7"));

            assertThat(respuesta.getBody()).as("valor '%s'", valor).isEqualTo(Map.of("idioma", "es"));
        }

        verify(usuarioAppService, org.mockito.Mockito.times(4)).actualizarIdioma(7, "es");
    }

    @Test
    void sinSesionDevuelve401() {
        assertThat(idiomaController.actualizar(idioma("en"), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(idiomaController.actualizar(idioma("en"), usuario("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(usuarioAppService, never()).actualizarIdioma(anyInt(), anyString());
    }

    // ---- al iniciar sesión ----

    @Test
    void elLoginGuardaElIdiomaSiLoTrae() {
        LoginAppResponse login = new LoginAppResponse("jwt", 9, "ana@example.com", java.util.List.of("FAMILIAR"));
        when(usuarioAppService.login("ana@example.com", "secreto123")).thenReturn(login);

        LoginAppRequest request = new LoginAppRequest();
        request.setEmail("ana@example.com");
        request.setPassword("secreto123");
        request.setIdioma("en");

        assertThat(authController.login(request, http()).getStatusCode()).isEqualTo(HttpStatus.OK);

        verify(usuarioAppService).actualizarIdioma(9, "en");
    }

    @Test
    void elLoginDeUnaVersionAntiguaSinIdiomaNoTocaElIdioma() {
        LoginAppResponse login = new LoginAppResponse("jwt", 9, "ana@example.com", java.util.List.of("FAMILIAR"));
        when(usuarioAppService.login("ana@example.com", "secreto123")).thenReturn(login);

        LoginAppRequest request = new LoginAppRequest();
        request.setEmail("ana@example.com");
        request.setPassword("secreto123");

        authController.login(request, http());

        verify(usuarioAppService, never()).actualizarIdioma(anyInt(), anyString());
    }

    // ---- al activar la cuenta ----

    @Test
    void alActivarSeGuardaElIdiomaAntesDeEnviarElEmailDeCuentaActivada() {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(4);
        usuario.setEmail("ana@example.com");
        when(usuarioAppService.activarCuenta("ana@example.com", "123456", "clave-nueva-1")).thenReturn(usuario);
        when(usuarioAppService.login("ana@example.com", "clave-nueva-1"))
                .thenReturn(new LoginAppResponse("jwt", 4, "ana@example.com", java.util.List.of()));

        ActivarCuentaAppRequest request = new ActivarCuentaAppRequest();
        request.setEmail("ana@example.com");
        request.setCodigo("123456");
        request.setPassword("clave-nueva-1");
        request.setIdioma("ca");

        authController.activar(request, http());

        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(usuarioAppService, notifier);
        orden.verify(usuarioAppService).actualizarIdioma(4, "ca");
        orden.verify(notifier).notificar(4);
    }
}
