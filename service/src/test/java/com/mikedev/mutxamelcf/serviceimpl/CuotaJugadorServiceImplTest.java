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

import com.mikedev.mutxamelcf.dao.CuotaJugadorDao;
import com.mikedev.mutxamelcf.model.CuotaJugador;
import com.mikedev.mutxamelcf.model.CuotaJugadorDTO;

@ExtendWith(MockitoExtension.class)
class CuotaJugadorServiceImplTest {

    @Mock
    private CuotaJugadorDao cuotaJugadorDao;

    private CuotaJugadorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CuotaJugadorServiceImpl(cuotaJugadorDao);
    }

    @Test
    void guardarCuotaDelegaEnElDao() {
        when(cuotaJugadorDao.guardarCuota(any(CuotaJugador.class))).thenReturn(true);

        CuotaJugadorDTO dto = new CuotaJugadorDTO();
        dto.setImporte(new BigDecimal("20.00"));

        assertThat(service.guardarCuota(dto)).isTrue();
    }

    @Test
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        when(cuotaJugadorDao.obtenerPorId(99L)).thenReturn(null);

        assertThat(service.obtenerPorId(99L)).isNull();
    }

    @Test
    void obtenerPorJugadorDelegaEnElDao() {
        when(cuotaJugadorDao.obtenerPorJugador(1L)).thenReturn(List.of(new CuotaJugador()));

        assertThat(service.obtenerPorJugador(1L)).hasSize(1);
    }

    @Test
    void obtenerPorEstadoDelegaEnElDao() {
        when(cuotaJugadorDao.obtenerPorEstado("PENDIENTE")).thenReturn(List.of(new CuotaJugador()));

        assertThat(service.obtenerPorEstado("PENDIENTE")).hasSize(1);
    }

    @Test
    void obtenerTodosDelegaEnElDao() {
        when(cuotaJugadorDao.obtenerTodos()).thenReturn(List.of(new CuotaJugador()));

        assertThat(service.obtenerTodos()).hasSize(1);
    }

    @Test
    void obtenerPorTemporadaDelegaEnElDao() {
        when(cuotaJugadorDao.obtenerPorTemporada(1L)).thenReturn(List.of(new CuotaJugador()));

        assertThat(service.obtenerPorTemporada(1L)).hasSize(1);
    }

    @Test
    void actualizarEstadoDelegaEnElDao() {
        service.actualizarEstado(1L);

        verify(cuotaJugadorDao).actualizarEstado(1L);
    }

    @Test
    void eliminarDelegaEnElDao() {
        service.eliminar(1L);

        verify(cuotaJugadorDao).eliminar(1L);
    }

    @Test
    void eliminarEnLoteDelegaEnElDao() {
        service.eliminarEnLote(List.of(1L, 2L));

        verify(cuotaJugadorDao).eliminarEnLote(List.of(1L, 2L));
    }
}
