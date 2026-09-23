package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.AlineacionRequest;
import com.mikedev.mutxamelcf.model.GolFavorRequest;
import com.mikedev.mutxamelcf.service.PartidoEnVivoService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AppPartidoEnVivoControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    @Test
    void inicioDevuelve401SiNoHayAutenticacion() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        assertThat(controller.inicio(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void inicioDevuelve401SiElNombreNoEsUnIdValido() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        assertThat(controller.inicio(autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void inicioDevuelve403SiElServicioDeniegaPorRol() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        doThrow(new SecurityException("sin permiso")).when(service).enviarInicioPartido(1L);

        ResponseEntity<?> response = controller.inicio(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void inicioDevuelve400SiElServicioLanzaIllegalArgument() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        doThrow(new IllegalArgumentException("no valido")).when(service).enviarInicioPartido(1L);

        ResponseEntity<?> response = controller.inicio(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void inicioDevuelve500AnteUnErrorInesperado() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        doThrow(new RuntimeException("fallo")).when(service).enviarInicioPartido(1L);

        ResponseEntity<?> response = controller.inicio(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void inicioDevuelveOkCuandoTodoVaBien() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        ResponseEntity<?> response = controller.inicio(autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).enviarInicioPartido(1L);
    }

    @Test
    void alineacionDelegaEnElServicioConLosDatosDelRequest() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        AlineacionRequest request = new AlineacionRequest();
        request.setOnceInicial("Jugador 1, Jugador 2");
        request.setSuplentes("Jugador 3");

        controller.alineacion(request, autenticado("1"));

        verify(service).enviarAlineacion(1L, request.getOnceInicial(), request.getSuplentes());
    }

    @Test
    void golFavorDelegaEnElServicioConElAutor() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        GolFavorRequest request = new GolFavorRequest();
        request.setAutor("Jugador 1");

        controller.golFavor(request, autenticado("1"));

        verify(service).enviarGolFavor(1L, "Jugador 1");
    }

    @Test
    void golContraDelegaEnElServicio() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        controller.golContra(autenticado("1"));

        verify(service).enviarGolContra(1L);
    }

    @Test
    void descansoDelegaEnElServicio() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        controller.descanso(autenticado("1"));

        verify(service).enviarDescanso(1L);
    }

    @Test
    void segundaParteDelegaEnElServicio() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        controller.segundaParte(autenticado("1"));

        verify(service).enviarSegundaParte(1L);
    }

    @Test
    void finalPartidoDelegaEnElServicio() {
        PartidoEnVivoService service = mock(PartidoEnVivoService.class);
        AppPartidoEnVivoController controller = new AppPartidoEnVivoController(service);

        controller.finalPartido(autenticado("1"));

        verify(service).enviarFinalPartido(1L);
    }
}
