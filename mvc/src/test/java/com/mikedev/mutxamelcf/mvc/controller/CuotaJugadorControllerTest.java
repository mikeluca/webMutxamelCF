package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CuotaJugadorControllerTest {

    @Test
    void guardarDevuelveOkCuandoTieneExito() {
        CuotaJugadorService service = mock(CuotaJugadorService.class);
        CuotaJugadorController controller = new CuotaJugadorController(service);

        CuotaJugadorDTO cuota = new CuotaJugadorDTO();
        when(service.guardarCuota(cuota)).thenReturn(true);

        assertThat(controller.guardar(cuota).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void guardarDevuelveBadRequestCuandoFalla() {
        CuotaJugadorService service = mock(CuotaJugadorService.class);
        CuotaJugadorController controller = new CuotaJugadorController(service);

        CuotaJugadorDTO cuota = new CuotaJugadorDTO();
        when(service.guardarCuota(cuota)).thenReturn(false);

        assertThat(controller.guardar(cuota).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerTodosDelegaEnElServicio() {
        CuotaJugadorService service = mock(CuotaJugadorService.class);
        CuotaJugadorController controller = new CuotaJugadorController(service);

        when(service.obtenerTodos()).thenReturn(List.of(new CuotaJugadorDTO()));

        assertThat(controller.obtenerTodos()).hasSize(1);
    }

    @Test
    void obtenerPorIdDevuelve404SiNoExiste() {
        CuotaJugadorService service = mock(CuotaJugadorService.class);
        CuotaJugadorController controller = new CuotaJugadorController(service);

        when(service.obtenerPorId(99L)).thenReturn(null);

        assertThat(controller.obtenerPorId(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerPorJugadorDelegaEnElServicio() {
        CuotaJugadorService service = mock(CuotaJugadorService.class);
        CuotaJugadorController controller = new CuotaJugadorController(service);

        when(service.obtenerPorJugador(1L)).thenReturn(List.of(new CuotaJugadorDTO()));

        assertThat(controller.obtenerPorJugador(1L)).hasSize(1);
    }

    @Test
    void eliminarDevuelveNoContent() {
        CuotaJugadorService service = mock(CuotaJugadorService.class);
        CuotaJugadorController controller = new CuotaJugadorController(service);

        ResponseEntity<Void> response = controller.eliminar(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).eliminar(1L);
    }
}
