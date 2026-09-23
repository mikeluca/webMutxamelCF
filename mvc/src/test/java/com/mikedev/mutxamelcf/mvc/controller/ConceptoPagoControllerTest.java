package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;
import com.mikedev.mutxamelcf.service.ConceptoPagoService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConceptoPagoControllerTest {

    @Test
    void guardarDevuelveOkCuandoTieneExito() {
        ConceptoPagoService service = mock(ConceptoPagoService.class);
        ConceptoPagoController controller = new ConceptoPagoController(service);

        ConceptoPagoDTO concepto = new ConceptoPagoDTO();
        when(service.guardarConceptoPago(concepto)).thenReturn(true);

        ResponseEntity<Boolean> response = controller.guardar(concepto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    void guardarDevuelveBadRequestCuandoFalla() {
        ConceptoPagoService service = mock(ConceptoPagoService.class);
        ConceptoPagoController controller = new ConceptoPagoController(service);

        ConceptoPagoDTO concepto = new ConceptoPagoDTO();
        when(service.guardarConceptoPago(concepto)).thenReturn(false);

        assertThat(controller.guardar(concepto).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerTodosDelegaEnElServicio() {
        ConceptoPagoService service = mock(ConceptoPagoService.class);
        ConceptoPagoController controller = new ConceptoPagoController(service);

        when(service.obtenerTodos()).thenReturn(List.of(new ConceptoPagoDTO()));

        assertThat(controller.obtenerTodos()).hasSize(1);
    }

    @Test
    void obtenerPorTemporadaDelegaEnElServicio() {
        ConceptoPagoService service = mock(ConceptoPagoService.class);
        ConceptoPagoController controller = new ConceptoPagoController(service);

        when(service.obtenerPorTemporada(1L)).thenReturn(List.of(new ConceptoPagoDTO()));

        assertThat(controller.obtenerPorTemporada(1L)).hasSize(1);
    }

    @Test
    void obtenerPorIdDevuelve404SiNoExiste() {
        ConceptoPagoService service = mock(ConceptoPagoService.class);
        ConceptoPagoController controller = new ConceptoPagoController(service);

        when(service.obtenerPorId(99L)).thenReturn(null);

        assertThat(controller.obtenerPorId(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerPorIdDevuelveElConcepto() {
        ConceptoPagoService service = mock(ConceptoPagoService.class);
        ConceptoPagoController controller = new ConceptoPagoController(service);

        when(service.obtenerPorId(1L)).thenReturn(new ConceptoPagoDTO());

        assertThat(controller.obtenerPorId(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void eliminarDevuelveNoContent() {
        ConceptoPagoService service = mock(ConceptoPagoService.class);
        ConceptoPagoController controller = new ConceptoPagoController(service);

        ResponseEntity<Void> response = controller.eliminar(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).eliminar(1L);
    }
}
