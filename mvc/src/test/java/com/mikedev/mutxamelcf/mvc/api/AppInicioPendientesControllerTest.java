package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.PendientesResponse;
import com.mikedev.mutxamelcf.model.ProximosPartidosResponse;
import com.mikedev.mutxamelcf.service.InicioAppService;
import com.mikedev.mutxamelcf.service.PendientesService;

class AppInicioPendientesControllerTest {

    private final InicioAppService inicio = mock(InicioAppService.class);
    private final PendientesService pendientes = mock(PendientesService.class);
    private final AppInicioController inicioController = new AppInicioController(inicio);
    private final AppPendientesController pendientesController = new AppPendientesController(pendientes);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void sinSesionEsUn401() {
        assertThat(inicioController.proximosPartidos(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pendientesController.pendientes(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void conSesionDevuelveLoDelServicioDeLaPropiaCuenta() {
        ProximosPartidosResponse proximos = new ProximosPartidosResponse(List.of());
        PendientesResponse lista = new PendientesResponse(List.of());
        when(inicio.obtenerProximosPartidos(7L)).thenReturn(proximos);
        when(pendientes.obtener(7L)).thenReturn(lista);

        assertThat(inicioController.proximosPartidos(autenticado("7")).getBody()).isSameAs(proximos);
        assertThat(pendientesController.pendientes(autenticado("7")).getBody()).isSameAs(lista);
    }

    @Test
    void unTokenQueNoEsUnIdEsUn401() {
        assertThat(inicioController.proximosPartidos(autenticado("abc")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pendientesController.pendientes(autenticado("abc")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unaSecurityExceptionEsUn403YUnFalloInternoUnCincoCientosSinDetalle() {
        when(inicio.obtenerProximosPartidos(8L)).thenThrow(new SecurityException("no"));
        when(pendientes.obtener(8L)).thenThrow(new SecurityException("no"));
        when(inicio.obtenerProximosPartidos(9L)).thenThrow(new IllegalStateException("ORA-00942"));
        when(pendientes.obtener(9L)).thenThrow(new IllegalStateException("ORA-00942"));

        assertThat(inicioController.proximosPartidos(autenticado("8")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pendientesController.pendientes(autenticado("8")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var a = inicioController.proximosPartidos(autenticado("9"));
        var b = pendientesController.pendientes(autenticado("9"));

        assertThat(a.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(b.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(a.getBody())).doesNotContain("ORA-");
        assertThat(String.valueOf(b.getBody())).doesNotContain("ORA-");
    }
}
