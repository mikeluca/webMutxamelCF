package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.ConflictoInvitacionException;
import com.mikedev.mutxamelcf.model.EquipoInvitableResponse;
import com.mikedev.mutxamelcf.model.InvitacionResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartidoResponse;
import com.mikedev.mutxamelcf.model.MotivoInvitacionRequest;
import com.mikedev.mutxamelcf.service.InvitadosService;

class AppInvitadosControllerTest {

    private final InvitadosService servicio = mock(InvitadosService.class);
    private final AppInvitadosController controller = new AppInvitadosController(servicio);

    private static Authentication autenticado(String nombre) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(nombre, "x");
        auth.setAuthenticated(true);
        return auth;
    }

    private static MotivoInvitacionRequest motivo(String texto) {
        MotivoInvitacionRequest request = new MotivoInvitacionRequest();
        request.setMotivo(texto);
        return request;
    }

    private static final InvitacionResponse INVITACION = new InvitacionResponse(
            5L, 100L, 10L, 1L, "Ana Prueba", 2L, "Cadete B", "AUTORIZADO", null);

    @Test
    void sinAutenticacionTodoEs401() {
        assertThat(controller.equipos(10L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.jugadores(10L, 2L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.pendientes(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.aprobar(5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.rechazar(5L, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.liberar(5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.desestimar(10L, 1L, motivo("x"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unUsuarioNoNumericoEs401() {
        assertThat(controller.pendientes(autenticado("no-numerico")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void equiposYJugadoresInvitablesDevuelven200() {
        List<EquipoInvitableResponse> equipos = List.of(new EquipoInvitableResponse(2L, "Cadete B", "Cadete", "B"));
        when(servicio.equiposInvitables(7L, 10L)).thenReturn(equipos);

        ResponseEntity<?> respuesta = controller.equipos(10L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(equipos);

        when(servicio.jugadoresInvitables(7L, 10L, 2L)).thenReturn(List.of());

        assertThat(controller.jugadores(10L, 2L, autenticado("7")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aprobarRechazarYLiberarDevuelvenLaInvitacion() {
        when(servicio.aprobar(3L, 5L)).thenReturn(INVITACION);
        when(servicio.rechazar(3L, 5L, "Juega ese día")).thenReturn(INVITACION);
        when(servicio.rechazar(3L, 6L, null)).thenReturn(INVITACION);
        when(servicio.liberar(8L, 5L)).thenReturn(INVITACION);

        assertThat(controller.aprobar(5L, autenticado("3")).getBody()).isEqualTo(INVITACION);
        assertThat(controller.rechazar(5L, motivo("Juega ese día"), autenticado("3")).getBody()).isEqualTo(INVITACION);
        // El motivo del rechazo es opcional: se puede rechazar sin cuerpo.
        assertThat(controller.rechazar(6L, null, autenticado("3")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.liberar(5L, autenticado("8")).getBody()).isEqualTo(INVITACION);
    }

    @Test
    void desestimarDevuelve201ConLaJustificacion() {
        JustificacionFaltaPartidoResponse justificacion = new JustificacionFaltaPartidoResponse(
                10L, 1L, "Ana", "Examen", java.time.LocalDateTime.of(2026, 11, 12, 10, 0));
        when(servicio.desestimar(21L, 10L, 1L, "Examen")).thenReturn(justificacion);

        ResponseEntity<?> respuesta = controller.desestimar(10L, 1L, motivo("Examen"), autenticado("21"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isEqualTo(justificacion);
    }

    @Test
    void mapeaLosErroresDelServicio() {
        doThrow(new SecurityException("solo coordinación")).when(servicio).pendientes(7L);
        assertThat(controller.pendientes(autenticado("7")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        doThrow(new IllegalArgumentException("ya no está pendiente")).when(servicio).aprobar(3L, 5L);
        ResponseEntity<?> mala = controller.aprobar(5L, autenticado("3"));
        assertThat(mala.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mala.getBody()).isEqualTo("ya no está pendiente");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unJugadorYaInvitadoEseDiaEsUn409ConSuCodigoParaLaApp() {
        doThrow(new ConflictoInvitacionException(
                ConflictoInvitacionException.JUGADOR_YA_INVITADO_ESE_DIA, "Ana ya está invitada con Cadete C", 1L, 6L,
                "Cadete C", LocalDate.of(2026, 11, 14))).when(servicio).aprobar(3L, 5L);

        ResponseEntity<?> respuesta = controller.aprobar(5L, autenticado("3"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((Map<String, Object>) respuesta.getBody())
                .containsEntry("codigo", "JUGADOR_YA_INVITADO_ESE_DIA")
                .containsEntry("mensaje", "Ana ya está invitada con Cadete C")
                .containsEntry("jugadorId", 1L)
                .containsEntry("equipoDestinoId", 6L)
                .containsEntry("equipoDestino", "Cadete C")
                .containsEntry("fecha", "2026-11-14");
    }

    @Test
    void elTextoDeUnErrorInesperadoNoSeFiltraAlCliente() {
        doThrow(new IllegalStateException("ORA-00942")).when(servicio).liberar(8L, 5L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.liberar(5L, autenticado("8")))
                .isInstanceOf(IllegalStateException.class);

        verify(servicio).liberar(8L, 5L);
    }
}
