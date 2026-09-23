package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;
import com.mikedev.mutxamelcf.model.PagoDTO;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.PagoService;

@ExtendWith(MockitoExtension.class)
class BecadoServiceImplTest {

    @Mock
    private CuotaJugadorService cuotaJugadorService;

    @Mock
    private PagoService pagoService;

    private BecadoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BecadoServiceImpl(cuotaJugadorService, pagoService);
    }

    @Test
    void marcarCuotasComoBecadasLanzaExcepcionSiElJugadorEsNull() {
        assertThatThrownBy(() -> service.marcarCuotasComoBecadas(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marcarCuotasComoBecadasNoCuentaCuotasYaPagadasDelTodo() {
        CuotaJugadorDTO cuota = new CuotaJugadorDTO();
        cuota.setId(1L);
        cuota.setImporte(new BigDecimal("20.00"));

        when(cuotaJugadorService.obtenerPorJugador(5L)).thenReturn(List.of(cuota));
        when(pagoService.obtenerTotalPagado(1L)).thenReturn(new BigDecimal("20.00"));

        int resultado = service.marcarCuotasComoBecadas(5L);

        assertThat(resultado).isZero();
        verify(pagoService, never()).guardarPago(any());
        verify(cuotaJugadorService, never()).actualizarEstado(any());
    }

    @Test
    void marcarCuotasComoBecadasRegistraElPagoDeLoPendiente() {
        CuotaJugadorDTO cuota = new CuotaJugadorDTO();
        cuota.setId(1L);
        cuota.setImporte(new BigDecimal("20.00"));

        when(cuotaJugadorService.obtenerPorJugador(5L)).thenReturn(List.of(cuota));
        when(pagoService.obtenerTotalPagado(1L)).thenReturn(new BigDecimal("5.00"));
        when(pagoService.guardarPago(any(PagoDTO.class))).thenReturn(true);

        int resultado = service.marcarCuotasComoBecadas(5L);

        assertThat(resultado).isEqualTo(1);

        org.mockito.ArgumentCaptor<PagoDTO> captor = org.mockito.ArgumentCaptor.forClass(PagoDTO.class);
        verify(pagoService).guardarPago(captor.capture());
        assertThat(captor.getValue().getImporte()).isEqualByComparingTo("15.00");
        assertThat(captor.getValue().getMetodoPago()).isEqualTo("BECADO");

        verify(cuotaJugadorService).actualizarEstado(1L);
    }

    @Test
    void marcarCuotasComoBecadasTrataElTotalPagadoNuloComoCero() {
        CuotaJugadorDTO cuota = new CuotaJugadorDTO();
        cuota.setId(1L);
        cuota.setImporte(new BigDecimal("20.00"));

        when(cuotaJugadorService.obtenerPorJugador(5L)).thenReturn(List.of(cuota));
        when(pagoService.obtenerTotalPagado(1L)).thenReturn(null);
        when(pagoService.guardarPago(any(PagoDTO.class))).thenReturn(true);

        int resultado = service.marcarCuotasComoBecadas(5L);

        assertThat(resultado).isEqualTo(1);

        org.mockito.ArgumentCaptor<PagoDTO> captor = org.mockito.ArgumentCaptor.forClass(PagoDTO.class);
        verify(pagoService).guardarPago(captor.capture());
        assertThat(captor.getValue().getImporte()).isEqualByComparingTo("20.00");
    }

    @Test
    void marcarCuotasComoBecadasLanzaExcepcionSiFallaElGuardadoDelPago() {
        CuotaJugadorDTO cuota = new CuotaJugadorDTO();
        cuota.setId(1L);
        cuota.setImporte(new BigDecimal("20.00"));

        when(cuotaJugadorService.obtenerPorJugador(5L)).thenReturn(List.of(cuota));
        when(pagoService.obtenerTotalPagado(1L)).thenReturn(BigDecimal.ZERO);
        when(pagoService.guardarPago(any(PagoDTO.class))).thenReturn(false);

        assertThatThrownBy(() -> service.marcarCuotasComoBecadas(5L))
                .isInstanceOf(IllegalStateException.class);

        verify(cuotaJugadorService, never()).actualizarEstado(any());
    }
}
