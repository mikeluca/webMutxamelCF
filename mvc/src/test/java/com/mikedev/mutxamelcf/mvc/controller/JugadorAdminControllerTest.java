package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorForm;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

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

class JugadorAdminControllerTest {

    private JugadorService jugadoresService;
    private FamiliarService familiarService;
    private EquipoService equiposService;
    private JugadorAdminController controller;

    @BeforeEach
    void setUp() {
        jugadoresService = mock(JugadorService.class);
        familiarService = mock(FamiliarService.class);
        equiposService = mock(EquipoService.class);
        controller = new JugadorAdminController(jugadoresService, familiarService, equiposService);
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
    void listarJugadoresRellenaElModelo() {
        when(equiposService.obtenerTodos()).thenReturn(List.of(equipo()));
        when(jugadoresService.obtenerTodos()).thenReturn(List.of(new JugadorDTO()));
        when(equiposService.obtenerCategorias()).thenReturn(List.of("Senior"));
        when(familiarService.obtenerTodos()).thenReturn(List.of(new FamiliarDTO()));

        Model model = new ExtendedModelMap();
        String vista = controller.listarJugadores(model);

        assertEquals("admin/jugadores", vista);
        assertEquals(1, ((List<?>) model.getAttribute("jugadores")).size());
        assertEquals(1, ((List<?>) model.getAttribute("listaFamiliares")).size());
    }

    @Test
    void guardarJugadorDevuelveBadRequestSiElEquipoNoExiste() {
        JugadorForm form = new JugadorForm();
        form.setNombre("Pedro");
        form.setEquipo(99L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(99L)).thenReturn(null);

        ResponseEntity<Map<String, String>> response = controller.guardarJugador(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El equipo seleccionado no existe.", response.getBody().get("error"));
        verify(jugadoresService, never()).guardarJugador(any());
    }

    @Test
    void guardarJugadorAsignaDorsalNuloCuandoNoSeIndica() {
        JugadorForm form = new JugadorForm();
        form.setNombre("Pedro");
        form.setEquipo(1L);
        form.setDorsal(null);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(jugadoresService.guardarJugador(any())).thenReturn(true);

        controller.guardarJugador(form);

        org.mockito.ArgumentCaptor<JugadorDTO> captor = org.mockito.ArgumentCaptor.forClass(JugadorDTO.class);
        verify(jugadoresService).guardarJugador(captor.capture());
        assertNull(captor.getValue().getDorsal());
    }

    @Test
    void guardarJugadorDevuelveBadRequestSiLaFotoNoEsValida() {
        JugadorForm form = new JugadorForm();
        form.setNombre("Pedro");
        form.setEquipo(1L);
        byte[] fotoDemasiadoGrande = new byte[6 * 1024 * 1024];
        form.setFoto(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", fotoDemasiadoGrande));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());

        ResponseEntity<Map<String, String>> response = controller.guardarJugador(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(jugadoresService, never()).guardarJugador(any());
    }

    @Test
    void guardarJugadorConservaLaFotoExistenteSiNoSeSubeUnaNueva() {
        JugadorForm form = new JugadorForm();
        form.setId(5L);
        form.setNombre("Pedro");
        form.setEquipo(1L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));

        JugadorDTO existente = new JugadorDTO();
        existente.setFoto(new byte[] { 1, 2, 3 });
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(jugadoresService.obtenerJugadorPorId(5L)).thenReturn(existente);
        when(jugadoresService.guardarJugador(any())).thenReturn(true);

        controller.guardarJugador(form);

        org.mockito.ArgumentCaptor<JugadorDTO> captor = org.mockito.ArgumentCaptor.forClass(JugadorDTO.class);
        verify(jugadoresService).guardarJugador(captor.capture());
        assertEquals(3, captor.getValue().getFoto().length);
    }

    @Test
    void guardarJugadorDevuelveBadRequestCuandoElServicioFalla() {
        JugadorForm form = new JugadorForm();
        form.setNombre("Pedro");
        form.setEquipo(1L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(jugadoresService.guardarJugador(any())).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.guardarJugador(form);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Error dando de alta al jugador.", response.getBody().get("error"));
    }

    @Test
    void guardarJugadorDevuelveOkCuandoTieneExito() {
        JugadorForm form = new JugadorForm();
        form.setNombre("Pedro");
        form.setEquipo(1L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenReturn(equipo());
        when(jugadoresService.guardarJugador(any())).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.guardarJugador(form);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void guardarJugadorDevuelve500SiElServicioLanzaExcepcion() {
        JugadorForm form = new JugadorForm();
        form.setNombre("Pedro");
        form.setEquipo(1L);
        form.setFoto(new MockMultipartFile("foto", new byte[0]));
        when(equiposService.obtenerEquipoPorId(1L)).thenThrow(new RuntimeException("fallo"));

        ResponseEntity<Map<String, String>> response = controller.guardarJugador(form);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void borrarJugadorOkDevuelveMensajeDeExito() {
        ResponseEntity<Map<String, String>> response = controller.borrarJugador(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(jugadoresService).eliminarJugador(1L);
    }

    @Test
    void borrarJugadorConDependenciasDevuelveBadRequest() {
        org.mockito.Mockito.doThrow(new IllegalStateException("tiene cuotas asignadas"))
                .when(jugadoresService).eliminarJugador(1L);

        ResponseEntity<Map<String, String>> response = controller.borrarJugador(1L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("tiene cuotas asignadas", response.getBody().get("error"));
    }

    @Test
    void borrarJugadorConErrorInesperadoDevuelve500() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo bd"))
                .when(jugadoresService).eliminarJugador(1L);

        ResponseEntity<Map<String, String>> response = controller.borrarJugador(1L);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
