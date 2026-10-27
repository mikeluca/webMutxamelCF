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
        form.setEquipoIds(List.of(99L));
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
        form.setEquipoIds(List.of(1L));
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
        form.setEquipoIds(List.of(1L));
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
        form.setEquipoIds(List.of(1L));
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(cuerpoTecnicoService.guardarCuerpoTecnico(any())).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.guardarCuerpoTecnico(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Error dando de alta al cuerpo técnico.", response.getBody().get("error"));
    }

    @Test
    void guardarCuerpoTecnicoConErrorDeBaseDeDatosDiceQueEsDeBaseDeDatos() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Juan");
        form.setEquipoIds(List.of(1L));
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(cuerpoTecnicoService.guardarCuerpoTecnico(any()))
                .thenThrow(new org.springframework.jdbc.BadSqlGrammarException("insert", "INSERT", new java.sql.SQLException("ORA-00904")));

        ResponseEntity<Map<String, String>> response = controller.guardarCuerpoTecnico(form);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("No se ha podido guardar: error de base de datos. Revisa los registros del servidor.",
                response.getBody().get("error"));
    }

    @Test
    void guardarCuerpoTecnicoDevuelveOkCuandoTieneExito() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Juan");
        form.setEquipoIds(List.of(1L));
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
        form.setEquipoIds(List.of(1L));
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

    // ---- equipos de la ficha y fusión de fichas ----

    @Test
    void guardarCuerpoTecnicoPasaLosEquiposConSuPuestoEnEseEquipo() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Juan");
        form.setPuesto("Entrenador");
        form.setEquipoIds(List.of(1L, 2L));
        form.setPuestosEquipo(List.of("", "Segundo entrenador"));
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(equiposService.obtenerEquipoPorId(2L)).thenReturn(equipo());
        when(cuerpoTecnicoService.guardarCuerpoTecnico(any())).thenReturn(true);

        controller.guardarCuerpoTecnico(form);

        org.mockito.ArgumentCaptor<CuerpoTecnicoDTO> captor = org.mockito.ArgumentCaptor.forClass(CuerpoTecnicoDTO.class);
        verify(cuerpoTecnicoService).guardarCuerpoTecnico(captor.capture());
        assertEquals("Entrenador", captor.getValue().getPuesto());
        assertEquals(2, captor.getValue().getEquipos().size());
        assertEquals(1L, captor.getValue().getEquipos().get(0).equipoId());
        assertEquals("", captor.getValue().getEquipos().get(0).puesto());
        assertEquals("Segundo entrenador", captor.getValue().getEquipos().get(1).puesto());
    }

    @Test
    void guardarCuerpoTecnicoSinEquiposEsValido() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Coordinador");
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(cuerpoTecnicoService.guardarCuerpoTecnico(any())).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.guardarCuerpoTecnico(form);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        org.mockito.ArgumentCaptor<CuerpoTecnicoDTO> captor = org.mockito.ArgumentCaptor.forClass(CuerpoTecnicoDTO.class);
        verify(cuerpoTecnicoService).guardarCuerpoTecnico(captor.capture());
        assertEquals(0, captor.getValue().getEquipos().size());
    }

    @Test
    void unDatoNoValidoDelServicioEsBadRequestConSuMensaje() {
        CuerpoTecnicoForm form = new CuerpoTecnicoForm();
        form.setNombre("Juan");
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(cuerpoTecnicoService.guardarCuerpoTecnico(any()))
                .thenThrow(new IllegalArgumentException("El equipo seleccionado no existe."));

        ResponseEntity<Map<String, String>> response = controller.guardarCuerpoTecnico(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El equipo seleccionado no existe.", response.getBody().get("error"));
    }

    @Test
    void fusionarDelegaEnElServicio() {
        ResponseEntity<Map<String, String>> response = controller.fusionar(10L, List.of(11L, 12L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(cuerpoTecnicoService).fusionarFichas(10L, List.of(11L, 12L));
    }

    @Test
    void fusionarUnaFichaConsigoMismaEsBadRequest() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("No se puede fusionar una ficha consigo misma."))
                .when(cuerpoTecnicoService).fusionarFichas(10L, List.of(10L));

        ResponseEntity<Map<String, String>> response = controller.fusionar(10L, List.of(10L));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("No se puede fusionar una ficha consigo misma.", response.getBody().get("error"));
    }

    @Test
    void fusionarConUnErrorDeBaseDeDatosDiceQueNoSeHaCambiadoNada() {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("ORA-02292"))
                .when(cuerpoTecnicoService).fusionarFichas(10L, List.of(11L));

        ResponseEntity<Map<String, String>> response = controller.fusionar(10L, List.of(11L));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("No se han podido fusionar las fichas: error de base de datos. No se ha cambiado nada.",
                response.getBody().get("error"));
    }

    @Test
    void elResumenDeLaFusionSeDevuelveSinCambiarNada() {
        when(cuerpoTecnicoService.resumenFusion(10L, List.of(11L))).thenReturn(
                new com.mikedev.mutxamelcf.model.FusionFichasDTO("Marcos Ejemplo", List.of("Lucía Prueba"),
                        List.of("Cadete A"), List.of(), 1));

        ResponseEntity<?> response = controller.resumenFusion(10L, List.of(11L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(cuerpoTecnicoService, never()).fusionarFichas(any(), any());
    }
}
