package com.mikedev.mutxamelcf.mvc.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.RecuperarPasswordRequest;
import com.mikedev.mutxamelcf.model.RestablecerPasswordRequest;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.mvc.config.LoginRateLimiter;
import com.mikedev.mutxamelcf.mvc.controller.AppAuthController;
import com.mikedev.mutxamelcf.service.UsuarioAppService;
import com.mikedev.mutxamelcf.serviceimpl.LimitadorRecuperacionContrasena;

/**
 * Flujo de /recuperar y /restablecer a través del controlador, con el orquestador real
 * (en el mismo hilo) y los servicios simulados.
 */
class RecuperacionContrasenaTest {

    private static final Instant AHORA = Instant.parse("2026-10-25T10:00:00Z");

    private UsuarioAppService usuarioAppService;
    private ComunicacionesService comunicaciones;
    private ReenvioActivacion reenvio;
    private AppAuthController controller;

    @BeforeEach
    void setUp() {
        usuarioAppService = mock(UsuarioAppService.class);
        comunicaciones = mock(ComunicacionesService.class);
        reenvio = mock(ReenvioActivacion.class);
        RecuperacionContrasena recuperacion = new RecuperacionContrasena(
                usuarioAppService, comunicaciones, reenvio,
                new LimitadorRecuperacionContrasena(Clock.fixed(AHORA, ZoneOffset.UTC)), Runnable::run);

        controller = new AppAuthController(
                usuarioAppService, new LoginRateLimiter(), mock(CuentaActivadaNotifier.class), recuperacion);
    }

    private static MockHttpServletRequest http(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private ResponseEntity<Void> recuperar(String email, String ip) {
        RecuperarPasswordRequest request = new RecuperarPasswordRequest();
        request.setEmail(email);
        return controller.recuperar(request, http(ip));
    }

    private static UsuarioApp cuenta(int id, String email, boolean activo, boolean alguna) {
        UsuarioApp usuario = new UsuarioApp();
        usuario.setId(id);
        usuario.setEmail(email);
        usuario.setActivo(activo);
        if (alguna) {
            usuario.setFechaActivacion(Timestamp.from(AHORA.minus(Duration.ofDays(30))));
        }
        return usuario;
    }

    @Test
    void responde204IgualParaInexistenteDesactivadoPendienteYActivoYSoloElActivoRecibeElCodigo() {
        when(usuarioAppService.obtenerPorEmail("nadie@example.com")).thenReturn(null);
        when(usuarioAppService.obtenerPorEmail("off@example.com"))
                .thenReturn(cuenta(1, "off@example.com", false, true));
        when(usuarioAppService.obtenerPorEmail("nuevo@example.com"))
                .thenReturn(cuenta(2, "nuevo@example.com", false, false));
        when(usuarioAppService.obtenerPorEmail("ana@example.com"))
                .thenReturn(cuenta(3, "ana@example.com", true, true));
        when(usuarioAppService.generarCodigoRecuperacion(3)).thenReturn("123456");

        ResponseEntity<Void> inexistente = recuperar("nadie@example.com", "1.1.1.1");
        ResponseEntity<Void> desactivado = recuperar("off@example.com", "1.1.1.2");
        ResponseEntity<Void> pendiente = recuperar("nuevo@example.com", "1.1.1.3");
        ResponseEntity<Void> activo = recuperar("ana@example.com", "1.1.1.4");

        for (ResponseEntity<Void> respuesta : java.util.List.of(inexistente, desactivado, pendiente, activo)) {
            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(respuesta.getBody()).isNull();
            assertThat(respuesta.getHeaders()).isEqualTo(inexistente.getHeaders());
        }

        verify(comunicaciones).enviarCodigoRecuperacion("ana@example.com", null, "123456", 30);
        verify(comunicaciones, never()).enviarCodigoRecuperacion(
                org.mockito.ArgumentMatchers.eq("off@example.com"), any(), anyString(), anyInt());
        verify(comunicaciones, never()).enviarCodigoRecuperacion(
                org.mockito.ArgumentMatchers.eq("nuevo@example.com"), any(), anyString(), anyInt());
        verify(usuarioAppService, never()).generarCodigoRecuperacion(1);
        verify(usuarioAppService, never()).generarCodigoRecuperacion(2);
    }

    @Test
    void laCuentaPendienteRecibeLaInvitacionYNoUnCodigoDeRecuperacion() {
        when(usuarioAppService.obtenerPorEmail("nuevo@example.com"))
                .thenReturn(cuenta(2, "nuevo@example.com", false, false));

        recuperar("nuevo@example.com", "1.1.1.1");

        verify(reenvio).desdeRecuperacion(2);
        verify(usuarioAppService, never()).generarCodigoRecuperacion(anyInt());
    }

    @Test
    void unaCuentaDesactivadaOEliminadaNoRecibeNada() {
        when(usuarioAppService.obtenerPorEmail("off@example.com"))
                .thenReturn(cuenta(1, "off@example.com", false, true));
        UsuarioApp eliminada = cuenta(4, "borrada@example.com", true, true);
        eliminada.setFechaEliminacion(Timestamp.from(AHORA));
        when(usuarioAppService.obtenerPorEmail("borrada@example.com")).thenReturn(eliminada);

        recuperar("off@example.com", "1.1.1.1");
        recuperar("borrada@example.com", "1.1.1.2");

        verify(reenvio, never()).desdeRecuperacion(anyInt());
        verify(usuarioAppService, never()).generarCodigoRecuperacion(anyInt());
    }

    @Test
    void unEmailSoloPuedePedirUnCodigoCadaDosMinutosPeroSigueRespondiendo204() {
        when(usuarioAppService.obtenerPorEmail("ana@example.com"))
                .thenReturn(cuenta(3, "ana@example.com", true, true));
        when(usuarioAppService.generarCodigoRecuperacion(3)).thenReturn("123456");

        ResponseEntity<Void> primera = recuperar("ana@example.com", "1.1.1.1");
        ResponseEntity<Void> segunda = recuperar("Ana@Example.com", "1.1.1.1");

        assertThat(primera.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(usuarioAppService, org.mockito.Mockito.times(1)).generarCodigoRecuperacion(3);
    }

    @Test
    void unaIpSoloPuedePedirDiezRecuperacionesPorHora() {
        for (int i = 0; i < 12; i++) {
            ResponseEntity<Void> respuesta = recuperar("u" + i + "@example.com", "9.9.9.9");
            assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        // Las 10 primeras llegan a consultar la cuenta; las 2 siguientes se descartan.
        verify(usuarioAppService, org.mockito.Mockito.times(10)).obtenerPorEmail(anyString());
    }

    @Test
    void elEmailDeConfirmacionSaleDespuesDeConfirmarElCambioYElTokenSeDevuelve() {
        UsuarioApp usuario = cuenta(3, "ana@example.com", true, true);
        when(usuarioAppService.restablecerPassword("ana@example.com", "123456", "nueva-clave-1")).thenReturn(usuario);
        when(usuarioAppService.obtenerPorId(3)).thenReturn(usuario);
        when(usuarioAppService.obtenerNombreDePila(3)).thenReturn("Ana");
        LoginAppResponse login = new LoginAppResponse();
        when(usuarioAppService.login("ana@example.com", "nueva-clave-1")).thenReturn(login);

        RestablecerPasswordRequest request = new RestablecerPasswordRequest();
        request.setEmail("ana@example.com");
        request.setCodigo("123456");
        request.setPassword("nueva-clave-1");

        ResponseEntity<?> respuesta = controller.restablecer(request, http("1.1.1.1"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isSameAs(login);

        InOrder orden = inOrder(usuarioAppService, comunicaciones);
        orden.verify(usuarioAppService).restablecerPassword("ana@example.com", "123456", "nueva-clave-1");
        orden.verify(comunicaciones).enviarContrasenaCambiada("ana@example.com", "Ana");
    }

    @Test
    void conUnCodigoIncorrectoDevuelve400SinEmailNiSesion() {
        when(usuarioAppService.restablecerPassword("ana@example.com", "000000", "nueva-clave-1"))
                .thenThrow(new IllegalArgumentException("Código incorrecto o caducado"));

        RestablecerPasswordRequest request = new RestablecerPasswordRequest();
        request.setEmail("ana@example.com");
        request.setCodigo("000000");
        request.setPassword("nueva-clave-1");

        ResponseEntity<?> respuesta = controller.restablecer(request, http("1.1.1.1"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isEqualTo("Código incorrecto o caducado");
        verify(comunicaciones, never()).enviarContrasenaCambiada(anyString(), any());
        verify(usuarioAppService, never()).login(anyString(), anyString());
    }

    @Test
    void enmascararNoDejaElEmailCompleto() {
        assertThat(RecuperacionContrasena.enmascarar("ana@example.com")).isEqualTo("a***@example.com");
        assertThat(RecuperacionContrasena.enmascarar("sinarroba")).isEqualTo("s***");
    }
}
