package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.FamiliarContactoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.service.FamiliarService;
import com.mikedev.mutxamelcf.service.JugadorService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppEquipoGestionControllerTest {

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private EquipoGestionDao equipoGestionDao;
    private JugadorService jugadorService;
    private FamiliarService familiarService;
    private AppEquipoGestionController controller;

    private void setUp() {
        equipoGestionDao = mock(EquipoGestionDao.class);
        jugadorService = mock(JugadorService.class);
        familiarService = mock(FamiliarService.class);
        controller = new AppEquipoGestionController(equipoGestionDao, jugadorService, familiarService);
    }

    @Test
    void obtenerJugadoresDevuelve401SinAutenticacion() {
        setUp();
        assertThat(controller.obtenerJugadores(1L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerJugadoresDevuelve400SiElEquipoIdEsInvalido() {
        setUp();
        assertThat(controller.obtenerJugadores(0L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerJugadoresDevuelve400SiElEquipoNoExiste() {
        setUp();
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(false);

        assertThat(controller.obtenerJugadores(1L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerJugadoresDevuelve403SiNoPuedeGestionarElEquipo() {
        setUp();
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(false);

        assertThat(controller.obtenerJugadores(1L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerJugadoresFiltraLosNoEncontradosYDevuelveOk() {
        setUp();
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.obtenerJugadoresPorEquipo(1L)).thenReturn(List.of(10L, 20L));
        when(jugadorService.obtenerJugadorPorId(10L)).thenReturn(new JugadorDTO());
        when(jugadorService.obtenerJugadorPorId(20L)).thenReturn(null);

        ResponseEntity<?> response = controller.obtenerJugadores(1L, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).hasSize(1);
    }

    @Test
    void obtenerFamiliaresDevuelve400SiElJugadorIdEsInvalido() {
        setUp();
        assertThat(controller.obtenerFamiliares(0L, 1L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerFamiliaresDevuelve403SiElJugadorNoPerteneceAlEquipo() {
        setUp();
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.perteneceJugadorAEquipo(5L, 1L)).thenReturn(false);

        assertThat(controller.obtenerFamiliares(5L, 1L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerFamiliaresDevuelveLosFamiliaresDelJugador() {
        setUp();
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(true);
        when(equipoGestionDao.perteneceJugadorAEquipo(5L, 1L)).thenReturn(true);
        when(familiarService.obtenerFamiliaresPorJugador(5L)).thenReturn(List.of(new FamiliarContactoDTO()));

        ResponseEntity<?> response = controller.obtenerFamiliares(5L, 1L, autenticado("1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) response.getBody()).hasSize(1);
    }

    @Test
    void obtenerJugadoresDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        setUp();
        assertThat(controller.obtenerJugadores(1L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerJugadoresDevuelve500AnteUnErrorInesperado() {
        setUp();
        when(equipoGestionDao.existeEquipo(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerJugadores(1L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void obtenerFamiliaresDevuelve401SinAutenticacion() {
        setUp();
        assertThat(controller.obtenerFamiliares(5L, 1L, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerFamiliaresDevuelve400SiElEquipoIdEsInvalido() {
        setUp();
        assertThat(controller.obtenerFamiliares(5L, 0L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerFamiliaresDevuelve400SiElEquipoNoExiste() {
        setUp();
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(false);

        assertThat(controller.obtenerFamiliares(5L, 1L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void obtenerFamiliaresDevuelve403SiNoPuedeGestionarElEquipo() {
        setUp();
        when(equipoGestionDao.existeEquipo(1L)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(1L, 1L)).thenReturn(false);

        assertThat(controller.obtenerFamiliares(5L, 1L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void obtenerFamiliaresDevuelve401SiElNombreDeUsuarioNoEsNumerico() {
        setUp();
        assertThat(controller.obtenerFamiliares(5L, 1L, autenticado("no-numero")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void obtenerFamiliaresDevuelve500AnteUnErrorInesperado() {
        setUp();
        when(equipoGestionDao.existeEquipo(1L)).thenThrow(new RuntimeException("fallo"));

        assertThat(controller.obtenerFamiliares(5L, 1L, autenticado("1")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
