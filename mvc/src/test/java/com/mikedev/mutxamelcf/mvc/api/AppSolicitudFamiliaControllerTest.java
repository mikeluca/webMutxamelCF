package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.SolicitudFamiliaRequest;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Mia;
import com.mikedev.mutxamelcf.model.SolicitudFamiliaResponses.Revision;
import com.mikedev.mutxamelcf.mvc.api.AppSolicitudFamiliaController.AprobarRequest;
import com.mikedev.mutxamelcf.mvc.api.AppSolicitudFamiliaController.RechazarRequest;
import com.mikedev.mutxamelcf.mvc.config.PublicFormRateLimiter;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Revisor;

class AppSolicitudFamiliaControllerTest {

    private SolicitudFamiliaService servicio;
    private AppSolicitudFamiliaController controller;
    private Authentication sesion;

    @BeforeEach
    void setUp() {
        servicio = mock(SolicitudFamiliaService.class);
        controller = new AppSolicitudFamiliaController(servicio, new PublicFormRateLimiter());
        sesion = new UsernamePasswordAuthenticationToken("42", null, List.of());
    }

    private static SolicitudFamiliaRequest peticion() {
        return new SolicitudFamiliaRequest(null, null, null, null, "MADRE",
                List.of(new SolicitudFamiliaRequest.Hijo("Luis", "Pérez", null)), true, true, null);
    }

    @Test
    void pedirElAccesoConSesionDevuelve201ConLaSolicitud() {

        Mia mia = new Mia(1L, "PENDIENTE", "MADRE", null, null, null, List.of());
        when(servicio.solicitarConSesion(eq(42), any())).thenReturn(mia);

        ResponseEntity<?> r = controller.solicitar(peticion(), sesion);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).isSameAs(mia);
    }

    @Test
    void pedirElAccesoTraduceLosErroresDelServicio() {

        when(servicio.solicitarConSesion(anyInt(), any())).thenThrow(new IllegalStateException("Ya tienes una solicitud pendiente de revisar"))
                .thenThrow(new IllegalArgumentException("Indica al menos un hijo o hija"));

        assertThat(controller.solicitar(peticion(), sesion).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(controller.solicitar(peticion(), sesion).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void seisEnviosQueFallanPorValidacionNoBloqueanElSeptimoValido() {

        Mia mia = new Mia(1L, "PENDIENTE", "MADRE", null, null, null, List.of());
        when(servicio.solicitarConSesion(anyInt(), any())).thenThrow(new IllegalArgumentException("El nombre es obligatorio"))
                .thenThrow(new IllegalArgumentException("El nombre es obligatorio"))
                .thenThrow(new IllegalArgumentException("El nombre es obligatorio"))
                .thenThrow(new IllegalArgumentException("El nombre es obligatorio"))
                .thenThrow(new IllegalArgumentException("El nombre es obligatorio"))
                .thenThrow(new IllegalArgumentException("El nombre es obligatorio")).thenReturn(mia);

        for (int i = 0; i < 6; i++) {
            assertThat(controller.solicitar(peticion(), sesion).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertThat(controller.solicitar(peticion(), sesion).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void seisEnviosValidosSeguidosBloqueanElSexto() {

        Mia mia = new Mia(1L, "PENDIENTE", "MADRE", null, null, null, List.of());
        when(servicio.solicitarConSesion(anyInt(), any())).thenReturn(mia);

        for (int i = 0; i < 5; i++) {
            assertThat(controller.solicitar(peticion(), sesion).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        assertThat(controller.solicitar(peticion(), sesion).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void sinSesionEsUn401() {

        assertThat(controller.mias(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.solicitar(peticion(), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.mias(new UsernamePasswordAuthenticationToken("no-numero", null, List.of()))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void miasDevuelveLasSolicitudesPropias() {

        when(servicio.misSolicitudes(42)).thenReturn(List.of());

        ResponseEntity<?> r = controller.mias(sesion);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).isEqualTo(List.of());
    }

    @Test
    void laRevisionSeHaceComoRevisorDeLaApp() {

        when(servicio.pendientes(any())).thenReturn(List.of());
        when(servicio.contarPendientes(any())).thenReturn(3);

        assertThat(controller.pendientes(sesion).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.contador(sesion).getBody()).isEqualTo(Map.of("total", 3));

        verify(servicio).pendientes(Revisor.app(42));
        verify(servicio).contarPendientes(Revisor.app(42));
    }

    @Test
    void sinPermisoDeRevisionEsUn403() {

        when(servicio.pendientes(any())).thenThrow(new SecurityException("No puedes revisar solicitudes de familia"));
        when(servicio.buscarJugadores(any(), any())).thenThrow(new SecurityException("no"));

        assertThat(controller.pendientes(sesion).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.jugadores("luis", sesion).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aprobarPideElJugadorYTraduceLosErrores() {

        Revision revision = mock(Revision.class);
        when(servicio.aprobarHijo(Revisor.app(42), 31, 55)).thenReturn(revision);

        assertThat(controller.aprobar(31, null, sesion).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.aprobar(31, new AprobarRequest(null), sesion).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(servicio, never()).aprobarHijo(any(), anyLong(), anyLong());

        ResponseEntity<?> ok = controller.aprobar(31, new AprobarRequest(55L), sesion);

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).isSameAs(revision);

        when(servicio.aprobarHijo(any(), eq(32L), anyLong())).thenThrow(new IllegalStateException("ya revisado"));
        when(servicio.aprobarHijo(any(), eq(33L), anyLong())).thenThrow(new SecurityException("no puedes"));
        when(servicio.aprobarHijo(any(), eq(34L), anyLong())).thenThrow(new IllegalArgumentException("no existe"));

        assertThat(controller.aprobar(32, new AprobarRequest(1L), sesion).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(controller.aprobar(33, new AprobarRequest(1L), sesion).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.aprobar(34, new AprobarRequest(1L), sesion).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rechazarAdmiteElMotivoOpcional() {

        controller.rechazar(31, new RechazarRequest("No es de nuestro club"), sesion);
        controller.rechazar(32, null, sesion);

        verify(servicio).rechazarHijo(Revisor.app(42), 31, "No es de nuestro club");
        verify(servicio).rechazarHijo(Revisor.app(42), 32, null);
    }
}
