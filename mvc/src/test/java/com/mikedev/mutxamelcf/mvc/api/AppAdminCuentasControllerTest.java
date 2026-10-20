package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.mikedev.mutxamelcf.mvc.communication.ReenvioActivacion;
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
        controller = new AppAdminCuentasController(
                usuarioAppService, new ReenvioActivacion(usuarioAppService, comunicacionesService, limitador));

        // Los usuarios 3 (coordinador) y 4 (entrenador) no son ADMIN_APP: el servicio lo rechaza.
        org.mockito.Mockito.doThrow(new SecurityException("no")).when(usuarioAppService).exigirAdminApp(3);
        org.mockito.Mockito.doThrow(new SecurityException("no")).when(usuarioAppService).exigirAdminApp(4);
        when(usuarioAppService.listarUsuariosPendientes(3)).thenThrow(new SecurityException("no"));
        when(usuarioAppService.listarUsuariosPendientes(4)).thenThrow(new SecurityException("no"));
        when(usuarioAppService.reenviarActivacionPendiente(3, 5)).thenThrow(new SecurityException("no"));
        when(usuarioAppService.reenviarActivacionPendiente(4, 5)).thenThrow(new SecurityException("no"));
    }

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private void invitacion(int id) {
        when(usuarioAppService.reenviarActivacionPendiente(1, id))
                .thenReturn(new InvitacionUsuarioApp(id, "nuevo@x.es", "Pablo", "123456"));
        when(comunicacionesService.enviarInvitacionApp("nuevo@x.es", "Pablo", "123456")).thenReturn(true);
    }

    @Test
    void devuelve401SinAutenticacion() {
        assertThat(controller.listarPendientes(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.reenviarActivacion(5, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devuelve403ParaCoordinadorYEntrenadorYNoGastaElLimite() {
        for (String usuario : List.of("3", "4")) {
            assertThat(controller.listarPendientes(autenticado(usuario)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(controller.reenviarActivacion(5, autenticado(usuario)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        // La autorización es lo primero: ni se llegó a generar un código ni a reservar el límite.
        verify(usuarioAppService, never()).reenviarActivacionPendiente(anyInt(), anyInt());
        verify(comunicacionesService, never()).enviarInvitacionApp(anyString(), anyString(), anyString());
        // Tras los rechazos el limite sigue libre: un admin puede reenviar.
        assertThat(limitador.reservar(5)).isZero();
    }

    @Test
    void listaLasCuentasPendientes() {
        when(usuarioAppService.listarUsuariosPendientes(1)).thenReturn(List.of());

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
        verify(usuarioAppService, times(1)).reenviarActivacionPendiente(1, 5);
        verify(comunicacionesService, times(1)).enviarInvitacionApp(anyString(), anyString(), anyString());

        invitacion(6);
        assertThat(controller.reenviarActivacion(6, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unaCuentaYaActivaODesactivadaEsUn400YNoGastaElLimite() {
        when(usuarioAppService.reenviarActivacionPendiente(1, 5))
                .thenThrow(new IllegalStateException("La cuenta ya está activa"));

        assertThat(controller.reenviarActivacion(5, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(limitador.reservar(5)).isZero();
        verify(comunicacionesService, never()).enviarInvitacionApp(anyString(), anyString(), anyString());
    }

    @Test
    void siFallaElEmailSeQuitaLaMarcaYSePuedeReintentar() {
        invitacion(5);
        when(comunicacionesService.enviarInvitacionApp("nuevo@x.es", "Pablo", "123456")).thenReturn(false);

        assertThat(controller.reenviarActivacion(5, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // La marca se quitó: el reintento no es un 429.
        when(comunicacionesService.enviarInvitacionApp("nuevo@x.es", "Pablo", "123456")).thenReturn(true);

        assertThat(controller.reenviarActivacion(5, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(usuarioAppService, times(2)).reenviarActivacionPendiente(1, 5);
        verify(usuarioAppService, never()).reenviarInvitacion(anyInt());
    }

    @Test
    void elPanelWebComparteElLimiteConLaApp() {
        invitacion(5);
        when(usuarioAppService.reenviarInvitacion(5))
                .thenReturn(new InvitacionUsuarioApp(5, "nuevo@x.es", "Pablo", "123456"));

        ReenvioActivacion reenvio = new ReenvioActivacion(usuarioAppService, comunicacionesService, limitador);

        // El panel reenvía primero...
        assertThat(reenvio.desdePanel(5).email()).isEqualTo("nuevo@x.es");

        // ...y la app, justo después, ya está limitada.
        assertThat(controller.reenviarActivacion(5, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
