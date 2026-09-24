package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.PartidoGuardarRequest;
import com.mikedev.mutxamelcf.mvc.api.AppPartidoController;
import com.mikedev.mutxamelcf.service.PartidoService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppPartidoControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    // ---------- crear ----------

    @Test
    void crearDevuelve401SinAutenticacion() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        ResponseEntity<?> response = controller.crear(new PartidoGuardarRequest(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crearDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        ResponseEntity<?> response = controller.crear(new PartidoGuardarRequest(), autenticado("no-numero"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void crearDevuelve201ConElPartidoCreado() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        PartidoGuardarRequest request = new PartidoGuardarRequest();
        when(service.crear(1L, request)).thenReturn(new PartidoDTO());

        ResponseEntity<?> response = controller.crear(request, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void crearDevuelve403SiElServicioDeniegaPorPermiso() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        PartidoGuardarRequest request = new PartidoGuardarRequest();
        when(service.crear(1L, request)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.crear(request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void crearDevuelve400SiElServicioLanzaIllegalArgument() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        PartidoGuardarRequest request = new PartidoGuardarRequest();
        when(service.crear(1L, request)).thenThrow(new IllegalArgumentException("no valido"));

        assertThat(controller.crear(request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void crearDevuelve500SiElServicioLanzaExcepcionInesperada() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        PartidoGuardarRequest request = new PartidoGuardarRequest();
        when(service.crear(1L, request)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.crear(request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ---------- actualizar ----------

    @Test
    void actualizarDevuelve401SinAutenticacion() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        ResponseEntity<?> response = controller.actualizar(5L, new PartidoGuardarRequest(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actualizarDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        ResponseEntity<?> response = controller.actualizar(5L, new PartidoGuardarRequest(),
                autenticado("no-numero"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actualizarDevuelve200ConElPartidoActualizado() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        PartidoGuardarRequest request = new PartidoGuardarRequest();
        when(service.actualizar(1L, 5L, request)).thenReturn(new PartidoDTO());

        ResponseEntity<?> response = controller.actualizar(5L, request, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void actualizarDevuelve403SiElServicioDeniegaPorPermiso() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        PartidoGuardarRequest request = new PartidoGuardarRequest();
        when(service.actualizar(1L, 5L, request)).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.actualizar(5L, request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void actualizarDevuelve400SiElServicioLanzaIllegalArgument() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        PartidoGuardarRequest request = new PartidoGuardarRequest();
        when(service.actualizar(1L, 5L, request)).thenThrow(new IllegalArgumentException("no valido"));

        assertThat(controller.actualizar(5L, request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void actualizarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        PartidoService service = mock(PartidoService.class);
        AppPartidoController controller = new AppPartidoController(service);

        PartidoGuardarRequest request = new PartidoGuardarRequest();
        when(service.actualizar(1L, 5L, request)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.actualizar(5L, request, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
