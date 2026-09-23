package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.TemporadaService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporadaControllerTest {

    @Test
    void guardarTemporadaDevuelveOkCuandoTieneExito() {
        TemporadaService service = mock(TemporadaService.class);
        TemporadaController controller = new TemporadaController(service);

        TemporadaDTO temporada = new TemporadaDTO();
        when(service.guardarTemporada(temporada)).thenReturn(true);

        ResponseEntity<Boolean> response = controller.guardarTemporada(temporada);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    void guardarTemporadaDevuelveBadRequestCuandoFalla() {
        TemporadaService service = mock(TemporadaService.class);
        TemporadaController controller = new TemporadaController(service);

        TemporadaDTO temporada = new TemporadaDTO();
        when(service.guardarTemporada(temporada)).thenReturn(false);

        assertThat(controller.guardarTemporada(temporada).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerPorIdDevuelve404SiNoExiste() {
        TemporadaService service = mock(TemporadaService.class);
        TemporadaController controller = new TemporadaController(service);

        when(service.obtenerPorId(99L)).thenReturn(null);

        assertThat(controller.obtenerPorId(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerPorIdDevuelveLaTemporada() {
        TemporadaService service = mock(TemporadaService.class);
        TemporadaController controller = new TemporadaController(service);

        when(service.obtenerPorId(1L)).thenReturn(new TemporadaDTO());

        assertThat(controller.obtenerPorId(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void obtenerTemporadaActivaDevuelve404SiNoHayNinguna() {
        TemporadaService service = mock(TemporadaService.class);
        TemporadaController controller = new TemporadaController(service);

        when(service.obtenerTemporadaActiva()).thenReturn(null);

        assertThat(controller.obtenerTemporadaActiva().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerTodosDelegaEnElServicio() {
        TemporadaService service = mock(TemporadaService.class);
        TemporadaController controller = new TemporadaController(service);

        when(service.obtenerTodos()).thenReturn(List.of(new TemporadaDTO()));

        assertThat(controller.obtenerTodos().getBody()).hasSize(1);
    }

    @Test
    void eliminarDevuelveNoContent() {
        TemporadaService service = mock(TemporadaService.class);
        TemporadaController controller = new TemporadaController(service);

        ResponseEntity<Void> response = controller.eliminar(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).eliminar(1L);
    }
}
