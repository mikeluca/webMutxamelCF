package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.mikedev.mutxamelcf.model.FichaPartidoDTO;
import com.mikedev.mutxamelcf.service.FichaPartidoService;

class PublicFichaPartidoControllerTest {

    private final FichaPartidoService servicio = mock(FichaPartidoService.class);
    private final PublicFichaPartidoController controller = new PublicFichaPartidoController(servicio);

    private static FichaPartidoDTO ficha() {
        return new FichaPartidoDTO(
                new FichaPartidoDTO.Cabecera(30L, "Mutxamel CF", "Pego C.F.", LocalDate.of(2026, 11, 8), "18:00", 1, 0,
                        "EN_JUEGO", "Preferente Valenciana · Jornada 9"),
                List.of(), new FichaPartidoDTO.AlineacionFicha(null, List.of(), List.of()), List.of());
    }

    @Test
    void devuelveLaFichaYElIdiomaSaleDeAcceptLanguage() {
        when(servicio.obtenerFicha(30L, "ca")).thenReturn(ficha());

        ResponseEntity<FichaPartidoDTO> respuesta = controller.ficha(30L, "ca-ES,ca;q=0.9,es;q=0.8");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(ficha());
    }

    @Test
    void sinAcceptLanguageOConUnIdiomaNoAdmitidoSeContestaEnCastellano() {
        when(servicio.obtenerFicha(30L, null)).thenReturn(ficha());

        assertThat(controller.ficha(30L, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.ficha(30L, "fr").getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(servicio, org.mockito.Mockito.times(2)).obtenerFicha(30L, null);
    }

    @Test
    void unPartidoDeOtroEquipoOInexistenteEs404() {
        when(servicio.obtenerFicha(31L, null)).thenReturn(null);

        assertThat(controller.ficha(31L, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
