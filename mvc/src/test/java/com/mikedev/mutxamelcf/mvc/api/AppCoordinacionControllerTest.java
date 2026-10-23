package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.FinDeSemanaResponse;
import com.mikedev.mutxamelcf.service.CoordinacionService;

class AppCoordinacionControllerTest {

    private final CoordinacionService servicio = mock(CoordinacionService.class);
    private final com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService recordatorios =
            mock(com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService.class);
    private final AppCoordinacionController controller = new AppCoordinacionController(servicio, recordatorios);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void devuelve401SinAutenticacion() {
        assertThat(controller.obtenerFinDeSemana(null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.obtenerSeguimiento(null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.obtenerDetalle(1L, null, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void devuelve200ConElPanelYPasaLaFecha() {
        LocalDate viernes = LocalDate.of(2026, 10, 23);
        FinDeSemanaResponse panel = new FinDeSemanaResponse(
                viernes, viernes.plusDays(2), List.of(), List.of(), List.of(), List.of(),
                new FinDeSemanaResponse.Resumen(0, 0, 0, 0, null));
        when(servicio.obtenerFinDeSemana(7L, LocalDate.of(2026, 10, 21))).thenReturn(panel);

        ResponseEntity<?> respuesta = controller.obtenerFinDeSemana("2026-10-21", autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isSameAs(panel);
    }

    @Test
    void unaFechaMalFormadaEsUn400() {
        assertThat(controller.obtenerFinDeSemana("ayer", autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void devuelve403ParaQuienNoEsCoordinacion() {
        when(servicio.obtenerFinDeSemana(any(), any())).thenThrow(new SecurityException("no"));
        when(servicio.obtenerSeguimientoJugadores(any(), any())).thenThrow(new SecurityException("no"));
        when(servicio.obtenerDetalleSeguimiento(any(), any(), any())).thenThrow(new SecurityException("no"));

        assertThat(controller.obtenerFinDeSemana(null, autenticado("8")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.obtenerSeguimiento("30d", autenticado("8")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.obtenerDetalle(1L, "30d", autenticado("8")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unPeriodoNoValidoEsUn400YUnFalloInternoNoFiltraElDetalle() {
        when(servicio.obtenerSeguimientoJugadores(any(), any())).thenThrow(new IllegalArgumentException("periodo"));
        when(servicio.obtenerDetalleSeguimiento(any(), any(), any())).thenThrow(new IllegalStateException("ORA-1"));

        assertThat(controller.obtenerSeguimiento("x", autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<?> error = controller.obtenerDetalle(1L, null, autenticado("7"));

        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(error.getBody())).doesNotContain("ORA-");
    }

    // ---- recordatorios --------------------------------------------------------------------

    private static com.mikedev.mutxamelcf.model.RecordatorioRequest recordatorio(String tipo) {
        com.mikedev.mutxamelcf.model.RecordatorioRequest request = new com.mikedev.mutxamelcf.model.RecordatorioRequest();
        request.setPartidoId(30L);
        request.setTipo(tipo);
        return request;
    }

    @Test
    void recordarDevuelve401SinAutenticacion() {
        assertThat(controller.recordar(recordatorio("CONVOCATORIA"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordarDevuelveElNumeroDeDestinatariosYLaFecha() {
        java.time.LocalDateTime fecha = java.time.LocalDateTime.of(2026, 10, 23, 10, 0);
        when(recordatorios.enviar(7L, 30L, "CONVOCATORIA"))
                .thenReturn(new com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService.Enviado(2, fecha));

        ResponseEntity<?> respuesta = controller.recordar(recordatorio("CONVOCATORIA"), autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.Map<String, Object>) respuesta.getBody())
                .containsEntry("destinatarios", 2)
                .containsEntry("fecha", fecha);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordarMapeaLosErroresDelServicio() {
        when(recordatorios.enviar(7L, 30L, "A")).thenThrow(new SecurityException("solo coordinación"));
        assertThat(controller.recordar(recordatorio("A"), autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        when(recordatorios.enviar(7L, 30L, "B")).thenThrow(new IllegalArgumentException("ya tiene convocatoria"));
        assertThat(controller.recordar(recordatorio("B"), autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        when(recordatorios.enviar(7L, 30L, "C"))
                .thenThrow(new com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService.SinEntrenadores());
        ResponseEntity<?> sinEntrenadores = controller.recordar(recordatorio("C"), autenticado("7"));
        assertThat(sinEntrenadores.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((java.util.Map<String, Object>) sinEntrenadores.getBody())
                .containsEntry("codigo", "SIN_ENTRENADORES")
                .containsEntry("mensaje", "El equipo no tiene entrenadores con cuenta en la app");

        when(recordatorios.enviar(7L, 30L, "E"))
                .thenThrow(com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService.SinEntrenadores.soloElCoordinador());
        ResponseEntity<?> unico = controller.recordar(recordatorio("E"), autenticado("7"));
        assertThat(unico.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((java.util.Map<String, Object>) unico.getBody())
                .containsEntry("codigo", "UNICO_ENTRENADOR")
                .containsEntry("mensaje", "Eres el único entrenador con cuenta de este equipo");

        java.time.LocalDateTime ultimo = java.time.LocalDateTime.of(2026, 10, 23, 9, 30);
        when(recordatorios.enviar(7L, 30L, "D"))
                .thenThrow(new com.mikedev.mutxamelcf.service.RecordatorioEntrenadorService.DemasiadoPronto(ultimo));
        ResponseEntity<?> repetido = controller.recordar(recordatorio("D"), autenticado("7"));
        assertThat(repetido.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat((java.util.Map<String, Object>) repetido.getBody())
                .containsEntry("codigo", "RECORDATORIO_REPETIDO")
                .containsEntry("ultimo", ultimo);
    }
}
