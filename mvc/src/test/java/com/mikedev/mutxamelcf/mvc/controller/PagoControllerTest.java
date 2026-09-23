package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.PagoService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagoControllerTest {

    @Test
    void guardarActualizaElEstadoDeLaCuotaCuandoTieneExito() {
        PagoService pagoService = mock(PagoService.class);
        CuotaJugadorService cuotaJugadorService = mock(CuotaJugadorService.class);
        PagoController controller = new PagoController(pagoService, cuotaJugadorService);

        PagoDTO pago = new PagoDTO();
        pago.setCuotaJugadorId(5L);
        when(pagoService.guardarPago(pago)).thenReturn(true);

        ResponseEntity<Boolean> response = controller.guardar(pago);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(cuotaJugadorService).actualizarEstado(5L);
    }

    @Test
    void guardarNoActualizaLaCuotaSiFalla() {
        PagoService pagoService = mock(PagoService.class);
        CuotaJugadorService cuotaJugadorService = mock(CuotaJugadorService.class);
        PagoController controller = new PagoController(pagoService, cuotaJugadorService);

        PagoDTO pago = new PagoDTO();
        pago.setCuotaJugadorId(5L);
        when(pagoService.guardarPago(pago)).thenReturn(false);

        assertThat(controller.guardar(pago).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(cuotaJugadorService, never()).actualizarEstado(5L);
    }

    @Test
    void obtenerPorCuotaDelegaEnElServicio() {
        PagoService pagoService = mock(PagoService.class);
        PagoController controller = new PagoController(pagoService, mock(CuotaJugadorService.class));

        when(pagoService.obtenerPorCuota(5L)).thenReturn(List.of(new PagoDTO()));

        assertThat(controller.obtenerPorCuota(5L)).hasSize(1);
    }

    @Test
    void obtenerTotalDelegaEnElServicio() {
        PagoService pagoService = mock(PagoService.class);
        PagoController controller = new PagoController(pagoService, mock(CuotaJugadorService.class));

        when(pagoService.obtenerTotalPagado(5L)).thenReturn(new BigDecimal("30.00"));

        assertThat(controller.obtenerTotal(5L)).isEqualByComparingTo("30.00");
    }

    @Test
    void eliminarActualizaElEstadoDeLaCuotaSiElPagoExistia() {
        PagoService pagoService = mock(PagoService.class);
        CuotaJugadorService cuotaJugadorService = mock(CuotaJugadorService.class);
        PagoController controller = new PagoController(pagoService, cuotaJugadorService);

        PagoDTO pago = new PagoDTO();
        pago.setCuotaJugadorId(5L);
        when(pagoService.obtenerPorId(1L)).thenReturn(pago);

        ResponseEntity<Void> response = controller.eliminar(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(pagoService).eliminar(1L);
        verify(cuotaJugadorService).actualizarEstado(5L);
    }

    @Test
    void eliminarNoActualizaLaCuotaSiElPagoNoExistia() {
        PagoService pagoService = mock(PagoService.class);
        CuotaJugadorService cuotaJugadorService = mock(CuotaJugadorService.class);
        PagoController controller = new PagoController(pagoService, cuotaJugadorService);

        when(pagoService.obtenerPorId(1L)).thenReturn(null);

        controller.eliminar(1L);

        verify(cuotaJugadorService, never()).actualizarEstado(org.mockito.ArgumentMatchers.any());
    }
}
