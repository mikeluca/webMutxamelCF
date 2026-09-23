package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.FamiliarJugadorDTO;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FamiliarControllerTest {

    private FamiliarService familiarService;
    private FamiliarJugadorService familiarJugadorService;
    private JugadorService jugadorService;
    private FamiliarController controller;

    @BeforeEach
    void setUp() {
        familiarService = mock(FamiliarService.class);
        familiarJugadorService = mock(FamiliarJugadorService.class);
        jugadorService = mock(JugadorService.class);
        controller = new FamiliarController(familiarService, familiarJugadorService, jugadorService);
    }

    private static FamiliarDTO familiarConContacto() {
        FamiliarDTO dto = new FamiliarDTO();
        dto.setNombre("Ana");
        dto.setTelefono("600000000");
        dto.setEmail("ana@example.com");
        return dto;
    }

    @Test
    void listarFamiliaresRellenaElModelo() {
        when(familiarService.obtenerTodos()).thenReturn(List.of(new FamiliarDTO()));
        when(jugadorService.obtenerTodos()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String vista = controller.listarFamiliares(model);

        assertThat(vista).isEqualTo("admin/familiares");
        assertThat((List<?>) model.getAttribute("familiares")).hasSize(1);
    }

    @Test
    void obtenerFamiliarDevuelve404SiNoExiste() {
        when(familiarService.obtenerFamiliarPorId(99L)).thenReturn(null);

        assertThat(controller.obtenerFamiliar(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void guardarFamiliarRechazaSiFaltaTelefonoOEmail() {
        FamiliarDTO dto = new FamiliarDTO();
        dto.setNombre("Ana");

        ResponseEntity<Map<String, Object>> response = controller.guardarFamiliar(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(familiarService, never()).guardarFamiliar(any());
    }

    @Test
    void guardarFamiliarPonePorDefectoLosFlagsNulos() {
        FamiliarDTO dto = familiarConContacto();
        dto.setRecibeInfoClub(null);
        dto.setWhatsappActivo(null);

        when(familiarService.guardarFamiliar(dto)).thenReturn(true);

        controller.guardarFamiliar(dto);

        assertThat(dto.getRecibeInfoClub()).isZero();
        assertThat(dto.getWhatsappActivo()).isZero();
    }

    @Test
    void guardarFamiliarDevuelveBadRequestSiElServicioFalla() {
        FamiliarDTO dto = familiarConContacto();
        when(familiarService.guardarFamiliar(dto)).thenReturn(false);

        assertThat(controller.guardarFamiliar(dto).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void guardarFamiliarDevuelve500SiElServicioLanzaExcepcion() {
        FamiliarDTO dto = familiarConContacto();
        when(familiarService.guardarFamiliar(dto)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.guardarFamiliar(dto).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void guardarFamiliarDevuelveOkCuandoTieneExito() {
        FamiliarDTO dto = familiarConContacto();
        when(familiarService.guardarFamiliar(dto)).thenReturn(true);

        assertThat(controller.guardarFamiliar(dto).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void borrarFamiliarDevuelveBadRequestSiTieneRelaciones() {
        org.mockito.Mockito.doThrow(new IllegalStateException("tiene jugadores")).when(familiarService)
                .eliminarFamiliar(1L);

        assertThat(controller.borrarFamiliar(1L).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void borrarFamiliarDevuelveOkCuandoTieneExito() {
        assertThat(controller.borrarFamiliar(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(familiarService).eliminarFamiliar(1L);
    }

    @Test
    void asignarFamiliarJugadorRechazaSiFaltanDatos() {
        FamiliarJugadorDTO relacion = new FamiliarJugadorDTO();

        assertThat(controller.asignarFamiliarJugador(relacion).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(familiarJugadorService, never()).guardarFamiliarJugador(any());
    }

    @Test
    void asignarFamiliarJugadorPonePorDefectoEsPrincipal() {
        FamiliarJugadorDTO relacion = new FamiliarJugadorDTO();
        relacion.setFamiliarId(1L);
        relacion.setJugadorId(2L);

        when(familiarJugadorService.guardarFamiliarJugador(relacion)).thenReturn(true);

        controller.asignarFamiliarJugador(relacion);

        assertThat(relacion.getEsPrincipal()).isZero();
    }

    @Test
    void asignarFamiliarJugadorDevuelveBadRequestSiYaExiste() {
        FamiliarJugadorDTO relacion = new FamiliarJugadorDTO();
        relacion.setFamiliarId(1L);
        relacion.setJugadorId(2L);

        when(familiarJugadorService.guardarFamiliarJugador(relacion)).thenReturn(false);

        assertThat(controller.asignarFamiliarJugador(relacion).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void marcarFamiliarPrincipalDevuelve404SiLaRelacionNoExiste() {
        when(familiarJugadorService.obtenerPorId(1L)).thenReturn(null);

        assertThat(controller.marcarFamiliarPrincipal(1L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void marcarFamiliarPrincipalActualizaLaRelacion() {
        FamiliarJugadorDTO relacion = new FamiliarJugadorDTO();
        when(familiarJugadorService.obtenerPorId(1L)).thenReturn(relacion);
        when(familiarJugadorService.guardarFamiliarJugador(relacion)).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.marcarFamiliarPrincipal(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(relacion.getEsPrincipal()).isEqualTo(1);
    }

    @Test
    void desasignarFamiliarJugadorDelegaEnElServicio() {
        assertThat(controller.desasignarFamiliarJugador(1L).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(familiarJugadorService).eliminar(1L);
    }

    @Test
    void obtenerTodosFamiliaresJsonDelegaEnElServicio() {
        when(familiarService.obtenerTodos()).thenReturn(List.of(new FamiliarDTO()));

        assertThat(controller.obtenerTodosFamiliaresJson().getBody()).hasSize(1);
    }

    @Test
    void obtenerTodosJugadoresJsonDelegaEnElServicio() {
        when(jugadorService.obtenerTodos()).thenReturn(List.of(new com.mikedev.mutxamelcf.model.JugadorDTO()));

        assertThat(controller.obtenerTodosJugadoresJson().getBody()).hasSize(1);
    }

    @Test
    void obtenerJugadoresDeFamiliarDelegaEnElServicio() {
        when(familiarJugadorService.obtenerJugadoresDeFamiliar(1L)).thenReturn(List.of(new FamiliarJugadorDTO()));

        assertThat(controller.obtenerJugadoresDeFamiliar(1L).getBody()).hasSize(1);
    }

    @Test
    void obtenerFamiliaresDeJugadorDelegaEnElServicio() {
        when(familiarJugadorService.obtenerFamiliaresDeJugador(1L)).thenReturn(List.of(new FamiliarJugadorDTO()));

        assertThat(controller.obtenerFamiliaresDeJugador(1L).getBody()).hasSize(1);
    }

    @Test
    void guardarFamiliarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        FamiliarDTO dto = familiarConContacto();
        when(familiarService.guardarFamiliar(dto)).thenThrow(new RuntimeException("fallo bd"));

        assertThat(controller.guardarFamiliar(dto).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void borrarFamiliarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo bd")).when(familiarService).eliminarFamiliar(1L);

        assertThat(controller.borrarFamiliar(1L).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void asignarFamiliarJugadorDevuelve500SiElServicioLanzaExcepcionInesperada() {
        FamiliarJugadorDTO relacion = new FamiliarJugadorDTO();
        relacion.setFamiliarId(1L);
        relacion.setJugadorId(2L);
        when(familiarJugadorService.guardarFamiliarJugador(relacion)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.asignarFamiliarJugador(relacion).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void marcarFamiliarPrincipalDevuelveBadRequestSiElServicioFalla() {
        FamiliarJugadorDTO relacion = new FamiliarJugadorDTO();
        when(familiarJugadorService.obtenerPorId(1L)).thenReturn(relacion);
        when(familiarJugadorService.guardarFamiliarJugador(relacion)).thenReturn(false);

        assertThat(controller.marcarFamiliarPrincipal(1L).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void marcarFamiliarPrincipalDevuelve500SiElServicioLanzaExcepcionInesperada() {
        when(familiarJugadorService.obtenerPorId(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.marcarFamiliarPrincipal(1L).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void desasignarFamiliarJugadorDevuelve500SiElServicioLanzaExcepcionInesperada() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo")).when(familiarJugadorService).eliminar(1L);

        assertThat(controller.desasignarFamiliarJugador(1L).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
