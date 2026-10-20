package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.ReporteModeracionResponse;
import com.mikedev.mutxamelcf.service.ModeracionService;

class AppModeracionControllerTest {

    private final ModeracionService servicio = mock(ModeracionService.class);
    private final AppModeracionController controller = new AppModeracionController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void devuelve401SinAutenticacion() {
        assertThat(controller.listar(null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.descartar(1L, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listaConElEstadoPedido() {
        when(servicio.listar(1L, "RESUELTOS")).thenReturn(List.of());

        var respuesta = controller.listar("RESUELTOS", autenticado("1"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(List.of());
    }

    @Test
    void devuelve403ParaCoordinadorYEntrenadorEnTodosLosEndpoints() {
        when(servicio.listar(any(), any())).thenThrow(new SecurityException("no"));
        when(servicio.contarPendientes(any())).thenThrow(new SecurityException("no"));
        doThrow(new SecurityException("no")).when(servicio).descartar(any(), any(), any());
        doThrow(new SecurityException("no")).when(servicio).borrarMensaje(any(), any(), any());
        doThrow(new SecurityException("no")).when(servicio).desactivarAutor(any(), any(), any(), anyBoolean());

        Authentication coordinador = autenticado("3");

        assertThat(controller.listar(null, coordinador).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.contarPendientes(coordinador).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.descartar(5L, null, coordinador).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.borrarMensaje(5L, null, coordinador).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.desactivarAutor(5L, null, coordinador).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void lasAccionesPasanLaNotaYResponden204() {
        var accion = new ReporteModeracionResponse.Accion("Nota", true);

        assertThat(controller.descartar(5L, accion, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.borrarMensaje(5L, accion, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.desactivarAutor(5L, accion, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        verify(servicio).descartar(1L, 5L, "Nota");
        verify(servicio).borrarMensaje(1L, 5L, "Nota");
        verify(servicio).desactivarAutor(1L, 5L, "Nota", true);
    }

    @Test
    void desactivarAutorSinCuerpoNoBorraElMensaje() {
        controller.desactivarAutor(5L, null, autenticado("1"));

        verify(servicio).desactivarAutor(eq(1L), eq(5L), eq(null), eq(false));
    }

    @Test
    void mapeaLosErroresDeNegocio() {
        doThrow(new IllegalArgumentException("No puedes desactivar tu propia cuenta")).when(servicio)
                .desactivarAutor(anyLong(), anyLong(), any(), anyBoolean());
        doThrow(new NoSuchElementException("no existe")).when(servicio).descartar(anyLong(), anyLong(), any());
        doThrow(new IllegalStateException("ya resuelto")).when(servicio).borrarMensaje(anyLong(), anyLong(), any());

        assertThat(controller.desactivarAutor(5L, null, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.descartar(5L, null, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.borrarMensaje(5L, null, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
