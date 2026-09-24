package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.PartidoGuardarRequest;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.PartidoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultadoAdminControllerTest {

    private PartidoService partidoService;
    private EquipoService equiposService;
    private ResultadoAdminController controller;

    @BeforeEach
    void setUp() {
        partidoService = mock(PartidoService.class);
        equiposService = mock(EquipoService.class);
        controller = new ResultadoAdminController(partidoService, equiposService);
    }

    private static EquipoDTO equipo(Long id, String deporte) {
        EquipoDTO equipo = new EquipoDTO();
        equipo.setId(id);
        equipo.setNombre("Senior A");
        equipo.setDeporte(deporte);
        return equipo;
    }

    @Test
    void listarResultadosAgrupaLosEquiposPorDeporte() {
        EquipoDTO equipoFutbol = equipo(1L, "F");
        EquipoDTO equipoFutbolSala = equipo(2L, "FS");

        when(equiposService.obtenerTodos()).thenReturn(List.of(equipoFutbol, equipoFutbolSala));
        when(partidoService.obtenerPorEquipo(1L)).thenReturn(List.of(new PartidoDTO()));
        when(partidoService.obtenerPorEquipo(2L)).thenReturn(List.of());
        when(equiposService.obtenerCategorias()).thenReturn(List.of("Senior"));

        Model model = new ExtendedModelMap();
        String vista = controller.listarResultados(model);

        assertEquals("admin/calendario-resultados", vista);

        @SuppressWarnings("unchecked")
        List<EquipoConPartidos> equiposFutbol = (List<EquipoConPartidos>) model.getAttribute("equiposFutbol");
        @SuppressWarnings("unchecked")
        List<EquipoConPartidos> equiposFutbolSala = (List<EquipoConPartidos>) model.getAttribute("equiposFutbolSala");

        assertEquals(1, equiposFutbol.size());
        assertEquals(1, equiposFutbol.get(0).getPartidos().size());
        assertEquals(1, equiposFutbolSala.size());
        assertTrue(equiposFutbolSala.get(0).getPartidos().isEmpty());
    }

    // ---------- crear ----------

    @Test
    void crearRedirigeConErrorSiLaFechaEsInvalida() {
        String vista = controller.crear(1L, "Rival", "fecha-invalida", "18:00", "Campo municipal", "2-1");

        assertEquals("redirect:/admin/calendario-resultados?error=Fecha inválida", vista);
        verify(partidoService, never()).crearComoAdmin(any());
    }

    @Test
    void crearAceptaDiaVacio() {
        String vista = controller.crear(1L, "Rival", "", "18:00", "Campo municipal", null);

        assertEquals("redirect:/admin/calendario-resultados", vista);
        verify(partidoService).crearComoAdmin(any());
    }

    @Test
    void crearRedirigeConErrorSiElServicioFalla() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo bd")).when(partidoService).crearComoAdmin(any());

        String vista = controller.crear(1L, "Rival", "01/01/2026", "18:00", "Campo municipal", "2-1");

        assertEquals("redirect:/admin/calendario-resultados?error=Error al crear el partido", vista);
    }

    @Test
    void crearRedirigeCorrectamenteCuandoTieneExitoYConstruyeElRequest() {
        String vista = controller.crear(1L, "Rival", "01/01/2026", "18:00", "Campo municipal", "2-1");

        assertEquals("redirect:/admin/calendario-resultados", vista);

        org.mockito.ArgumentCaptor<PartidoGuardarRequest> captor = org.mockito.ArgumentCaptor
                .forClass(PartidoGuardarRequest.class);
        verify(partidoService).crearComoAdmin(captor.capture());

        PartidoGuardarRequest request = captor.getValue();
        assertEquals(1L, request.getEquipoId());
        assertEquals("Rival", request.getRival());
        assertEquals(LocalDate.of(2026, 1, 1), request.getDia());
        assertEquals("18:00", request.getHora());
        assertEquals("Campo municipal", request.getCampo());
        assertEquals("2-1", request.getResultado());
    }

    // ---------- actualizar ----------

    @Test
    void actualizarRedirigeConErrorSiLaFechaEsInvalida() {
        String vista = controller.actualizar(5L, 1L, "Rival", "fecha-invalida", "18:00", "Campo municipal", "2-1");

        assertEquals("redirect:/admin/calendario-resultados?error=Fecha inválida", vista);
        verify(partidoService, never()).actualizarComoAdmin(eq(5L), any());
    }

    @Test
    void actualizarAceptaDiaVacio() {
        String vista = controller.actualizar(5L, 1L, "Rival", "", "18:00", "Campo municipal", null);

        assertEquals("redirect:/admin/calendario-resultados", vista);
        verify(partidoService).actualizarComoAdmin(eq(5L), any());
    }

    @Test
    void actualizarRedirigeConErrorSiElServicioFalla() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo bd")).when(partidoService)
                .actualizarComoAdmin(eq(5L), any());

        String vista = controller.actualizar(5L, 1L, "Rival", "01/01/2026", "18:00", "Campo municipal", "2-1");

        assertEquals("redirect:/admin/calendario-resultados?error=Error al actualizar el partido", vista);
    }

    @Test
    void actualizarRedirigeCorrectamenteCuandoTieneExito() {
        String vista = controller.actualizar(5L, 1L, "Rival", "01/01/2026", "18:00", "Campo municipal", "2-1");

        assertEquals("redirect:/admin/calendario-resultados", vista);

        org.mockito.ArgumentCaptor<PartidoGuardarRequest> captor = org.mockito.ArgumentCaptor
                .forClass(PartidoGuardarRequest.class);
        verify(partidoService).actualizarComoAdmin(eq(5L), captor.capture());
        assertEquals("Rival", captor.getValue().getRival());
    }
}
