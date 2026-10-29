package com.mikedev.mutxamelcf.mvc.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.mikedev.mutxamelcf.model.ContinuidadJugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.service.ContinuidadService;

class AppContinuidadControllerTest {

    private final ContinuidadService servicio = mock(ContinuidadService.class);
    private final AppContinuidadController continuidad = new AppContinuidadController(servicio);
    private final AppPruebasController pruebas = new AppPruebasController(servicio);

    private static Authentication autenticado(String nombre) {
        Authentication auth = new TestingAuthenticationToken(nombre, null);
        auth.setAuthenticated(true);
        return auth;
    }

    private static ContinuidadJugadorDTO dto(String respuesta) {
        return new ContinuidadJugadorDTO(1L, "Pablo Pérez", "Alevín", 7L, "2027/2028", respuesta,
                LocalDateTime.of(2027, 5, 3, 9, 30));
    }

    // ---- GET ----------------------------------------------------------------------------------------

    @Test
    void listarSinAutenticacionEsUn401YConUsuarioNoNumericoTambien() {
        assertThat(continuidad.listar(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(continuidad.listar(autenticado("abc")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listarDevuelveLosJugadoresDelUsuario() {
        when(servicio.listar(50L)).thenReturn(List.of(dto("PENDIENTE")));

        ResponseEntity<?> respuesta = continuidad.listar(autenticado("50"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) respuesta.getBody()).hasSize(1);
    }

    @Test
    void sinBorradorLaListaVaciaEsUn200() {
        when(servicio.listar(50L)).thenReturn(List.of());

        ResponseEntity<?> respuesta = continuidad.listar(autenticado("50"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) respuesta.getBody()).isEmpty();
    }

    // ---- PUT ----------------------------------------------------------------------------------------

    @Test
    void responderSinAutenticacionEsUn401() {
        assertThat(continuidad.responder(1L, Map.of("respuesta", "SIGUE"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void responderDelegaConElUsuarioYDevuelveElEstadoNuevo() {
        when(servicio.responder(50L, 1L, "NO_SIGUE")).thenReturn(dto("NO_SIGUE"));

        ResponseEntity<?> respuesta = continuidad.responder(1L, Map.of("respuesta", "NO_SIGUE"), autenticado("50"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((ContinuidadJugadorDTO) respuesta.getBody()).respuesta()).isEqualTo("NO_SIGUE");
    }

    @Test
    void sinVinculoEsUn403UnaRespuestaInvalidaUn400YSinTemporadaUn409() {
        when(servicio.responder(50L, 1L, "SIGUE")).thenThrow(new SecurityException("No tienes vinculado a este jugador."));
        when(servicio.responder(50L, 2L, "TAL_VEZ")).thenThrow(new IllegalArgumentException("respuesta no válida"));
        when(servicio.responder(50L, 3L, "SIGUE")).thenThrow(new IllegalStateException("No hay temporada siguiente"));

        assertThat(continuidad.responder(1L, Map.of("respuesta", "SIGUE"), autenticado("50")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(continuidad.responder(2L, Map.of("respuesta", "TAL_VEZ"), autenticado("50")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(continuidad.responder(3L, Map.of("respuesta", "SIGUE"), autenticado("50")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void sinCuerpoLaRespuestaVaNulaYElServicioLaRechaza() {
        when(servicio.responder(50L, 1L, null)).thenThrow(new IllegalArgumentException("respuesta no válida"));

        assertThat(continuidad.responder(1L, null, autenticado("50")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- POST /api/app/pruebas -------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void altaDePruebaDevuelveUn201ConElJugadorCreado() {
        JugadorTemporada fila = new JugadorTemporada();
        fila.setJugadorId(99L);
        fila.setTemporadaId(7L);
        fila.setCategoria("Alevín");
        fila.setEstado("PRUEBA");
        when(servicio.altaPrueba(9L, "Luis", "Gil", 2016)).thenReturn(fila);

        ResponseEntity<?> respuesta = pruebas.alta(new AppPruebasController.AltaPruebaRequest("Luis", "Gil", 2016),
                autenticado("9"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((Map<String, Object>) respuesta.getBody()).containsEntry("jugadorId", 99L).containsEntry("categoria", "Alevín")
                .containsEntry("temporadaId", 7L).containsEntry("estado", "PRUEBA");
    }

    @Test
    void altaDePruebaErrores() {
        assertThat(pruebas.alta(new AppPruebasController.AltaPruebaRequest("L", "G", 2016), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        when(servicio.altaPrueba(8L, "Luis", "Gil", 2016)).thenThrow(new SecurityException("no"));
        when(servicio.altaPrueba(9L, "Luis", "Gil", null)).thenThrow(new IllegalArgumentException("falta el año"));
        when(servicio.altaPrueba(9L, "Luis", "Gil", 2016)).thenThrow(new IllegalStateException("no hay siguiente"));

        assertThat(pruebas.alta(new AppPruebasController.AltaPruebaRequest("Luis", "Gil", 2016), autenticado("8"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pruebas.alta(new AppPruebasController.AltaPruebaRequest("Luis", "Gil", null), autenticado("9"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(pruebas.alta(new AppPruebasController.AltaPruebaRequest("Luis", "Gil", 2016), autenticado("9"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(servicio).altaPrueba(9L, "Luis", "Gil", null);
    }

    @Test
    void altaDePruebaSinCuerpoPasaNulosAlServicio() {
        when(servicio.altaPrueba(any(), any(), any(), any())).thenThrow(new IllegalArgumentException("faltan datos"));

        assertThat(pruebas.alta(null, autenticado("9")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
