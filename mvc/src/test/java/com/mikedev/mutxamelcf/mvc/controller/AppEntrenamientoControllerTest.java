package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.EntrenamientoGuardarRequest;
import com.mikedev.mutxamelcf.model.EntrenamientoResponse;
import com.mikedev.mutxamelcf.service.EntrenamientoService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppEntrenamientoControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void crearDevuelve401SinAutenticacion() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        ResponseEntity<?> response = controller.crear(new EntrenamientoGuardarRequest(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crearDevuelve201ConElEntrenamientoCreado() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest();
        when(service.crear(1L, request)).thenReturn(new EntrenamientoResponse());

        ResponseEntity<?> response = controller.crear(request, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void crearDevuelve403SiElServicioDeniegaPorRol() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest();
        when(service.crear(1L, request)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.crear(request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void crearDevuelve400SiElServicioLanzaIllegalArgument() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest();
        when(service.crear(1L, request)).thenThrow(new IllegalArgumentException("no valido"));

        assertThat(controller.crear(request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void actualizarLanzaExcepcionSiNoHayContextoDeSeguridad() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> controller.actualizar(1L, new EntrenamientoGuardarRequest()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void actualizarDelegaEnElServicioUsandoElContextoDeSeguridad() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        org.springframework.security.core.context.SecurityContext contexto = SecurityContextHolder
                .createEmptyContext();
        contexto.setAuthentication(autenticado("1"));
        SecurityContextHolder.setContext(contexto);

        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest();
        when(service.actualizar(1L, 5L, request)).thenReturn(new EntrenamientoResponse());

        ResponseEntity<EntrenamientoResponse> response = controller.actualizar(5L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void obtenerPorIdDevuelve400SiFaltaElEquipoId() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        assertThat(controller.obtenerPorId(null, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerPorIdDevuelve404SiElServicioLanzaIllegalArgument() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        when(service.obtenerPorId(1L, 5L)).thenThrow(new IllegalArgumentException("no existe"));

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerPorEquipoDevuelveLaListaDeEntrenamientos() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        when(service.obtenerPorEquipo(1L, 5L)).thenReturn(List.of(new EntrenamientoResponse()));

        ResponseEntity<?> response = controller.obtenerPorEquipo(5L, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void crearDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        assertThat(controller.crear(new EntrenamientoGuardarRequest(), autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crearDevuelve500SiElServicioLanzaExcepcionInesperada() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);
        EntrenamientoGuardarRequest request = new EntrenamientoGuardarRequest();
        when(service.crear(1L, request)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.crear(request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void actualizarLanzaExcepcionSiElUsuarioNoEstaAutenticado() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        org.springframework.security.core.context.SecurityContext contexto = SecurityContextHolder
                .createEmptyContext();
        Authentication auth = new TestingAuthenticationToken("1", null);
        auth.setAuthenticated(false);
        contexto.setAuthentication(auth);
        SecurityContextHolder.setContext(contexto);

        assertThatThrownBy(() -> controller.actualizar(1L, new EntrenamientoGuardarRequest()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void obtenerPorIdDevuelve401SinAutenticacion() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        assertThat(controller.obtenerPorId(5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorIdDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        assertThat(controller.obtenerPorId(5L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorIdDevuelve403SiElServicioDeniegaPorRol() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);
        when(service.obtenerPorId(1L, 5L)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerPorIdDevuelve500SiElServicioLanzaExcepcionInesperada() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);
        when(service.obtenerPorId(1L, 5L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerPorId(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerPorEquipoDevuelve401SinAutenticacion() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        assertThat(controller.obtenerPorEquipo(5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorEquipoDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);

        assertThat(controller.obtenerPorEquipo(5L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorEquipoDevuelve403SiElServicioDeniegaPorRol() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);
        when(service.obtenerPorEquipo(1L, 5L)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerPorEquipo(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerPorEquipoDevuelve404SiElServicioLanzaIllegalArgument() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);
        when(service.obtenerPorEquipo(1L, 5L)).thenThrow(new IllegalArgumentException("no existe"));

        assertThat(controller.obtenerPorEquipo(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerPorEquipoDevuelve500SiElServicioLanzaExcepcionInesperada() {
        EntrenamientoService service = mock(EntrenamientoService.class);
        AppEntrenamientoController controller = new AppEntrenamientoController(service);
        when(service.obtenerPorEquipo(1L, 5L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerPorEquipo(5L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
