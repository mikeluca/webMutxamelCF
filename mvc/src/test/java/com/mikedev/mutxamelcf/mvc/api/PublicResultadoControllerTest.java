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
    void obtenerResultadoPrimerEquipoDevuelveElResultado() {
        PartidoService service = mock(PartidoService.class);
        PublicResultadoController controller = new PublicResultadoController(service);

        ResultadoDTO resultado = new ResultadoDTO();
        when(service.obtenerResultadoPrimerEquipo((String) null)).thenReturn(resultado);

        ResponseEntity<ResultadoDTO> response = controller.obtenerResultadoPrimerEquipo(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(resultado);
    }

    @Test
    void elIdiomaSaleDeAcceptLanguageYSinElSeContestaEnCastellano() {
        PartidoService service = mock(PartidoService.class);
        PublicResultadoController controller = new PublicResultadoController(service);
        ResultadoDTO resultado = new ResultadoDTO();
        when(service.obtenerResultadoPrimerEquipo("ca")).thenReturn(resultado);

        assertThat(controller.obtenerResultadoPrimerEquipo("ca-ES,ca;q=0.9,es;q=0.8").getBody()).isSameAs(resultado);

        assertThat(PublicResultadoController.idioma("en-GB")).isEqualTo("en");
        assertThat(PublicResultadoController.idioma("fr-FR,es;q=0.5")).isEqualTo("es");
        assertThat(PublicResultadoController.idioma("fr-FR")).isNull();
        assertThat(PublicResultadoController.idioma("*")).isNull();
        assertThat(PublicResultadoController.idioma("??? mal")).isNull();
        assertThat(PublicResultadoController.idioma(null)).isNull();
        assertThat(PublicResultadoController.idioma("  ")).isNull();
    }
}
