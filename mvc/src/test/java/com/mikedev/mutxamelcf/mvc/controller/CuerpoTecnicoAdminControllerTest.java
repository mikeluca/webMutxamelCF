package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoForm;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CuerpoTecnicoAdminControllerTest {

    private CuerpoTecnicoService cuerpoTecnicoService;
    private EquipoService equiposService;
    private CuerpoTecnicoAdminController controller;

    @BeforeEach
    void setUp() {
        cuerpoTecnicoService = mock(CuerpoTecnicoService.class);
        equiposService = mock(EquipoService.class);
        controller = new CuerpoTecnicoAdminController(cuerpoTecnicoService, equiposService);
    }

    private static EquipoDTO equipo() {
        EquipoDTO equipo = new EquipoDTO();
        equipo.setId(1L);
        equipo.setCategoria("Senior");
        equipo.setNombre("Primer equipo");
        equipo.setDeporte("Futbol");
        return equipo;
    }

    @Test
    void listarCuerpoTecnicoRellenaElModelo() {
        when(equiposService.obtenerTodos()).thenReturn(List.of(equipo()));
        when(cuerpoTecnicoService.obtenerTodos()).thenReturn(List.of(new CuerpoTecnicoDTO()));
        when(equiposService.obtenerCategorias()).thenReturn(List.of("Senior"));

        Model model = new ExtendedModelMap();
        String vista = controller.listarCuerpoTecnico(model);

        assertEquals("admin/cuerpo-tecnico", vista);
        assertEquals(1, ((List<?>) model.getAttribute("cuerpoTecnico")).size());
    }

    @Test
    void guardarCuerpoTecnicoDevuelveBadRequestSiElEquipoNoExiste() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Juan");
        form.setEquipo(99L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(99L)).thenReturn(null);

        ResponseEntity<Map<String, String>> response = controller.guardarCuerpoTecnico(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El equipo seleccionado no existe.", response.getBody().get("error"));
        verify(cuerpoTecnicoService, never()).guardarCuerpoTecnico(any());
    }

    @Test
    void guardarCuerpoTecnicoDevuelveBadRequestSiLaFotoNoEsValida() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Juan");
        form.setEquipo(1L);
        byte[] fotoDemasiadoGrande = new byte[6 * 1024 * 1024];
        form.setFoto(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", fotoDemasiadoGrande));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());

        ResponseEntity<Map<String, String>> response = controller.guardarCuerpoTecnico(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(cuerpoTecnicoService, never()).guardarCuerpoTecnico(any());
    }

    @Test
    void guardarCuerpoTecnicoConservaLaFotoExistenteSiNoSeSubeUnaNueva() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setId(5L);
        form.setNombre("Juan");
        form.setEquipo(1L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));

        CuerpoTecnicoDTO existente = new CuerpoTecnicoDTO();
        existente.setFoto(new byte[] { 1, 2, 3 });
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(cuerpoTecnicoService.obtenerCuerpoTecnicoPorId(5L)).thenReturn(existente);
        when(cuerpoTecnicoService.guardarCuerpoTecnico(any())).thenReturn(true);

        controller.guardarCuerpoTecnico(form);

        org.mockito.ArgumentCaptor<CuerpoTecnicoDTO> captor = org.mockito.ArgumentCaptor.forClass(CuerpoTecnicoDTO.class);
        verify(cuerpoTecnicoService).guardarCuerpoTecnico(captor.capture());
        assertEquals(3, captor.getValue().getFoto().length);
    }

    @Test
    void guardarCuerpoTecnicoDevuelveBadRequestCuandoElServicioFalla() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Juan");
        form.setEquipo(1L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(cuerpoTecnicoService.guardarCuerpoTecnico(any())).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.guardarCuerpoTecnico(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Error dando de alta al cuerpo técnico.", response.getBody().get("error"));
    }

    @Test
    void guardarCuerpoTecnicoDevuelveOkCuandoTieneExito() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Juan");
        form.setEquipo(1L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(cuerpoTecnicoService.guardarCuerpoTecnico(any())).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.guardarCuerpoTecnico(form);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void guardarCuerpoTecnicoDevuelve500SiElServicioLanzaExcepcion() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Juan");
        form.setEquipo(1L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenThrow(new RuntimeException("fallo"));

        ResponseEntity<Map<String, String>> response = controller.guardarCuerpoTecnico(form);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void borrarCuerpoTecnicoOkDevuelveMensajeDeExito() {
        ResponseEntity<Map<String, String>> response = controller.borrarCuerpoTecnico(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(cuerpoTecnicoService).eliminarCuerpoTecnico(1L);
    }

    @Test
    void borrarCuerpoTecnicoConDependenciasDevuelveBadRequest() {
        org.mockito.Mockito.doThrow(new IllegalStateException("tiene entrenamientos asignados"))
                .when(cuerpoTecnicoService).eliminarCuerpoTecnico(1L);

        ResponseEntity<Map<String, String>> response = controller.borrarCuerpoTecnico(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("tiene entrenamientos asignados", response.getBody().get("error"));
    }

    @Test
    void borrarCuerpoTecnicoConErrorInesperadoDevuelve500() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo bd"))
                .when(cuerpoTecnicoService).eliminarCuerpoTecnico(1L);

        ResponseEntity<Map<String, String>> response = controller.borrarCuerpoTecnico(1L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getBody().get("mensaje"));
    }
}
