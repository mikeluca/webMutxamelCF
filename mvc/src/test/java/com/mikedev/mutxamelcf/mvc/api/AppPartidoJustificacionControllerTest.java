package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.JustificacionFaltaPartidoResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.service.JustificacionFaltaPartidoService;

class AppPartidoJustificacionControllerTest {

    private final JustificacionFaltaPartidoService servicio = mock(JustificacionFaltaPartidoService.class);
    private final AppPartidoJustificacionController controller = new AppPartidoJustificacionController(servicio);

    private static Authentication autenticado(String nombre) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(nombre, "x");
        auth.setAuthenticated(true);
        return auth;
    }

    private static JustificacionFaltaRequest peticion() {
        JustificacionFaltaRequest request = new JustificacionFaltaRequest();
        request.setJugadorId(100L);
        request.setMotivo("Examen");
        return request;
    }

    private static final JustificacionFaltaPartidoResponse RESPUESTA = new JustificacionFaltaPartidoResponse(
            30L, 100L, "Pablo", "Examen", LocalDateTime.of(2026, 10, 21, 10, 0));

    @Test
    void sinAutenticacionTodoEs401() {
        assertThat(controller.justificar(30L, peticion(), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.retirar(30L, 100L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.obtener(30L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void justificarCreaYDevuelve201() {
        JustificacionFaltaRequest request = peticion();
        when(servicio.justificar(7L, 30L, request)).thenReturn(RESPUESTA);

        ResponseEntity<?> respuesta = controller.justificar(30L, request, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isEqualTo(RESPUESTA);
    }

    @Test
    void justificarMapeaLosErroresDelServicio() {
        JustificacionFaltaRequest request = peticion();

        doThrow(new SecurityException("sin vínculo")).when(servicio).justificar(7L, 30L, request);
        assertThat(controller.justificar(30L, request, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        doThrow(new IllegalArgumentException("plazo cerrado")).when(servicio).justificar(7L, 30L, request);
        ResponseEntity<?> fueraDePlazo = controller.justificar(30L, request, autenticado("7"));
        assertThat(fueraDePlazo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fueraDePlazo.getBody()).isEqualTo("plazo cerrado");

        doThrow(new IllegalStateException("ORA-00942")).when(servicio).justificar(7L, 30L, request);
        ResponseEntity<?> error = controller.justificar(30L, request, autenticado("7"));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // El detalle interno no se filtra al cliente.
        assertThat(error.getBody()).isEqualTo("Error al justificar la ausencia");

        assertThat(controller.justificar(30L, request, autenticado("no-numerico")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void retirarDevuelve204YMapeaErrores() {
        assertThat(controller.retirar(30L, 100L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(servicio).retirar(7L, 30L, 100L);

        doThrow(new SecurityException("sin vínculo")).when(servicio).retirar(7L, 30L, 101L);
        assertThat(controller.retirar(30L, 101L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        doThrow(new IllegalArgumentException("plazo")).when(servicio).retirar(7L, 30L, 102L);
        assertThat(controller.retirar(30L, 102L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerDevuelveLaListaOLos403() {
        when(servicio.obtener(7L, 30L)).thenReturn(List.of(RESPUESTA));

        ResponseEntity<?> respuesta = controller.obtener(30L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(List.of(RESPUESTA));

        doThrow(new SecurityException("no gestiona")).when(servicio).obtener(8L, 30L);
        assertThat(controller.obtener(30L, autenticado("8")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
