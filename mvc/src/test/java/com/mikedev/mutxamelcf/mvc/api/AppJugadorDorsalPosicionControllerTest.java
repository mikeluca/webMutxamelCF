package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.DorsalRepetidoException;
import com.mikedev.mutxamelcf.model.JugadorEquipoAppDTO;
import com.mikedev.mutxamelcf.service.JugadorDorsalPosicionService;

class AppJugadorDorsalPosicionControllerTest {

    private JugadorDorsalPosicionService service;
    private AppJugadorDorsalPosicionController controller;

    @BeforeEach
    void setUp() {
        service = mock(JugadorDorsalPosicionService.class);
        controller = new AppJugadorDorsalPosicionController(service);
    }

    private static Authentication usuario(String id) {
        Authentication auth = new TestingAuthenticationToken(id, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private static Map<String, Object> cuerpo(Object dorsal, Object posicion) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("dorsal", dorsal);
        cuerpo.put("posicion", posicion);
        return cuerpo;
    }

    @Test
    void sinSesionEs401() {

        assertThat(controller.actualizar(5L, cuerpo(7, "DEFENSA"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(service, never()).actualizar(any(), any(), any(), any());
    }

    @Test
    void guardaYDevuelveElJugadorActualizado() {

        JugadorEquipoAppDTO jugador = new JugadorEquipoAppDTO(5L, "Pablo", "Garcia", 7, "Defensa", null);
        jugador.setEditable(true);
        when(service.actualizar(3L, 5L, 7, "DEFENSA")).thenReturn(jugador);

        ResponseEntity<?> respuesta = controller.actualizar(5L, cuerpo(7, "DEFENSA"), usuario("3"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isSameAs(jugador);
    }

    @Test
    void nullEnLosDosCamposLosDejaVacios() {

        controller.actualizar(5L, cuerpo(null, null), usuario("3"));

        verify(service).actualizar(3L, 5L, null, null);
    }

    @Test
    void unDorsalConFormaDeEnteroDecimalSeAcepta() {

        controller.actualizar(5L, cuerpo(7.0, "PORTERO"), usuario("3"));

        verify(service).actualizar(3L, 5L, 7, "PORTERO");
    }

    @Test
    void hayQueEnviarLosDosCampos() {

        Map<String, Object> soloDorsal = new HashMap<>();
        soloDorsal.put("dorsal", 7);
        Map<String, Object> soloPosicion = new HashMap<>();
        soloPosicion.put("posicion", "DEFENSA");

        assertThat(controller.actualizar(5L, soloDorsal, usuario("3")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.actualizar(5L, soloPosicion, usuario("3")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.actualizar(5L, null, usuario("3")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).actualizar(any(), any(), any(), any());
    }

    @Test
    void unDorsalQueNoEsUnEnteroEsUn400() {

        assertThat(controller.actualizar(5L, cuerpo("7", null), usuario("3")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.actualizar(5L, cuerpo(7.5, null), usuario("3")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.actualizar(5L, cuerpo(null, 3), usuario("3")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(service, never()).actualizar(any(), any(), any(), any());
    }

    @Test
    void cadaErrorDelServicioTieneSuEstado() {

        when(service.actualizar(anyLong(), eq(1L), any(), any())).thenThrow(new SecurityException("No tienes permiso"));
        when(service.actualizar(anyLong(), eq(2L), any(), any())).thenThrow(new NoSuchElementException("El jugador no existe"));
        when(service.actualizar(anyLong(), eq(3L), any(), any()))
                .thenThrow(new DorsalRepetidoException("El dorsal 7 ya lo tiene Pablo Garcia"));
        when(service.actualizar(anyLong(), eq(4L), any(), any()))
                .thenThrow(new IllegalArgumentException("El dorsal debe estar entre 1 y 99"));
        when(service.actualizar(anyLong(), eq(5L), any(), any())).thenThrow(new IllegalStateException("x"));

        ResponseEntity<?> prohibido = controller.actualizar(1L, cuerpo(7, null), usuario("3"));
        ResponseEntity<?> noExiste = controller.actualizar(2L, cuerpo(7, null), usuario("3"));
        ResponseEntity<?> repetido = controller.actualizar(3L, cuerpo(7, null), usuario("3"));
        ResponseEntity<?> invalido = controller.actualizar(4L, cuerpo(700, null), usuario("3"));
        ResponseEntity<?> fallo = controller.actualizar(5L, cuerpo(7, null), usuario("3"));

        assertThat(prohibido.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(noExiste.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(repetido.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repetido.getBody()).isEqualTo("El dorsal 7 ya lo tiene Pablo Garcia");
        assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalido.getBody()).isEqualTo("El dorsal debe estar entre 1 y 99");
        assertThat(fallo.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void unUsuarioNoNumericoEs401() {

        assertThat(controller.actualizar(5L, cuerpo(7, null), usuario("anonymousUser")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
