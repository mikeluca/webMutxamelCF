package com.mikedev.mutxamelcf.mvc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.mikedev.mutxamelcf.model.AsignacionEquipoFfcv;
import com.mikedev.mutxamelcf.model.PrevisualizacionFfcv;
import com.mikedev.mutxamelcf.model.ResultadoImportacionFfcv;
import com.mikedev.mutxamelcf.service.ImportacionFfcvService;

class ImportacionFfcvAdminControllerTest {

    private ImportacionFfcvService service;
    private ImportacionFfcvAdminController controller;

    private static final PrevisualizacionFfcv PREVIA =
            new PrevisualizacionFfcv(null, null, List.of(), List.of(), List.of(), List.of());

    @BeforeEach
    void setUp() {
        service = mock(ImportacionFfcvService.class);
        controller = new ImportacionFfcvAdminController(service);
    }

    private static MockMultipartFile archivo(byte[] contenido) {
        return new MockMultipartFile("archivo", "LstPartidos.csv", "text/csv", contenido);
    }

    @Test
    void previsualizarDevuelveLaPrevisualizacionDelServicio() {
        byte[] contenido = "datos".getBytes();
        when(service.previsualizar(contenido)).thenReturn(PREVIA);

        ResponseEntity<?> respuesta = controller.previsualizar(archivo(contenido));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isSameAs(PREVIA);
    }

    @Test
    void previsualizarRechazaUnArchivoVacio() {
        ResponseEntity<?> respuesta = controller.previsualizar(archivo(new byte[0]));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).previsualizar(any());
    }

    @Test
    void previsualizarRechazaUnArchivoDemasiadoGrande() {
        byte[] enorme = new byte[(int) ImportacionFfcvAdminController.TAMANO_MAXIMO_BYTES + 1];

        ResponseEntity<?> respuesta = controller.previsualizar(archivo(enorme));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).previsualizar(any());
    }

    @Test
    void previsualizarTraduceUnArgumentoInvalidoAUn400ConSuMensaje() {
        when(service.previsualizar(any())).thenThrow(new IllegalArgumentException("El archivo está vacío"));

        ResponseEntity<?> respuesta = controller.previsualizar(archivo("x".getBytes()));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isEqualTo(Map.of("error", "El archivo está vacío"));
    }

    @Test
    void previsualizarDevuelve500SinFiltrarElDetalleSiFallaAlgoInesperado() {
        when(service.previsualizar(any())).thenThrow(new IllegalStateException("detalle interno"));

        ResponseEntity<?> respuesta = controller.previsualizar(archivo("x".getBytes()));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody().toString()).doesNotContain("detalle interno");
    }

    @Test
    void confirmarPasaElUsuarioLaPrevisualizacionYLasAsignaciones() {
        List<AsignacionEquipoFfcv> asignaciones = List.of(new AsignacionEquipoFfcv("Comp", "A", 5L));
        ResultadoImportacionFfcv resultado = new ResultadoImportacionFfcv(1, 0, 0, 0, 0, 0, 0, List.of());
        when(service.confirmar(eq("coordinador"), eq(PREVIA), eq(asignaciones))).thenReturn(resultado);

        ResponseEntity<?> respuesta = controller.confirmar(
                new ConfirmarFfcvRequest(PREVIA, asignaciones),
                new UsernamePasswordAuthenticationToken("coordinador", "x"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isSameAs(resultado);
    }

    @Test
    void confirmarSinPrevisualizacionEsUnaPeticionIncorrecta() {
        ResponseEntity<?> respuesta = controller.confirmar(new ConfirmarFfcvRequest(null, List.of()), null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).confirmar(any(), any(), any());
    }

    @Test
    void confirmarTraduceUnArgumentoInvalidoAUn400() {
        when(service.confirmar(any(), any(), any())).thenThrow(new IllegalArgumentException("El equipo indicado no existe"));

        ResponseEntity<?> respuesta = controller.confirmar(new ConfirmarFfcvRequest(PREVIA, List.of()), null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isEqualTo(Map.of("error", "El equipo indicado no existe"));
    }

}
