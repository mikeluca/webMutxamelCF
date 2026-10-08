package com.mikedev.mutxamelcf.mvc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ReporteComunicacionRequest;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

class AppReporteComunicacionControllerTest {

    private ComunicacionService comunicacionService;
    private UsuarioAppService usuarioAppService;
    private ComunicacionesService comunicacionesService;
    private AppReporteComunicacionController controller;

    @BeforeEach
    void setUp() {
        comunicacionService = mock(ComunicacionService.class);
        usuarioAppService = mock(UsuarioAppService.class);
        comunicacionesService = mock(ComunicacionesService.class);
        controller = new AppReporteComunicacionController(
                comunicacionService, usuarioAppService, comunicacionesService);
    }

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private static ReporteComunicacionRequest request(String motivo) {
        ReporteComunicacionRequest request = new ReporteComunicacionRequest();
        request.setMotivo(motivo);
        return request;
    }

    private static UsuarioApp usuario(int id, String email) {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(id);
        usuario.setEmail(email);
        usuario.setActivo(true);
        return usuario;
    }

    private static Comunicacion mensaje(long autorId) {
        Comunicacion c = new Comunicacion();
        c.setId(40L);
        c.setTipo("PRIVADA");
        c.setContenido("texto ofensivo");
        c.setUsuarioAutorId(autorId);
        c.setFechaCreacion(LocalDateTime.of(2026, 10, 1, 12, 0));
        return c;
    }

    private static RolApp rol(String codigo) {
        RolApp rol = new RolApp();
        rol.setCodigo(codigo);
        return rol;
    }

    @Test
    void devuelve401SinAutenticacion() {
        assertThat(controller.reportar(40L, request("x"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void enviaElEmailConLosDatosRelevantes() {
        when(comunicacionService.obtenerPorId(40L, 5L)).thenReturn(mensaje(8L));
        when(usuarioAppService.obtenerPorId(5)).thenReturn(usuario(5, "reporta@x.es"));
        when(usuarioAppService.obtenerPorId(8)).thenReturn(usuario(8, "autor@x.es"));
        when(usuarioAppService.obtenerRoles(8)).thenReturn(List.of(rol("JUGADOR")));
        when(usuarioAppService.obtenerRoles(5)).thenReturn(List.of(rol("FAMILIAR")));
        when(comunicacionesService.enviarReporteContenido(anyString(), anyString(), anyString()))
                .thenReturn(true);

        var response = controller.reportar(40L, request("Insultos"), autenticado("5"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(comunicacionesService).enviarReporteContenido(
                contains("mensaje 40"), cuerpo.capture(), eq("reporta@x.es"));
        assertThat(cuerpo.getValue())
                .contains("Insultos")
                .contains("reporta@x.es")
                .contains("autor@x.es")
                .contains("texto ofensivo")
                .contains("PRIVADA");
    }

    @Test
    void noPermiteReportarElPropioMensaje() {
        when(comunicacionService.obtenerPorId(40L, 5L)).thenReturn(mensaje(5L));

        var response = controller.reportar(40L, request("x"), autenticado("5"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(comunicacionesService, never()).enviarReporteContenido(anyString(), anyString(), anyString());
    }

    @Test
    void devuelve403SiNoPuedeVerElMensaje() {
        when(comunicacionService.obtenerPorId(40L, 5L)).thenThrow(new SecurityException("no"));

        assertThat(controller.reportar(40L, request("x"), autenticado("5")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void devuelve404SiNoExiste() {
        when(comunicacionService.obtenerPorId(40L, 5L)).thenThrow(new IllegalArgumentException("no existe"));

        assertThat(controller.reportar(40L, request("x"), autenticado("5")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void devuelve503SiNoSePuedeEnviarElEmail() {
        when(comunicacionService.obtenerPorId(40L, 5L)).thenReturn(mensaje(8L));
        when(usuarioAppService.obtenerPorId(5)).thenReturn(usuario(5, "reporta@x.es"));
        when(usuarioAppService.obtenerPorId(8)).thenReturn(usuario(8, "autor@x.es"));
        when(comunicacionesService.enviarReporteContenido(anyString(), anyString(), anyString()))
                .thenReturn(false);

        assertThat(controller.reportar(40L, request("x"), autenticado("5")).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
