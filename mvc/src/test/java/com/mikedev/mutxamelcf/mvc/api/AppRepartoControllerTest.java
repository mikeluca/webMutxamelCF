package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.AsignacionRepartoRequest;
import com.mikedev.mutxamelcf.model.EquipoRepartoDTO;
import com.mikedev.mutxamelcf.model.PruebasSinConfirmarException;
import com.mikedev.mutxamelcf.model.RepartoCategoriaDTO;
import com.mikedev.mutxamelcf.model.ResultadoRepartoDTO;
import com.mikedev.mutxamelcf.service.RepartoService;

class AppRepartoControllerTest {

    private final RepartoService servicio = mock(RepartoService.class);
    private final AppRepartoController controller = new AppRepartoController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private static AsignacionRepartoRequest peticion() {
        return new AsignacionRepartoRequest(11L, List.of(new AsignacionRepartoRequest.Jugador(1L, 7)), false);
    }

    @Test
    void sinAutenticacionTodoEsUn401() {
        assertThat(controller.categorias(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.obtenerCategoria("Alevín", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.asignar(peticion(), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller.crearEquipo("Alevín", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(servicio, never()).asignar(any());
    }

    @Test
    void unUsuarioNoNumericoEsUn401() {
        assertThat(controller.categorias(autenticado("abc")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void quienNoEsCoordinadorRecibeUn403SinTocarNada() {
        doThrow(new SecurityException("Solo coordinación puede repartir jugadores")).when(servicio)
                .comprobarCoordinador(8L);

        assertThat(controller.categorias(autenticado("8")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.obtenerCategoria("Alevín", autenticado("8")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.asignar(peticion(), autenticado("8")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.crearEquipo("Alevín", autenticado("8")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        verify(servicio, never()).asignar(any());
        verify(servicio, never()).crearEquipo(any());
    }

    @Test
    void elCoordinadorListaCategoriasYElRepartoDeUna() {
        RepartoCategoriaDTO reparto = new RepartoCategoriaDTO("Alevín", null, List.of(), List.of());
        when(servicio.categorias()).thenReturn(List.of("Alevín"));
        when(servicio.obtenerCategoria("Alevín")).thenReturn(reparto);

        assertThat(controller.categorias(autenticado("7")).getBody()).isEqualTo(List.of("Alevín"));
        ResponseEntity<?> respuesta = controller.obtenerCategoria("Alevín", autenticado("7"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isSameAs(reparto);
        verify(servicio, org.mockito.Mockito.times(2)).comprobarCoordinador(7L);
    }

    @Test
    void unaCategoriaNoFormativaEsUn400() {
        when(servicio.obtenerCategoria("Senior")).thenThrow(new IllegalArgumentException("no es formativa"));

        ResponseEntity<?> respuesta = controller.obtenerCategoria("Senior", autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).isEqualTo("no es formativa");
    }

    @Test
    void asignarDevuelveElResultado() {
        ResultadoRepartoDTO resultado = new ResultadoRepartoDTO(1, List.of(), true, false, List.of());
        when(servicio.asignar(any())).thenReturn(resultado);

        ResponseEntity<?> respuesta = controller.asignar(peticion(), autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isSameAs(resultado);
    }

    @Test
    void laPruebaSinConfirmarEsUn409ConCodigoYLosNombres() {
        when(servicio.asignar(any())).thenThrow(new PruebasSinConfirmarException(List.of("Biel Gil")));

        ResponseEntity<?> respuesta = controller.asignar(peticion(), autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<?, ?> cuerpo = (Map<?, ?>) respuesta.getBody();
        assertThat(cuerpo.get("codigo")).isEqualTo("PRUEBAS_SIN_CONFIRMAR");
        assertThat(cuerpo.get("pruebas")).isEqualTo(List.of("Biel Gil"));
    }

    @Test
    void unaValidacionFallidaEsUn400YUnEstadoInvalidoUn409() {
        when(servicio.asignar(any())).thenThrow(new IllegalArgumentException("El dorsal debe estar entre 0 y 99"));
        assertThat(controller.asignar(peticion(), autenticado("7")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        doThrow(new IllegalStateException("No hay ninguna temporada activa.")).when(servicio).asignar(any());
        assertThat(controller.asignar(peticion(), autenticado("7")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void crearEquipoDevuelveUn201() {
        EquipoRepartoDTO equipo = new EquipoRepartoDTO(40L, "Alevín C", "C", "F8", false, List.of());
        when(servicio.crearEquipo("Alevín")).thenReturn(equipo);

        ResponseEntity<?> respuesta = controller.crearEquipo("Alevín", autenticado("7"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody()).isSameAs(equipo);
    }
}
