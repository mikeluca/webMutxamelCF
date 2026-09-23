package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.model.TemporadaDTO;

@ExtendWith(MockitoExtension.class)
class TemporadaServiceImplTest {

    @Mock
    private TemporadaDao temporadaDao;

    private TemporadaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TemporadaServiceImpl(temporadaDao);
    }

    @Test
    void guardarTemporadaActivaDesactivaLasDemas() {
        TemporadaDTO dto = new TemporadaDTO();
        dto.setId(1L);
        dto.setNombre("2025/2026");
        dto.setActiva(1);

        when(temporadaDao.guardarTemporada(any(Temporada.class))).thenReturn(true);

        assertThat(service.guardarTemporada(dto)).isTrue();

        verify(temporadaDao).desactivarOtrasTemporadas(1L);
    }

    @Test
    void guardarTemporadaNoActivaNoTocaLasDemas() {
        TemporadaDTO dto = new TemporadaDTO();
        dto.setNombre("2024/2025");
        dto.setActiva(0);

        when(temporadaDao.guardarTemporada(any(Temporada.class))).thenReturn(true);

        assertThat(service.guardarTemporada(dto)).isTrue();

        verify(temporadaDao, never()).desactivarOtrasTemporadas(any());
    }

    @Test
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        when(temporadaDao.obtenerPorId(99L)).thenReturn(null);

        assertThat(service.obtenerPorId(99L)).isNull();
    }

    @Test
    void obtenerTemporadaActivaDelegaEnElDao() {
        Temporada temporada = new Temporada();
        temporada.setNombre("2025/2026");
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporada);

        assertThat(service.obtenerTemporadaActiva().getNombre()).isEqualTo("2025/2026");
    }

    @Test
    void obtenerTodosDelegaEnElDao() {
        when(temporadaDao.obtenerTodos()).thenReturn(List.of(new Temporada()));

        assertThat(service.obtenerTodos()).hasSize(1);
    }

    @Test
    void eliminarDelegaEnElDao() {
        service.eliminar(1L);

        verify(temporadaDao).eliminar(1L);
    }
}
