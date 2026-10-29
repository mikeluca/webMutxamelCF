package com.mikedev.mutxamelcf.mvc.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.NuevaTemporadaService;

class NuevaTemporadaAdminControllerTest {

    private NuevaTemporadaService nuevaTemporadaService;
    private NuevaTemporadaAdminController controller;

    @BeforeEach
    void setUp() {
        nuevaTemporadaService = mock(NuevaTemporadaService.class);
        controller = new NuevaTemporadaAdminController(nuevaTemporadaService, mock(EquipoService.class));
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
}
