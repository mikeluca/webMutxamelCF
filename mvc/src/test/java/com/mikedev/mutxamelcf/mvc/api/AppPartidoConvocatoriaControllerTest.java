package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.ConvocatoriaPartidoResponse;
import com.mikedev.mutxamelcf.service.ConvocatoriaPartidoService;

class AppPartidoConvocatoriaControllerTest {

    private final ConvocatoriaPartidoService servicio = mock(ConvocatoriaPartidoService.class);
    private final AppPartidoConvocatoriaController controller = new AppPartidoConvocatoriaController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void sinAutenticacionEs401() {
        assertThat(controller.obtener(30L, 100L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devuelveLaConvocatoria() {
        ConvocatoriaPartidoResponse respuesta = new ConvocatoriaPartidoResponse(
                5L, "Pego", null, "11:00", "Municipal", true, "09:30", "Vestuarios", true, List.of(), null, null);
        when(servicio.obtenerParaJugador(7L, 30L, 100L)).thenReturn(respuesta);

        var resultado = controller.obtener(30L, 100L, autenticado("7"));

        assertThat(resultado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resultado.getBody()).isEqualTo(respuesta);
    }

    @Test
    void mapeaLosErroresDelServicio() {
        when(servicio.obtenerParaJugador(7L, 30L, 100L)).thenThrow(new SecurityException("otro equipo"));
        assertThat(controller.obtener(30L, 100L, autenticado("7")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        when(servicio.obtenerParaJugador(7L, 31L, 100L)).thenThrow(new NoSuchElementException("sin convocatoria"));
        assertThat(controller.obtener(31L, 100L, autenticado("7")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        when(servicio.obtenerParaJugador(7L, 32L, 100L)).thenThrow(new IllegalArgumentException("mal"));
        assertThat(controller.obtener(32L, 100L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
