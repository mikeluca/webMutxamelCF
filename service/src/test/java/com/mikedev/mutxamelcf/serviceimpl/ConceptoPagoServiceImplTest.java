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

import com.mikedev.mutxamelcf.dao.ConceptoPagoDao;
import com.mikedev.mutxamelcf.model.ConceptoPago;
import com.mikedev.mutxamelcf.model.ConceptoPagoDTO;

@ExtendWith(MockitoExtension.class)
class ConceptoPagoServiceImplTest {

    @Mock
    private ConceptoPagoDao conceptoPagoDao;

    private ConceptoPagoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConceptoPagoServiceImpl(conceptoPagoDao);
    }

    @Test
    void guardarConceptoPagoDelegaEnElDao() {
        when(conceptoPagoDao.guardarConceptoPago(any(ConceptoPago.class))).thenReturn(true);

        ConceptoPagoDTO dto = new ConceptoPagoDTO();
        dto.setNombre("Cuota mensual");

        assertThat(service.guardarConceptoPago(dto)).isTrue();
    }

    @Test
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        when(conceptoPagoDao.obtenerPorId(99L)).thenReturn(null);

        assertThat(service.obtenerPorId(99L)).isNull();
    }

    @Test
    void obtenerPorIdMapeaLosCampos() {
        ConceptoPago concepto = new ConceptoPago();
        concepto.setId(1L);
        concepto.setNombre("Cuota mensual");
        concepto.setImporte(new BigDecimal("20.00"));

        when(conceptoPagoDao.obtenerPorId(1L)).thenReturn(concepto);

        ConceptoPagoDTO resultado = service.obtenerPorId(1L);

        assertThat(resultado.getNombre()).isEqualTo("Cuota mensual");
        assertThat(resultado.getImporte()).isEqualByComparingTo("20.00");
    }

    @Test
    void obtenerPorTemporadaDelegaEnElDao() {
        when(conceptoPagoDao.obtenerPorTemporada(1L)).thenReturn(List.of(new ConceptoPago()));

        assertThat(service.obtenerPorTemporada(1L)).hasSize(1);
    }

    @Test
    void obtenerActivosPorTemporadaDelegaEnElDao() {
        when(conceptoPagoDao.obtenerActivosPorTemporada(1L)).thenReturn(List.of(new ConceptoPago()));

        assertThat(service.obtenerActivosPorTemporada(1L)).hasSize(1);
    }

    @Test
    void obtenerTodosDelegaEnElDao() {
        when(conceptoPagoDao.obtenerTodos()).thenReturn(List.of(new ConceptoPago()));

        assertThat(service.obtenerTodos()).hasSize(1);
    }

    @Test
    void eliminarDelegaEnElDao() {
        service.eliminar(1L);

        verify(conceptoPagoDao).eliminar(1L);
    }
}
