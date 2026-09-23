package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.service.EquipoService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

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
}
