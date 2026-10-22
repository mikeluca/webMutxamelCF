package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse;
import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse.EntrenamientosResumen;
import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse.PartidosResumen;
import com.mikedev.mutxamelcf.service.ResumenTemporadaService;

class AppResumenTemporadaControllerTest {

    private final ResumenTemporadaService servicio = mock(ResumenTemporadaService.class);
    private final AppResumenTemporadaController controller = new AppResumenTemporadaController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void devuelve401SinAutenticacion() {
        assertThat(controller.obtenerResumen(1L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devuelve200ConElResumen() {
        ResumenTemporadaResponse resumen = new ResumenTemporadaResponse(
                null, new PartidosResumen(0, 0, 0, 0, 0, 0), new EntrenamientosResumen(0, 0, 0, 0, null), true);
        when(servicio.obtenerResumen(7L, 100L)).thenReturn(resumen);

        ResponseEntity<?> respuesta = controller.obtenerResumen(100L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isSameAs(resumen);
    }

    @Test
    void devuelve403SiElServicioNiegaElAcceso() {
        when(servicio.obtenerResumen(any(), any())).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.obtenerResumen(100L, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void devuelve500SinFiltrarElDetalle() {
        when(servicio.obtenerResumen(any(), any())).thenThrow(new IllegalStateException("ORA-00942"));

        ResponseEntity<?> respuesta = controller.obtenerResumen(100L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(respuesta.getBody())).doesNotContain("ORA-");
    }

}
