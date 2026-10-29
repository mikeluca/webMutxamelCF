package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.service.EquipoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipoAdminControllerTest {

    private EquipoService equiposService;

    private EquipoAdminController controller;

    @BeforeEach
    void setUp() {
        equiposService = mock(EquipoService.class);
        controller = new EquipoAdminController(equiposService);
    }

    @Test
    void borrarEquipoOkDevuelveMensajeDeExito() {
        ResponseEntity<Map<String, String>> response = controller.borrarEquipo(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Equipo eliminado correctamente.", response.getBody().get("mensaje"));
    }

    @Test
    void borrarEquipoConDependenciasDevuelveBadRequestConMensaje() {
        org.mockito.Mockito.doThrow(new IllegalStateException("El equipo tiene jugadores asignados"))
                .when(equiposService).eliminarEquipo(1L);

        ResponseEntity<Map<String, String>> response = controller.borrarEquipo(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El equipo tiene jugadores asignados", response.getBody().get("error"));
    }

    @Test
    void borrarEquipoConErrorInesperadoDevuelve500SinFiltrarElMensajeInterno() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo de conexion a BD"))
                .when(equiposService).eliminarEquipo(1L);

        ResponseEntity<Map<String, String>> response = controller.borrarEquipo(1L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("No se ha podido eliminar el equipo.", response.getBody().get("error"));
    }

    @Test
    void listarEquiposRellenaElModelo() {
        when(equiposService.obtenerTodosConEstadoTemporada()).thenReturn(List.of(new EquipoDTO()));
        when(equiposService.obtenerCategorias()).thenReturn(List.of("Senior"));

        Model model = new ExtendedModelMap();
        String vista = controller.listarEquipos(model);

        assertEquals("admin/equipos", vista);
        assertEquals(1, ((List<?>) model.getAttribute("equipos")).size());
    }

    @Test
    void guardarEquipoDevuelveOkCuandoTieneExito() {
        when(equiposService.guardar(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.guardarEquipo(null, "Senior", "Equipo A", "A",
                "Futbol", "F11", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void guardarEquipoDevuelveBadRequestSiYaExiste() {
        when(equiposService.guardar(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.guardarEquipo(null, "Senior", "Equipo A", "A",
                "Futbol", "F11", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Error. El equipo ya existe.", response.getBody().get("error"));
    }

    @Test
    void guardarEquipoDevuelve500SiElServicioLanzaExcepcionInesperada() {
        when(equiposService.guardar(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("fallo"));

        ResponseEntity<Map<String, String>> response = controller.guardarEquipo(null, "Senior", "Equipo A", "A",
                "Futbol", "F11", null);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void guardarEquipoGuardaLaModalidadYLaVacíaComoNull() {
        when(equiposService.guardar(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.ArgumentCaptor<EquipoDTO> captor = org.mockito.ArgumentCaptor.forClass(EquipoDTO.class);

        controller.guardarEquipo(null, "Benjamín", "Benjamín A", "A", "F", "F8", null);
        controller.guardarEquipo(7L, "Benjamín", "Benjamín A", "A", "F", "", null);
        controller.guardarEquipo(null, "Benjamín", "Benjamín A", "A", "F", null, null);

        org.mockito.Mockito.verify(equiposService, org.mockito.Mockito.times(3)).guardar(captor.capture());
        assertEquals("F8", captor.getAllValues().get(0).getModalidad());
        assertEquals(null, captor.getAllValues().get(1).getModalidad());
        assertEquals(null, captor.getAllValues().get(2).getModalidad());
    }

    @Test
    void editarSinParametroModalidadConservaLaActual() {
        EquipoDTO existente = new EquipoDTO();
        existente.setModalidad("F8");
        when(equiposService.obtenerEquipoPorId(7L)).thenReturn(existente);
        when(equiposService.guardar(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.ArgumentCaptor<EquipoDTO> captor = org.mockito.ArgumentCaptor.forClass(EquipoDTO.class);

        controller.guardarEquipo(7L, "Benjamín", "Benjamín A", "A", "F", null, null);

        org.mockito.Mockito.verify(equiposService).guardar(captor.capture());
        assertEquals("F8", captor.getValue().getModalidad());
    }

@Test
void guardarEquipoPasaSiJuegaEstaTemporada() {
    when(equiposService.guardar(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    org.mockito.ArgumentCaptor<EquipoDTO> captor = org.mockito.ArgumentCaptor.forClass(EquipoDTO.class);

    controller.guardarEquipo(7L, "Benjamín", "Benjamín A", "A", "F", "F8", 0);
    controller.guardarEquipo(7L, "Benjamín", "Benjamín A", "A", "F", "F8", 1);
    controller.guardarEquipo(7L, "Benjamín", "Benjamín A", "A", "F", "F8", null);

    org.mockito.Mockito.verify(equiposService, org.mockito.Mockito.times(3)).guardar(captor.capture());
    assertEquals(Boolean.FALSE, captor.getAllValues().get(0).getActivoTemporada());
    assertEquals(Boolean.TRUE, captor.getAllValues().get(1).getActivoTemporada());
    // Sin parámetro: nulo, que conserva el estado actual (o activo si es nuevo)
    assertEquals(null, captor.getAllValues().get(2).getActivoTemporada());
}

@Test
void guardarEquipoRechazaUnaModalidadInvalidaSinGuardar() {
        ResponseEntity<Map<String, String>> response = controller.guardarEquipo(null, "Senior", "Equipo A", "A", "F",
                "F7", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        org.mockito.Mockito.verify(equiposService, org.mockito.Mockito.never()).guardar(org.mockito.ArgumentMatchers.any());
    }
}
