package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mikedev.mutxamelcf.model.LoginAppResponse;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaRequest;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.mvc.api.PublicSolicitudFamiliaController.ReenviarRequest;
import com.mikedev.mutxamelcf.mvc.api.PublicSolicitudFamiliaController.VerificarRequest;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.mvc.config.PublicFormRateLimiter;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Registro;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Resultado;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

class PublicSolicitudFamiliaControllerTest {

    private SolicitudFamiliaService solicitudes;
    private UsuarioAppService usuarioAppService;
    private ComunicacionesService comunicaciones;
    private PublicSolicitudFamiliaController controller;

    @BeforeEach
    void setUp() {
        solicitudes = mock(SolicitudFamiliaService.class);
        usuarioAppService = mock(UsuarioAppService.class);
        comunicaciones = mock(ComunicacionesService.class);
        controller = new PublicSolicitudFamiliaController(solicitudes, usuarioAppService, comunicaciones,
                new PublicFormRateLimiter(), true);
    }

    private static MockHttpServletRequest http(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private static SolicitudFamiliaRequest peticion(String email) {
        return new SolicitudFamiliaRequest("Ana", "Pérez", email, null, "MADRE",
                List.of(new SolicitudFamiliaRequest.Hijo("Luis", "Pérez", null)), true, true, "es");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> cuerpo(ResponseEntity<?> r) {
        return (Map<String, String>) r.getBody();
    }

    // ---- solicitar -----------------------------------------------------------------------------------------------------

    @Test
    void unaSolicitudNuevaMandaElCodigoPorEmailEnElIdiomaDeLaPeticion() {

        when(solicitudes.registrarPublica(any())).thenReturn(
                new Registro(Resultado.CODIGO_ENVIADO, "ana@example.com", "Ana", "123456", "ca"));
        when(comunicaciones.enviarCodigoSolicitudFamilia(any(), any(), any(), any())).thenReturn(true);

        ResponseEntity<?> r = controller.solicitar(peticion("ana@example.com"), http("1.1.1.1"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cuerpo(r)).containsEntry("estado", "CODIGO_ENVIADO");
        verify(comunicaciones).enviarCodigoSolicitudFamilia("ana@example.com", "Ana", "123456", "ca");
        // El código nunca viaja en la respuesta.
        assertThat(cuerpo(r).toString()).doesNotContain("123456");
    }

    @Test
    void siElEmailNoSaleSeAvisaConUn503() {

        when(solicitudes.registrarPublica(any())).thenReturn(
                new Registro(Resultado.CODIGO_ENVIADO, "ana@example.com", "Ana", "123456", "es"));
        when(comunicaciones.enviarCodigoSolicitudFamilia(any(), any(), any(), any())).thenReturn(false);

        ResponseEntity<?> r = controller.solicitar(peticion("ana@example.com"), http("1.1.1.1"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(cuerpo(r)).containsEntry("estado", "ENVIO_FALLIDO");
    }

    @Test
    void unEmailConCuentaDiceQueInicieSesionSinMandarNingunEmail() {

        when(solicitudes.registrarPublica(any()))
                .thenReturn(new Registro(Resultado.CUENTA_EXISTENTE, null, null, null, null));

        ResponseEntity<?> r = controller.solicitar(peticion("ana@example.com"), http("1.1.1.1"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cuerpo(r)).containsEntry("estado", "CUENTA_EXISTENTE");
        assertThat(cuerpo(r).get("mensaje")).containsIgnoringCase("inicia sesión").containsIgnoringCase("dentro");
        verify(comunicaciones, never()).enviarCodigoSolicitudFamilia(any(), any(), any(), any());
    }

    @Test
    void unEmailConInvitacionPendienteDiceQueUseActivarCuenta() {

        when(solicitudes.registrarPublica(any()))
                .thenReturn(new Registro(Resultado.INVITACION_PENDIENTE, null, null, null, null));

        ResponseEntity<?> r = controller.solicitar(peticion("ana@example.com"), http("1.1.1.1"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cuerpo(r)).containsEntry("estado", "INVITACION_PENDIENTE");
        assertThat(cuerpo(r).get("mensaje")).contains("Activar cuenta");
        verify(comunicaciones, never()).enviarCodigoSolicitudFamilia(any(), any(), any(), any());
    }

    @Test
    void unaPeticionNoValidaDevuelve400ConElMensaje() {

        when(solicitudes.registrarPublica(any())).thenThrow(new IllegalArgumentException("Indica al menos un hijo o hija"));

        ResponseEntity<?> r = controller.solicitar(peticion("ana@example.com"), http("1.1.1.1"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cuerpo(r)).containsEntry("mensaje", "Indica al menos un hijo o hija");
    }

    @Test
    void laSolicitudSeLimitaPorIpYPorEmail() {

        when(solicitudes.registrarPublica(any()))
                .thenReturn(new Registro(Resultado.CUENTA_EXISTENTE, null, null, null, null));

        // Por IP: 5 peticiones con emails distintos desde la misma IP y la sexta se rechaza.
        for (int i = 0; i < 5; i++) {
            assertThat(controller.solicitar(peticion("a" + i + "@example.com"), http("2.2.2.2")).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<?> porIp = controller.solicitar(peticion("a9@example.com"), http("2.2.2.2"));

        assertThat(porIp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(cuerpo(porIp)).containsEntry("codigo", "DEMASIADOS_INTENTOS");

        // Por email: el mismo email desde IPs distintas.
        for (int i = 0; i < 5; i++) {
            assertThat(controller.solicitar(peticion("mismo@example.com"), http("3.3.3." + i)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        assertThat(controller.solicitar(peticion("MISMO@example.com"), http("3.3.3.9")).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Una IP y un email distintos siguen pudiendo.
        assertThat(controller.solicitar(peticion("otro@example.com"), http("4.4.4.4")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // ---- verificar -----------------------------------------------------------------------------------------------------

    @Test
    void verificarConfirmaElEmailYDevuelveUnLoginSinRoles() {

        UsuarioApp cuenta = new UsuarioApp();
        cuenta.setId(100);
        cuenta.setEmail("ana@example.com");
        LoginAppResponse login = new LoginAppResponse();
        login.setUsuarioId(100);
        when(solicitudes.verificarEmail("ana@example.com", "123456", "Secreta123")).thenReturn(cuenta);
        when(usuarioAppService.login("ana@example.com", "Secreta123")).thenReturn(login);

        ResponseEntity<?> r = controller.verificar(
                new VerificarRequest("ana@example.com", "123456", "Secreta123", "ca"), http("1.1.1.1"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isSameAs(login);
        verify(usuarioAppService).actualizarIdioma(100, "ca");
    }

    @Test
    void unCodigoMaloDevuelve400ConElMismoMensajeQueLaActivacion() {

        when(solicitudes.verificarEmail(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("El código no es válido o ha caducado."));

        ResponseEntity<?> r = controller.verificar(new VerificarRequest("ana@example.com", "0", "x", null),
                http("1.1.1.1"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cuerpo(r)).containsEntry("mensaje", "El código no es válido o ha caducado.");
        verify(usuarioAppService, never()).login(anyString(), anyString());
    }

    @Test
    void verificarUnaCuentaYaActivaEsUn409() {

        when(solicitudes.verificarEmail(any(), any(), any())).thenThrow(new IllegalStateException("La cuenta ya está activa"));

        assertThat(controller.verificar(new VerificarRequest("ana@example.com", "1", "x", null), http("1.1.1.1"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void verificarSeLimitaPorIpYPorEmail() {

        when(solicitudes.verificarEmail(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("El código no es válido o ha caducado."));

        for (int i = 0; i < 5; i++) {
            assertThat(controller.verificar(new VerificarRequest("ana@example.com", "0", "x", null), http("5.5.5." + i))
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        ResponseEntity<?> r = controller.verificar(new VerificarRequest("ana@example.com", "0", "x", null),
                http("5.5.5.9"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // El sexto intento ni siquiera llega al servicio.
        verify(solicitudes, org.mockito.Mockito.times(5)).verificarEmail(any(), any(), any());
    }

    @Test
    void verificarSinCuerpoEsUn400() {
        assertThat(controller.verificar(null, http("1.1.1.1")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- reenviar el código --------------------------------------------------------------------------------------------

    @Test
    void reenviarMandaElCodigoSoloSiHaySolicitudYResponde204EnTodosLosCasos() {

        when(solicitudes.reenviarCodigo("ana@example.com")).thenReturn(
                new Registro(Resultado.CODIGO_ENVIADO, "ana@example.com", "Ana", "654321", "es"));
        when(solicitudes.reenviarCodigo("nadie@example.com")).thenReturn(null);

        ResponseEntity<?> con = controller.reenviar(new ReenviarRequest("ana@example.com"), http("1.1.1.1"));
        ResponseEntity<?> sin = controller.reenviar(new ReenviarRequest("nadie@example.com"), http("1.1.1.2"));

        assertThat(con.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(sin.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(con.getBody()).isNull();
        assertThat(sin.getBody()).isNull();
        verify(comunicaciones).enviarCodigoSolicitudFamilia("ana@example.com", "Ana", "654321", "es");
        verify(comunicaciones, org.mockito.Mockito.times(1)).enviarCodigoSolicitudFamilia(any(), any(), any(), any());
    }

    @Test
    void reenviarNoRevelaNadaNiSiFallaElEnvio() {

        when(solicitudes.reenviarCodigo(any())).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.reenviar(new ReenviarRequest("ana@example.com"), http("1.1.1.1")).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(controller.reenviar(null, http("1.1.1.1")).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void reenviarSeLimitaPorIpYPorEmail() {

        when(solicitudes.reenviarCodigo(any())).thenReturn(null);

        for (int i = 0; i < 5; i++) {
            assertThat(controller.reenviar(new ReenviarRequest("ana@example.com"), http("6.6.6." + i))
                    .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        assertThat(controller.reenviar(new ReenviarRequest("ana@example.com"), http("6.6.6.9")).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(solicitudes, org.mockito.Mockito.times(5)).reenviarCodigo(eq("ana@example.com"));
    }

    @Test
    void cadaOperacionTieneSuPropioContador() {

        when(solicitudes.registrarPublica(any()))
                .thenReturn(new Registro(Resultado.CUENTA_EXISTENTE, null, null, null, null));
        when(solicitudes.reenviarCodigo(any())).thenReturn(null);

        for (int i = 0; i < 5; i++) {
            controller.solicitar(peticion("ana@example.com"), http("7.7.7.7"));
        }

        // Agotadas las solicitudes de esa IP y ese email, el reenvío sigue disponible.
        assertThat(controller.solicitar(peticion("ana@example.com"), http("7.7.7.7")).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(controller.reenviar(new ReenviarRequest("ana@example.com"), http("7.7.7.7")).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
}
