package com.mikedev.mutxamelcf.mvc.api;

import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.PartidoService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicResultadoControllerTest {

    @Test
    void obtenerResultadosPideLosDeFutbol() {
        PartidoService service = mock(PartidoService.class);
        PublicResultadoController controller = new PublicResultadoController(service);

        when(service.obtenerResultados("F")).thenReturn(List.of(new ResultadoDTO()));

        assertThat(controller.obtenerResultados()).hasSize(1);
    }

    @Test
    void obtenerResultadoPrimerEquipoDevuelve404SiNoHayResultado() {
        PartidoService service = mock(PartidoService.class);
        PublicResultadoController controller = new PublicResultadoController(service);

        when(service.obtenerResultadoPrimerEquipo()).thenReturn(null);

        assertThat(controller.obtenerResultadoPrimerEquipo().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void obtenerResultadoPrimerEquipoDevuelveElResultado() {
        PartidoService service = mock(PartidoService.class);
        PublicResultadoController controller = new PublicResultadoController(service);

        ResultadoDTO resultado = new ResultadoDTO();
        when(service.obtenerResultadoPrimerEquipo()).thenReturn(resultado);

        ResponseEntity<ResultadoDTO> response = controller.obtenerResultadoPrimerEquipo();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(resultado);
    }
}
