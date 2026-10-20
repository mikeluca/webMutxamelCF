package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.TarjetasJugadorResponse;
import com.mikedev.mutxamelcf.service.EstadisticasEquipoService;

class AppEstadisticasEquipoControllerTest {

    private final EstadisticasEquipoService servicio = mock(EstadisticasEquipoService.class);
    private final AppEstadisticasEquipoController controller = new AppEstadisticasEquipoController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void devuelve401SinAutenticacion() {
        assertThat(controller.obtenerTarjetas(1L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.obtenerEstadisticas(1L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devuelve200ConLasTarjetas() {
        List<TarjetasJugadorResponse> tarjetas = List.of(new TarjetasJugadorResponse(1L, "Pablo", 9, 4, 0, "A_UNA"));
        when(servicio.obtenerTarjetas(7L, 100L)).thenReturn(tarjetas);

        ResponseEntity<?> respuesta = controller.obtenerTarjetas(100L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isSameAs(tarjetas);
    }

    @Test
    void devuelve403SiNoEsStaffDelEquipo() {
        when(servicio.obtenerTarjetas(any(), any())).thenThrow(new SecurityException("sin permiso"));
        when(servicio.obtenerEstadisticas(any(), any())).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerTarjetas(100L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.obtenerEstadisticas(100L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void devuelve400SiElEquipoNoExiste() {
        when(servicio.obtenerEstadisticas(any(), any())).thenThrow(new IllegalArgumentException("El equipo no existe"));

        assertThat(controller.obtenerEstadisticas(100L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void devuelve500SinFiltrarElDetalle() {
        when(servicio.obtenerEstadisticas(any(), any())).thenThrow(new IllegalStateException("ORA-00942"));

        ResponseEntity<?> respuesta = controller.obtenerEstadisticas(100L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(respuesta.getBody())).doesNotContain("ORA-");
    }
}
