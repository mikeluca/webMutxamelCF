package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.service.SesionEntrenamientoService;

/** DELETE /api/app/sesiones-entrenamiento/{id}/justificaciones/{jugadorId}. */
class AppSesionEntrenamientoControllerRetirarTest {

    private final SesionEntrenamientoService servicio = mock(SesionEntrenamientoService.class);
    private final AppSesionEntrenamientoController controller = new AppSesionEntrenamientoController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void sinAutenticacionEs401() {
        assertThat(controller.retirarJustificacion(30L, 100L, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void retirarDevuelve204() {
        ResponseEntity<?> respuesta = controller.retirarJustificacion(30L, 100L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(servicio).retirarJustificacion(7L, 30L, 100L);
    }

    @Test
    void sinVinculoEs403YFueraDePlazoEs400() {
        doThrow(new SecurityException("sin vínculo")).when(servicio).retirarJustificacion(7L, 30L, 100L);
        assertThat(controller.retirarJustificacion(30L, 100L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        doThrow(new IllegalArgumentException("plazo")).when(servicio).retirarJustificacion(7L, 30L, 101L);
        assertThat(controller.retirarJustificacion(30L, 101L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
