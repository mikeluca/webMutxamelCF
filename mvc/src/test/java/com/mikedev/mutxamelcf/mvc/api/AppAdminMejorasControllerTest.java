package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.EstadoAppResponse;
import com.mikedev.mutxamelcf.model.InvitacionAppRequest;
import com.mikedev.mutxamelcf.model.ReporteModeracionResponse;
import com.mikedev.mutxamelcf.model.VinculosDisponiblesResponse;
import com.mikedev.mutxamelcf.mvc.communication.CuentaActivadaNotifier;
import com.mikedev.mutxamelcf.mvc.communication.InvitacionUsuario;
import com.mikedev.mutxamelcf.service.EmailYaRegistradoException;
import com.mikedev.mutxamelcf.service.EstadoAppService;
import com.mikedev.mutxamelcf.service.ModeracionService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/** Invitar desde la app, estado de la app y reactivar al autor de un reporte. */
class AppAdminMejorasControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    // ---- invitar -----------------------------------------------------------------------------

    private final UsuarioAppService usuarios = mock(UsuarioAppService.class);
    private final InvitacionUsuario invitacion = mock(InvitacionUsuario.class);
    private final AppAdminInvitacionesController invitaciones =
            new AppAdminInvitacionesController(usuarios, invitacion);

    @Test
    void invitarYVinculosSinAutenticacionSon401() {
        assertThat(invitaciones.vinculosDisponibles(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invitaciones.invitar(new InvitacionAppRequest(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void vinculosDisponiblesDevuelveLasListasYSinSerAdminEs403() {
        VinculosDisponiblesResponse lista = new VinculosDisponiblesResponse(List.of(), List.of(), List.of(), List.of());
        when(usuarios.obtenerVinculosDisponibles(7)).thenReturn(lista);
        when(usuarios.obtenerVinculosDisponibles(8)).thenThrow(new SecurityException("solo admin"));

        assertThat(invitaciones.vinculosDisponibles(autenticado("7")).getBody()).isSameAs(lista);
        assertThat(invitaciones.vinculosDisponibles(autenticado("8")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void invitarDevuelve201ConElEmail() {
        InvitacionAppRequest request = new InvitacionAppRequest();
        when(invitacion.desdeApp(7, request)).thenReturn(new InvitacionUsuario.Resultado("a@b.es", true));

        ResponseEntity<?> respuesta = invitaciones.invitar(request, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((Map<String, Object>) respuesta.getBody())
                .containsEntry("email", "a@b.es")
                .containsEntry("emailEnviado", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void invitarConElEmailSinEnviarSigueSiendo201PeroLoDice() {
        InvitacionAppRequest request = new InvitacionAppRequest();
        when(invitacion.desdeApp(7, request)).thenReturn(new InvitacionUsuario.Resultado("a@b.es", false));

        ResponseEntity<?> respuesta = invitaciones.invitar(request, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((Map<String, Object>) respuesta.getBody()).containsEntry("emailEnviado", false);
    }

    @Test
    void invitarMapeaLosErrores() {
        InvitacionAppRequest request = new InvitacionAppRequest();

        when(invitacion.desdeApp(1, request)).thenThrow(new SecurityException("no admin"));
        assertThat(invitaciones.invitar(request, autenticado("1")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        when(invitacion.desdeApp(2, request)).thenThrow(new IllegalArgumentException("El rol ADMIN_APP solo..."));
        assertThat(invitaciones.invitar(request, autenticado("2")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        when(invitacion.desdeApp(3, request)).thenThrow(new EmailYaRegistradoException());
        ResponseEntity<?> duplicado = invitaciones.invitar(request, autenticado("3"));
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(duplicado.getBody())).contains("Ya existe una cuenta con ese email");

        when(invitacion.desdeApp(4, request)).thenThrow(new RuntimeException("bd"));
        ResponseEntity<?> error = invitaciones.invitar(request, autenticado("4"));
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(error.getBody())).doesNotContain("bd");
    }

    // ---- estado ------------------------------------------------------------------------------

    private final EstadoAppService estadoService = mock(EstadoAppService.class);
    private final AppAdminEstadoController estado = new AppAdminEstadoController(estadoService);

    @Test
    void estadoYUsuariosSinAutenticacionSon401() {
        assertThat(estado.estado(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(estado.usuarios("INACTIVOS", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void estadoDevuelveElResumenYMapeaPermisosYFiltros() {
        EstadoAppResponse resumen = new EstadoAppResponse(
                new EstadoAppResponse.Cuentas(1, 2, 3), 0, 0, 0, 30, 0, List.of(),
                new EstadoAppResponse.VersionMinima("1.0.2", "1.0.2"));
        when(estadoService.obtenerEstado(7)).thenReturn(resumen);
        when(estadoService.obtenerEstado(8)).thenThrow(new SecurityException("solo admin"));

        assertThat(estado.estado(autenticado("7")).getBody()).isSameAs(resumen);
        assertThat(estado.estado(autenticado("8")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        when(estadoService.listarUsuarios(7, "INACTIVOS")).thenReturn(List.of());
        when(estadoService.listarUsuarios(7, "OTRO")).thenThrow(new IllegalArgumentException("filtro"));

        assertThat(estado.usuarios("INACTIVOS", autenticado("7")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(estado.usuarios("OTRO", autenticado("7")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- reactivar autor ---------------------------------------------------------------------

    private final ModeracionService moderacion = mock(ModeracionService.class);
    private final CuentaActivadaNotifier notificador = mock(CuentaActivadaNotifier.class);
    private final AppModeracionController controlador = new AppModeracionController(moderacion, notificador);

    @Test
    void reactivarSinAutenticacionEs401() {
        assertThat(controlador.reactivarAutor(5L, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reactivarPasaLaNotaYAvisaALaCuentaReactivada() {
        ReporteModeracionResponse.Accion accion = new ReporteModeracionResponse.Accion();
        accion.setNota("Se ha disculpado");
        when(moderacion.reactivarAutor(7L, 5L, "Se ha disculpado")).thenReturn(20L);

        ResponseEntity<?> respuesta = controlador.reactivarAutor(5L, accion, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(notificador).notificar(20);
    }

    @Test
    void reactivarSinCuerpoEsValido() {
        when(moderacion.reactivarAutor(7L, 5L, null)).thenReturn(20L);

        assertThat(controlador.reactivarAutor(5L, null, autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void reactivarMapeaLosErroresYNoAvisaSiFalla() {
        when(moderacion.reactivarAutor(1L, 5L, null)).thenThrow(new SecurityException("solo admin"));
        assertThat(controlador.reactivarAutor(5L, null, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        when(moderacion.reactivarAutor(2L, 5L, null)).thenThrow(new IllegalStateException("otro estado"));
        assertThat(controlador.reactivarAutor(5L, null, autenticado("2")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        when(moderacion.reactivarAutor(3L, 5L, null)).thenThrow(new NoSuchElementException("no existe"));
        assertThat(controlador.reactivarAutor(5L, null, autenticado("3")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(moderacion.reactivarAutor(4L, 5L, null)).thenThrow(new IllegalArgumentException("nota larga"));
        assertThat(controlador.reactivarAutor(5L, null, autenticado("4")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(notificador, never()).notificar(org.mockito.ArgumentMatchers.anyInt());
    }
}
