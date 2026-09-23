package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.PagoDao;
import com.mikedev.mutxamelcf.model.Pago;
import com.mikedev.mutxamelcf.model.PagoDTO;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    @Mock
    private PagoDao pagoDao;

    private PagoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PagoServiceImpl(pagoDao);
    }

    @Test
    void guardarPagoTrasladaElIdGeneradoAlDto() {
        when(pagoDao.guardarPago(any(Pago.class))).thenAnswer(invocation -> {
            Pago pago = invocation.getArgument(0);
            pago.setId(77L);
            return true;
        });

        PagoDTO dto = new PagoDTO();
        dto.setCuotaJugadorId(1L);
        dto.setImporte(new BigDecimal("20.00"));

        boolean resultado = service.guardarPago(dto);

        assertThat(resultado).isTrue();
        assertThat(dto.getId()).isEqualTo(77L);
    }

    @Test
    void guardarPagoNoTocaElDtoSiFalla() {
        when(pagoDao.guardarPago(any(Pago.class))).thenReturn(false);

        PagoDTO dto = new PagoDTO();

        assertThat(service.guardarPago(dto)).isFalse();
        assertThat(dto.getId()).isNull();
    }

    @Test
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        when(pagoDao.obtenerPorId(99L)).thenReturn(null);

        assertThat(service.obtenerPorId(99L)).isNull();
    }

    @Test
    void obtenerPorIdMapeaLosCampos() {
        Pago pago = new Pago();
        pago.setId(1L);
        pago.setCuotaJugadorId(2L);
        pago.setImporte(new BigDecimal("15.00"));
        pago.setMetodoPago("EFECTIVO");

        when(pagoDao.obtenerPorId(1L)).thenReturn(pago);

        PagoDTO resultado = service.obtenerPorId(1L);

        assertThat(resultado.getMetodoPago()).isEqualTo("EFECTIVO");
        assertThat(resultado.getImporte()).isEqualByComparingTo("15.00");
    }

    @Test
    void obtenerPorCuotaDelegaEnElDao() {
        when(pagoDao.obtenerPorCuota(1L)).thenReturn(List.of(new Pago()));

        assertThat(service.obtenerPorCuota(1L)).hasSize(1);
    }

    @Test
    void obtenerPorCuotasDelegaEnElDao() {
        when(pagoDao.obtenerPorCuotas(List.of(1L, 2L))).thenReturn(List.of(new Pago(), new Pago()));

        assertThat(service.obtenerPorCuotas(List.of(1L, 2L))).hasSize(2);
    }

    @Test
    void obtenerTotalPagadoDelegaEnElDao() {
        when(pagoDao.obtenerTotalPagado(1L)).thenReturn(new BigDecimal("40.00"));

        assertThat(service.obtenerTotalPagado(1L)).isEqualByComparingTo("40.00");
    }

    @Test
    void eliminarDelegaEnElDao() {
        service.eliminar(1L);

        verify(pagoDao).eliminar(1L);
    }
}
