package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.service.SesionEntrenamientoService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cubre solo GET /api/app/sesiones-entrenamiento (obtenerPorEquipoYRango),
 * en concreto el limite de rango de fechas de DB-04; el resto de
 * endpoints de AppSesionEntrenamientoController (crear, cancelar,
 * justificar...) no tenian tests previos y quedan fuera de este cambio.
 */
class AppSesionEntrenamientoControllerRangoTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void obtenerPorEquipoYRangoDevuelve401SiNoHayAutenticacion() {
        AppSesionEntrenamientoController controller =
                new AppSesionEntrenamientoController(mock(SesionEntrenamientoService.class));

        ResponseEntity<?> response = controller.obtenerPorEquipoYRango(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerPorEquipoYRangoDevuelve400SiElRangoSuperaLos93Dias() {
        AppSesionEntrenamientoController controller =
                new AppSesionEntrenamientoController(mock(SesionEntrenamientoService.class));

        ResponseEntity<?> response = controller.obtenerPorEquipoYRango(
                1L, LocalDate.of(1900, 1, 1), LocalDate.of(2100, 1, 1), autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerPorEquipoYRangoDelegaEnElServicioDentroDelRangoPermitido() {
        SesionEntrenamientoService service = mock(SesionEntrenamientoService.class);
        AppSesionEntrenamientoController controller = new AppSesionEntrenamientoController(service);

        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);
        when(service.obtenerPorEquipoYRango(1L, desde, hasta)).thenReturn(List.of());

        ResponseEntity<?> response = controller.obtenerPorEquipoYRango(1L, desde, hasta, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
