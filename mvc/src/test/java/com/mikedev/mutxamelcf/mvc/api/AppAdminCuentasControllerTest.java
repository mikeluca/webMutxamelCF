package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;
import com.mikedev.mutxamelcf.serviceimpl.LimitadorReenvioActivacion;

class AppAdminCuentasControllerTest {

    private UsuarioAppService usuarioAppService;
    private ComunicacionesService comunicacionesService;
    private LimitadorReenvioActivacion limitador;
    private AppAdminCuentasController controller;

    @BeforeEach
    void setUp() {
        usuarioAppService = mock(UsuarioAppService.class);
        comunicacionesService = mock(ComunicacionesService.class);
        limitador = new LimitadorReenvioActivacion(
                Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC));
        controller = new AppAdminCuentasController(usuarioAppService, comunicacionesService, limitador);

        when(usuarioAppService.tieneRol(1, "ADMIN_APP")).thenReturn(true);
    }

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private void invitacion(int id) {
        when(usuarioAppService.reenviarActivacionPendiente(id))
                .thenReturn(new InvitacionUsuarioApp(id, "nuevo@x.es", "Pablo", "123456"));
        when(comunicacionesService.enviarInvitacionApp("nuevo@x.es", "Pablo", "123456")).thenReturn(true);
    }

    @Test
    void devuelve401SinAutenticacion() {
        assertThat(controller.listarPendientes(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.reenviarActivacion(5, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devuelve403ParaCoordinadorYEntrenador() {
        // tieneRol ADMIN_APP = false para los usuarios 3 (coordinador) y 4 (entrenador).
        for (String usuario : List.of("3", "4")) {
            assertThat(controller.listarPendientes(autenticado(usuario)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(controller.reenviarActivacion(5, autenticado(usuario)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        verify(usuarioAppService, never()).reenviarActivacionPendiente(anyInt());
    }

    @Test
    void listaLasCuentasPendientes() {
        when(usuarioAppService.listarUsuariosPendientes()).thenReturn(List.of());

        ResponseEntity<?> respuesta = controller.listarPendientes(autenticado("1"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reenviarEnviaElEmailYDevuelveElMensaje() {
        invitacion(5);

        ResponseEntity<?> respuesta = controller.reenviarActivacion(5, autenticado("1"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map<String, String>) respuesta.getBody())
                .containsEntry("mensaje", "Invitación reenviada a nuevo@x.es.")
                .containsEntry("email", "nuevo@x.es");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unSegundoReenvioAntesDeDiezMinutosEsUn429ConMensajeClaro() {
        invitacion(5);

        controller.reenviarActivacion(5, autenticado("1"));

        ResponseEntity<?> segundo = controller.reenviarActivacion(5, autenticado("1"));

        assertThat(segundo.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(((Map<String, String>) segundo.getBody()).get("error")).contains("10 minutos");
        // El segundo intento no llegó a tocar la cuenta ni el email.
        verify(usuarioAppService, org.mockito.Mockito.times(1)).reenviarActivacionPendiente(5);
        verify(comunicacionesService, org.mockito.Mockito.times(1)).enviarInvitacionApp(anyString(), anyString(), anyString());

        // Otro usuario no está limitado.
        invitacion(6);
        assertThat(controller.reenviarActivacion(6, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unaCuentaYaActivaODesactivadaEsUn400YNoGastaElLimite() {
        when(usuarioAppService.reenviarActivacionPendiente(5))
                .thenThrow(new IllegalStateException("La cuenta ya está activa"));

        assertThat(controller.reenviarActivacion(5, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(limitador.esperaRestante(5)).isZero();
        verify(comunicacionesService, never()).enviarInvitacionApp(anyString(), anyString(), anyString());
    }

    @Test
    void siFallaElEmailNoSeAnotaElReenvioYSePuedeReintentar() {
        invitacion(5);
        when(comunicacionesService.enviarInvitacionApp("nuevo@x.es", "Pablo", "123456")).thenReturn(false);

        assertThat(controller.reenviarActivacion(5, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(limitador.esperaRestante(5)).isZero();
    }
}
