package com.mikedev.mutxamelcf.mvc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.mikedev.mutxamelcf.model.ResultadoPreguntaContinuidadDTO;
import com.mikedev.mutxamelcf.service.ContinuidadService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.NuevaTemporadaService;

class NuevaTemporadaAdminControllerTest {

    private NuevaTemporadaService nuevaTemporadaService;
    private NuevaTemporadaAdminController controller;
    private ContinuidadService continuidadService;

    @BeforeEach
    void setUp() {
        nuevaTemporadaService = mock(NuevaTemporadaService.class);
        continuidadService = mock(ContinuidadService.class);
        controller = new NuevaTemporadaAdminController(nuevaTemporadaService, mock(EquipoService.class),
                continuidadService);
    }

    @Test
    void continuidadDelegaEnElServicioYDevuelveMensaje() {
        ResponseEntity<Map<String, String>> respuesta = controller.continuidad(2L, 7L, "DUDA");

        verify(nuevaTemporadaService).marcarContinuidad(2L, 7L, "DUDA");
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals("Continuidad guardada.", respuesta.getBody().get("mensaje"));
    }

    @Test
    void unaContinuidadInvalidaDevuelve400ConElMensajeDelServicio() {
        doThrow(new IllegalArgumentException("La continuidad tiene que ser PENDIENTE, SIGUE, NO_SIGUE o DUDA."))
                .when(nuevaTemporadaService).marcarContinuidad(2L, 7L, "TAL_VEZ");

        ResponseEntity<Map<String, String>> respuesta = controller.continuidad(2L, 7L, "TAL_VEZ");

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertEquals("La continuidad tiene que ser PENDIENTE, SIGUE, NO_SIGUE o DUDA.", respuesta.getBody().get("error"));
    }

    @Test
    void anadirPruebaDelegaEnElServicio() {
        ResponseEntity<Map<String, String>> respuesta = controller.anadirPrueba(2L, "Luis", "Gil", 2016);

        verify(nuevaTemporadaService).anadirPrueba(2L, "Luis", "Gil", 2016);
        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
    }

    @Test
    void anadirPruebaSinAnioDevuelve400() {
        doThrow(new IllegalArgumentException("El año de nacimiento es obligatorio para un jugador de prueba."))
                .when(nuevaTemporadaService).anadirPrueba(2L, "Luis", "Gil", null);

        ResponseEntity<Map<String, String>> respuesta = controller.anadirPrueba(2L, "Luis", "Gil", null);

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
    }

    @Test
    void pedirContinuidadDelegaYResumeCuantosSeHanPreguntado() {
        when(continuidadService.pedirConfirmacion(2L))
                .thenReturn(new ResultadoPreguntaContinuidadDTO(2L, "2027/2028", 30, 12, 9));

        ResponseEntity<Map<String, String>> respuesta = controller.pedirContinuidad(2L);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals("Pregunta enviada: 12 jugador(es), 9 cuenta(s) avisada(s).", respuesta.getBody().get("mensaje"));
    }

    @Test
    void pedirContinuidadSinNadieNuevoLoDiceYUnDestinoInvalidoEsUn400() {
        when(continuidadService.pedirConfirmacion(2L))
                .thenReturn(new ResultadoPreguntaContinuidadDTO(2L, "2027/2028", 0, 0, 0));
        when(continuidadService.pedirConfirmacion(9L)).thenThrow(new IllegalArgumentException("La temporada de destino no existe."));

        assertEquals(HttpStatus.OK, controller.pedirContinuidad(2L).getStatusCode());
        assertTrue(controller.pedirContinuidad(2L).getBody().get("mensaje").startsWith("No había nadie nuevo"));
        assertEquals(HttpStatus.BAD_REQUEST, controller.pedirContinuidad(9L).getStatusCode());
    }
}
