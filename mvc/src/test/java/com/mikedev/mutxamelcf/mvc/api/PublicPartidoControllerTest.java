package com.mikedev.mutxamelcf.mvc.api;

import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.service.PartidoService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicPartidoControllerTest {

    @Test
    void obtenerUltimosDevuelve400SiNoIndicaNingunEquipo() {
        PartidoService service = mock(PartidoService.class);
        PublicPartidoController controller = new PublicPartidoController(service);

        ResponseEntity<?> response = controller.obtenerUltimos(null, null, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerUltimosDevuelve400SiIndicaAmbosEquipos() {
        PartidoService service = mock(PartidoService.class);
        PublicPartidoController controller = new PublicPartidoController(service);

        ResponseEntity<?> response = controller.obtenerUltimos(1L, "Senior A", 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerUltimosUsaElEquipoIdCuandoSeIndica() {
        PartidoService service = mock(PartidoService.class);
        PublicPartidoController controller = new PublicPartidoController(service);

        when(service.obtenerUltimosPorEquipo(1L, 5)).thenReturn(List.of(new PartidoDTO()));

        ResponseEntity<?> response = controller.obtenerUltimos(1L, null, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).hasSize(1);
        verify(service, never()).obtenerUltimosPorEquipoNombre(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void obtenerUltimosUsaElNombreDeEquipoCuandoSeIndica() {
        PartidoService service = mock(PartidoService.class);
        PublicPartidoController controller = new PublicPartidoController(service);

        when(service.obtenerUltimosPorEquipoNombre("Senior A", 5)).thenReturn(List.of(new PartidoDTO()));

        ResponseEntity<?> response = controller.obtenerUltimos(null, "Senior A", 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).hasSize(1);
    }

    @Test
    void obtenerUltimosAcotaElLimiteMaximo() {
        PartidoService service = mock(PartidoService.class);
        PublicPartidoController controller = new PublicPartidoController(service);

        controller.obtenerUltimos(1L, null, 100);

        verify(service).obtenerUltimosPorEquipo(1L, 20);
    }

    @Test
    void obtenerUltimosAcotaElLimiteMinimo() {
        PartidoService service = mock(PartidoService.class);
        PublicPartidoController controller = new PublicPartidoController(service);

        controller.obtenerUltimos(1L, null, 0);

        verify(service).obtenerUltimosPorEquipo(1L, 1);
    }
}
