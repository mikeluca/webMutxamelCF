package com.mikedev.mutxamelcf.mvc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.AlineacionRequest;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse;
import com.mikedev.mutxamelcf.model.EstadoPartidoEnVivoResponse.UltimoEventoDeshacible;
import com.mikedev.mutxamelcf.model.GolFavorRequest;
import com.mikedev.mutxamelcf.service.PartidoEnVivoService;

class AppPartidoEnVivoControllerTest {

    private final PartidoEnVivoService servicio = mock(PartidoEnVivoService.class);
    private final AppPartidoEnVivoController controller = new AppPartidoEnVivoController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private static EstadoPartidoEnVivoResponse estado() {
        return new EstadoPartidoEnVivoResponse(30L, "Pego C.F.", true, "PRIMERA_PARTE", 1, 0, List.of("Pablo"),
                List.of(), new UltimoEventoDeshacible(5L, LocalDateTime.of(2026, 10, 24, 11, 2)));
    }

    @Test
    void todasLasRutasDan401SinAutenticacion() {
        assertThat(controller.partidosDisponibles(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.estado(30L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.convocados(30L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.inicio(30L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.golContra(30L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.deshacer(30L, 5L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void partidosDisponiblesDevuelveLaLista() {
        when(servicio.obtenerPartidosDisponibles(7L)).thenReturn(List.of());

        ResponseEntity<?> respuesta = controller.partidosDisponibles(autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(List.of());
    }

    @Test
    void elEstadoDelPartidoSeDevuelveEnElCuerpo() {
        when(servicio.obtenerEstado(7L, 30L)).thenReturn(estado());

        ResponseEntity<?> respuesta = controller.estado(30L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEqualTo(estado());
    }

    @Test
    void cadaEventoLlevaElPartidoIdYDevuelveElEstado() {
        when(servicio.enviarInicioPartido(7L, 30L)).thenReturn(estado());
        when(servicio.enviarGolContra(7L, 30L)).thenReturn(estado());
        when(servicio.enviarDescanso(7L, 30L)).thenReturn(estado());
        when(servicio.enviarSegundaParte(7L, 30L)).thenReturn(estado());
        when(servicio.enviarFinalPartido(7L, 30L)).thenReturn(estado());

        Authentication auth = autenticado("7");

        assertThat(controller.inicio(30L, auth).getBody()).isEqualTo(estado());
        assertThat(controller.golContra(30L, auth).getBody()).isEqualTo(estado());
        assertThat(controller.descanso(30L, auth).getBody()).isEqualTo(estado());
        assertThat(controller.segundaParte(30L, auth).getBody()).isEqualTo(estado());
        assertThat(controller.finalPartido(30L, auth).getBody()).isEqualTo(estado());
    }

    @Test
    void golFavorPasaElJugadorOElTexto() {
        GolFavorRequest conJugador = new GolFavorRequest();
        conJugador.setJugadorId(10L);

        GolFavorRequest conTexto = new GolFavorRequest();
        conTexto.setAutorTexto("p.p.");

        when(servicio.enviarGolFavor(any(), anyLong(), any(), any())).thenReturn(estado());

        controller.golFavor(30L, conJugador, autenticado("7"));
        controller.golFavor(30L, conTexto, autenticado("7"));

        verify(servicio).enviarGolFavor(7L, 30L, 10L, null);
        verify(servicio).enviarGolFavor(7L, 30L, null, "p.p.");
    }

    @Test
    void deshacerLlevaPartidoYEvento() {
        when(servicio.deshacer(7L, 30L, 5L)).thenReturn(estado());

        ResponseEntity<?> respuesta = controller.deshacer(30L, 5L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(servicio).deshacer(7L, 30L, 5L);
    }

    @Test
    void sinElRolDevuelve403() {
        when(servicio.enviarInicioPartido(any(), any())).thenThrow(new SecurityException("sin permiso"));

        assertThat(controller.inicio(30L, autenticado("7")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unPartidoOUnOrdenNoValidosDevuelven400ConElMensaje() {
        when(servicio.enviarGolContra(any(), any()))
                .thenThrow(new IllegalArgumentException("El partido todavía no ha empezado"));

        ResponseEntity<?> respuesta = controller.golContra(30L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isEqualTo("El partido todavía no ha empezado");
    }

    @Test
    void unErrorInesperadoDevuelve500SinFiltrarElDetalle() {
        when(servicio.obtenerEstado(any(), any())).thenThrow(new IllegalStateException("ORA-00942"));

        ResponseEntity<?> respuesta = controller.estado(30L, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(String.valueOf(respuesta.getBody())).doesNotContain("ORA-");
    }

    @Test
    void laAlineacionSigueSinPartido() {
        AlineacionRequest request = new AlineacionRequest();
        request.setOnceInicial("1, 2");
        request.setSuplentes("3");

        ResponseEntity<?> respuesta = controller.alineacion(request, autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(servicio).enviarAlineacion(7L, "1, 2", "3");
    }

    @Test
    void laAlineacionSinRolDevuelve403() {
        doThrow(new SecurityException("sin permiso")).when(servicio).enviarAlineacion(any(), any(), any());

        assertThat(controller.alineacion(new AlineacionRequest(), autenticado("7")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
