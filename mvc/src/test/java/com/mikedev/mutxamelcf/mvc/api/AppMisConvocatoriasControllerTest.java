package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.MiConvocatoriaResponse;
import com.mikedev.mutxamelcf.model.RespuestaConvocatoriaRequest;
import com.mikedev.mutxamelcf.service.RespuestaConvocatoriaService;

class AppMisConvocatoriasControllerTest {

    private final RespuestaConvocatoriaService servicio = mock(RespuestaConvocatoriaService.class);
    private final AppMisConvocatoriasController controller = new AppMisConvocatoriasController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private static final RespuestaConvocatoriaRequest VA = new RespuestaConvocatoriaRequest("VA", null);

    @Test
    void misConvocatoriasDevuelve401SinAutenticacion() {
        assertThat(controller.obtenerMisConvocatorias(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void misConvocatoriasDevuelveLaListaDelUsuario() {
        when(servicio.obtenerMisConvocatorias(7L)).thenReturn(List.of());

        ResponseEntity<?> respuesta = controller.obtenerMisConvocatorias(autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(List.of());
    }

    @Test
    void responderDevuelve401SinAutenticacion() {
        assertThat(controller.responder(1L, 2L, VA, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void responderDevuelve200ConLaConvocatoriaActualizada() {
        MiConvocatoriaResponse esperado = mock(MiConvocatoriaResponse.class);
        when(servicio.responder(7L, 1L, 2L, VA)).thenReturn(esperado);

        ResponseEntity<?> respuesta = controller.responder(1L, 2L, VA, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(servicio).responder(7L, 1L, 2L, VA);
    }

    @Test
    void responderSinVinculoDevuelve403() {
        when(servicio.responder(any(), any(), any(), any())).thenThrow(new SecurityException("sin vínculo"));

        assertThat(controller.responder(1L, 2L, VA, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void responderConDatosNoValidosDevuelve400ConElMensaje() {
        when(servicio.responder(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("El motivo es obligatorio"));

        ResponseEntity<?> respuesta = controller.responder(1L, 2L, VA, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isEqualTo("El motivo es obligatorio");
    }

    @Test
    void responderConUnErrorInesperadoDevuelve500SinFiltrarElDetalle() {
        when(servicio.responder(any(), any(), any(), any())).thenThrow(new IllegalStateException("ORA-00942"));

        ResponseEntity<?> respuesta = controller.responder(1L, 2L, VA, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(respuesta.getBody())).doesNotContain("ORA-");
    }

}
