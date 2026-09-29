package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.CalendarioResponse;
import com.mikedev.mutxamelcf.service.PartidoService;
import com.mikedev.mutxamelcf.service.SesionEntrenamientoService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppCalendarioControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void obtenerCalendarioDevuelve401SiNoHayAutenticacion() {
        AppCalendarioController controller = new AppCalendarioController(
                mock(SesionEntrenamientoService.class), mock(PartidoService.class));

        ResponseEntity<?> response = controller.obtenerCalendario(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerCalendarioDevuelve400SiElRangoSuperaLos93Dias() {
        // DB-04: sin este limite se podia pedir un rango arbitrariamente
        // grande (p. ej. todo el historico) en una sola llamada.
        SesionEntrenamientoService sesionService = mock(SesionEntrenamientoService.class);
        PartidoService partidoService = mock(PartidoService.class);
        AppCalendarioController controller = new AppCalendarioController(sesionService, partidoService);

        ResponseEntity<?> response = controller.obtenerCalendario(
                1L, LocalDate.of(1900, 1, 1), LocalDate.of(2100, 1, 1), null, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerCalendarioDelegaEnLosServiciosDentroDelRangoPermitido() {
        SesionEntrenamientoService sesionService = mock(SesionEntrenamientoService.class);
        PartidoService partidoService = mock(PartidoService.class);
        AppCalendarioController controller = new AppCalendarioController(sesionService, partidoService);

        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);

        when(sesionService.obtenerPorEquipoYRango(1L, desde, hasta)).thenReturn(List.of());
        when(partidoService.obtenerPorEquipoYRangoFechas(1L, desde, hasta)).thenReturn(List.of());

        ResponseEntity<?> response = controller.obtenerCalendario(1L, desde, hasta, null, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(CalendarioResponse.class);
    }

    @Test
    void obtenerCalendarioConJugadorIdUsaLaVariantePorJugador() {
        SesionEntrenamientoService sesionService = mock(SesionEntrenamientoService.class);
        PartidoService partidoService = mock(PartidoService.class);
        AppCalendarioController controller = new AppCalendarioController(sesionService, partidoService);

        LocalDate desde = LocalDate.of(2026, 1, 1);
        LocalDate hasta = LocalDate.of(2026, 1, 31);

        when(sesionService.obtenerPorEquipoYRangoParaJugador(anyLong(), anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of());
        when(partidoService.obtenerPorEquipoYRangoFechas(1L, desde, hasta)).thenReturn(List.of());

        ResponseEntity<?> response = controller.obtenerCalendario(1L, desde, hasta, 5L, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        org.mockito.Mockito.verify(sesionService).obtenerPorEquipoYRangoParaJugador(1L, 1L, desde, hasta, 5L);
    }
}
