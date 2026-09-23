package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.AnadirVinculosRequest;
import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.model.InvitarUsuarioAppRequest;
import com.mikedev.mutxamelcf.model.PersonasVinculablesResponse;
import com.mikedev.mutxamelcf.model.UsuarioAppAdminResponse;
import com.mikedev.mutxamelcf.model.VinculoSolicitado;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUsuariosAppControllerTest {

    private UsuarioAppService usuarioAppService;
    private ComunicacionesService comunicacionesService;
    private AdminUsuariosAppController controller;

    @BeforeEach
    void setUp() {
        usuarioAppService = mock(UsuarioAppService.class);
        comunicacionesService = mock(ComunicacionesService.class);
        controller = new AdminUsuariosAppController(usuarioAppService, comunicacionesService);
    }

    private static InvitarUsuarioAppRequest request() {
        InvitarUsuarioAppRequest request = new InvitarUsuarioAppRequest();
        request.setVinculos(List.of(new VinculoSolicitado("JUGADOR", 1L)));
        return request;
    }

    private static InvitacionUsuarioApp invitacion() {
        return new InvitacionUsuarioApp(1, "familia@example.com", "Ana", "token-secreto");
    }

    @Test
    void listarRellenaElModelo() {
        when(usuarioAppService.listarUsuariosAdmin()).thenReturn(List.of(new UsuarioAppAdminResponse()));
        when(usuarioAppService.obtenerPersonasVinculables()).thenReturn(new PersonasVinculablesResponse());

        Model model = new ExtendedModelMap();
        String vista = controller.listar(model);

        assertEquals("admin/usuarios-app", vista);
        assertEquals(1, ((List<?>) model.getAttribute("usuarios")).size());
    }

    @Test
    void invitarDevuelveCreatedCuandoElEmailSeEnviaCorrectamente() {
        when(usuarioAppService.invitarUsuario(any())).thenReturn(invitacion());
        when(comunicacionesService.enviarInvitacionApp(anyString(), anyString(), anyString())).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.invitar(request());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Invitación enviada correctamente a familia@example.com.", response.getBody().get("mensaje"));
    }

    @Test
    void invitarAvisaCuandoLaCuentaSeCreaPeroNoSeEnviaElEmail() {
        when(usuarioAppService.invitarUsuario(any())).thenReturn(invitacion());
        when(comunicacionesService.enviarInvitacionApp(anyString(), anyString(), anyString())).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.invitar(request());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(
                "El usuario se ha creado, pero no se ha podido enviar el email de invitación. "
                        + "Usa 'Reenviar invitación' cuando quieras volver a intentarlo.",
                response.getBody().get("mensaje"));
    }

    @Test
    void invitarDevuelveBadRequestSiElServicioLanzaIllegalArgument() {
        when(usuarioAppService.invitarUsuario(any())).thenThrow(new IllegalArgumentException("email duplicado"));

        ResponseEntity<Map<String, String>> response = controller.invitar(request());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("email duplicado", response.getBody().get("error"));
    }

    @Test
    void invitarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        when(usuarioAppService.invitarUsuario(any())).thenThrow(new RuntimeException("fallo bd"));

        ResponseEntity<Map<String, String>> response = controller.invitar(request());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void anadirVinculosDelegaEnElServicio() {
        AnadirVinculosRequest request = new AnadirVinculosRequest();
        request.setVinculos(List.of(new VinculoSolicitado("ENTRENADOR", null)));

        ResponseEntity<Map<String, String>> response = controller.anadirVinculos(1, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(usuarioAppService).agregarVinculosAUsuarioExistente(1, request);
    }

    @Test
    void anadirVinculosDevuelveBadRequestSiElServicioLanzaIllegalState() {
        AnadirVinculosRequest request = new AnadirVinculosRequest();
        org.mockito.Mockito.doThrow(new IllegalStateException("vinculo duplicado"))
                .when(usuarioAppService).agregarVinculosAUsuarioExistente(anyInt(), any());

        ResponseEntity<Map<String, String>> response = controller.anadirVinculos(1, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("vinculo duplicado", response.getBody().get("error"));
    }

    @Test
    void anadirVinculosDevuelve500SiElServicioLanzaExcepcionInesperada() {
        AnadirVinculosRequest request = new AnadirVinculosRequest();
        org.mockito.Mockito.doThrow(new RuntimeException("fallo"))
                .when(usuarioAppService).agregarVinculosAUsuarioExistente(anyInt(), any());

        ResponseEntity<Map<String, String>> response = controller.anadirVinculos(1, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void quitarVinculoDelegaEnElServicio() {
        VinculoSolicitado vinculo = new VinculoSolicitado("JUGADOR", 1L);

        ResponseEntity<Map<String, String>> response = controller.quitarVinculo(1, vinculo);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(usuarioAppService).quitarVinculo(1, vinculo);
    }

    @Test
    void quitarVinculoDevuelveBadRequestSiElServicioLanzaIllegalArgument() {
        VinculoSolicitado vinculo = new VinculoSolicitado("JUGADOR", 1L);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("no encontrado"))
                .when(usuarioAppService).quitarVinculo(anyInt(), any());

        ResponseEntity<Map<String, String>> response = controller.quitarVinculo(1, vinculo);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void quitarVinculoDevuelve500SiElServicioLanzaExcepcionInesperada() {
        VinculoSolicitado vinculo = new VinculoSolicitado("JUGADOR", 1L);
        org.mockito.Mockito.doThrow(new RuntimeException("fallo"))
                .when(usuarioAppService).quitarVinculo(anyInt(), any());

        ResponseEntity<Map<String, String>> response = controller.quitarVinculo(1, vinculo);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void reenviarDevuelveOkCuandoElEmailSeEnviaCorrectamente() {
        when(usuarioAppService.reenviarInvitacion(1)).thenReturn(invitacion());
        when(comunicacionesService.enviarInvitacionApp(anyString(), anyString(), anyString())).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.reenviar(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void reenviarDevuelve500SiNoSePuedeEnviarElEmail() {
        when(usuarioAppService.reenviarInvitacion(1)).thenReturn(invitacion());
        when(comunicacionesService.enviarInvitacionApp(anyString(), anyString(), anyString())).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.reenviar(1);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void reenviarDevuelveBadRequestSiElServicioLanzaIllegalState() {
        when(usuarioAppService.reenviarInvitacion(1)).thenThrow(new IllegalStateException("cuenta ya activa"));

        ResponseEntity<Map<String, String>> response = controller.reenviar(1);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void reenviarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        when(usuarioAppService.reenviarInvitacion(1)).thenThrow(new RuntimeException("fallo"));

        ResponseEntity<Map<String, String>> response = controller.reenviar(1);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void activarDelegaEnElServicio() {
        ResponseEntity<Map<String, String>> response = controller.activar(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(usuarioAppService).activarUsuarioAdmin(1);
    }

    @Test
    void activarDevuelveBadRequestSiElServicioLanzaIllegalArgument() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("no existe"))
                .when(usuarioAppService).activarUsuarioAdmin(1);

        ResponseEntity<Map<String, String>> response = controller.activar(1);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void activarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo"))
                .when(usuarioAppService).activarUsuarioAdmin(1);

        ResponseEntity<Map<String, String>> response = controller.activar(1);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void desactivarDelegaEnElServicio() {
        ResponseEntity<Map<String, String>> response = controller.desactivar(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(usuarioAppService).desactivarUsuarioAdmin(1);
    }

    @Test
    void desactivarDevuelveBadRequestSiElServicioLanzaIllegalArgument() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("no existe"))
                .when(usuarioAppService).desactivarUsuarioAdmin(1);

        ResponseEntity<Map<String, String>> response = controller.desactivar(1);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void desactivarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo"))
                .when(usuarioAppService).desactivarUsuarioAdmin(1);

        ResponseEntity<Map<String, String>> response = controller.desactivar(1);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void eliminarDelegaEnElServicio() {
        ResponseEntity<Map<String, String>> response = controller.eliminar(1);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(usuarioAppService).eliminarInvitacion(1);
    }

    @Test
    void eliminarDevuelveBadRequestSiElServicioLanzaIllegalState() {
        org.mockito.Mockito.doThrow(new IllegalStateException("cuenta ya activada"))
                .when(usuarioAppService).eliminarInvitacion(1);

        ResponseEntity<Map<String, String>> response = controller.eliminar(1);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(comunicacionesService, never()).enviarInvitacionApp(any(), any(), any());
    }

    @Test
    void eliminarDevuelve500SiElServicioLanzaExcepcionInesperada() {
        org.mockito.Mockito.doThrow(new RuntimeException("fallo"))
                .when(usuarioAppService).eliminarInvitacion(1);

        ResponseEntity<Map<String, String>> response = controller.eliminar(1);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
