package com.mikedev.mutxamelcf.mvc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.EliminarCuentaAppRequest;
import com.mikedev.mutxamelcf.mvc.config.LoginRateLimiter;
import com.mikedev.mutxamelcf.service.CuentaAppService;

class AppCuentaControllerTest {

    private CuentaAppService cuentaAppService;
    private LoginRateLimiter rateLimiter;
    private AppCuentaController controller;

    @BeforeEach
    void setUp() {
        cuentaAppService = mock(CuentaAppService.class);
        rateLimiter = mock(LoginRateLimiter.class);
        controller = new AppCuentaController(cuentaAppService, rateLimiter);
        when(rateLimiter.clave(LoginRateLimiter.CONTEXTO_APP_BORRAR_CUENTA, "127.0.0.1", "5"))
                .thenReturn("clave");
    }

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private static EliminarCuentaAppRequest request(String password) {
        EliminarCuentaAppRequest request = new EliminarCuentaAppRequest();
        request.setPassword(password);
        return request;
    }

    private static MockHttpServletRequest httpRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @Test
    void devuelve401SinAutenticacion() {
        assertThat(controller.eliminar(request("x"), null, httpRequest()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(cuentaAppService, never()).eliminarCuenta(anyInt(), anyString());
    }

    @Test
    void devuelve401SiElNombreNoEsUnId() {
        assertThat(controller.eliminar(request("x"), autenticado("abc"), httpRequest()).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devuelve204YRegistraExito() {
        assertThat(controller.eliminar(request("secreta123"), autenticado("5"), httpRequest()).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        verify(cuentaAppService).eliminarCuenta(5, "secreta123");
        verify(rateLimiter).registrarExito("clave");
    }

    @Test
    void devuelve400YRegistraFalloSiLaPasswordEsIncorrecta() {
        doThrow(new IllegalArgumentException("La contraseña no es correcta"))
                .when(cuentaAppService).eliminarCuenta(5, "mala");

        var response = controller.eliminar(request("mala"), autenticado("5"), httpRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(rateLimiter).registrarFallo("clave");
    }

    @Test
    void devuelve429SiEstaBloqueado() {
        when(rateLimiter.estaBloqueado("clave")).thenReturn(true);

        assertThat(controller.eliminar(request("x"), autenticado("5"), httpRequest()).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        verify(cuentaAppService, never()).eliminarCuenta(anyInt(), anyString());
    }
}
