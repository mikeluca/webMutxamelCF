package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.ResultadoDTO;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.ResultadoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultadoAdminControllerTest {

    private ResultadoService resultadoService;
    private EquipoService equiposService;
    private ResultadoAdminController controller;

    @BeforeEach
    void setUp() {
        resultadoService = mock(ResultadoService.class);
        equiposService = mock(EquipoService.class);
        controller = new ResultadoAdminController(resultadoService, equiposService);
    }

    @Test
    void listarResultadosRellenaElModelo() {
        when(equiposService.obtenerTodos()).thenReturn(List.of(new EquipoDTO()));
        when(resultadoService.obtenerResultados("F")).thenReturn(List.of(new ResultadoDTO()));
        when(resultadoService.obtenerResultados("FS")).thenReturn(List.of());
        when(equiposService.obtenerCategorias()).thenReturn(List.of("Senior"));

        Model model = new ExtendedModelMap();
        String vista = controller.listarResultados(model);

        assertEquals("admin/calendario-resultados", vista);
        assertEquals(1, ((List<?>) model.getAttribute("resultadosFutbol")).size());
        assertTrue(((List<?>) model.getAttribute("resultadosFutbolSala")).isEmpty());
    }

    @Test
    void actualizarResultadoRedirigeConErrorSiLaFechaEsInvalida() {
        String vista = controller.actualizarResultado("Primer equipo", "Senior", "2-1", "Rival",
                "fecha-invalida", "18:00", "Campo municipal");

        assertEquals("redirect:/admin/calendario-resultados?error=Fecha inválida", vista);
        verify(resultadoService, org.mockito.Mockito.never()).actualizarResultado(any());
    }

    @Test
    void actualizarResultadoAceptaDiaVacio() {
        String vista = controller.actualizarResultado("Primer equipo", "Senior", "2-1", "Rival",
                "", "18:00", "Campo municipal");

        assertEquals("redirect:/admin/calendario-resultados", vista);
        verify(resultadoService).actualizarResultado(any());
    }

    @Test
    void actualizarResultadoRedirigeConErrorSiElServicioFalla() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo bd")).when(resultadoService)
                .actualizarResultado(any());

        String vista = controller.actualizarResultado("Primer equipo", "Senior", "2-1", "Rival",
                "01/01/2026", "18:00", "Campo municipal");

        assertEquals("redirect:/admin/calendario-resultados?error=Error al actualizar el resultado", vista);
    }

    @Test
    void actualizarResultadoRedirigeCorrectamenteCuandoTieneExito() {
        String vista = controller.actualizarResultado("Primer equipo", "Senior", "2-1", "Rival",
                "01/01/2026", "18:00", "Campo municipal");

        assertEquals("redirect:/admin/calendario-resultados", vista);
        org.mockito.ArgumentCaptor<ResultadoDTO> captor = org.mockito.ArgumentCaptor.forClass(ResultadoDTO.class);
        verify(resultadoService).actualizarResultado(captor.capture());
        assertEquals("Rival", captor.getValue().getRival());
    }
}
