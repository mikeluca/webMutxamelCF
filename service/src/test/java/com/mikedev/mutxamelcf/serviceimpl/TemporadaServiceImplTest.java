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

    @Mock
    private com.mikedev.mutxamelcf.dao.JugadorTemporadaDao jugadorTemporadaDao;

    private TemporadaServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TemporadaServiceImpl(temporadaDao, jugadorTemporadaDao);
    }

    @Test
    void obtenerFechaFinTemporadaActivaDevuelveLaFechaDeFin() {
        Temporada temporada = new Temporada();
        temporada.setId(1L);
        temporada.setActiva(1);
        temporada.setFechaFin(java.sql.Date.valueOf("2027-06-30"));

        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporada);

        assertThat(service.obtenerFechaFinTemporadaActiva()).isEqualTo(java.time.LocalDate.of(2027, 6, 30));
    }

    @Test
    void obtenerFechaFinTemporadaActivaEsNullSinTemporadaActiva() {
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(null);

        assertThat(service.obtenerFechaFinTemporadaActiva()).isNull();
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
    void sinPlantillasSeBorraComoSiempre() {
        Temporada pasada = temporada(1L, 0, "2024-09-01");
        when(temporadaDao.obtenerPorId(1L)).thenReturn(pasada);
        when(jugadorTemporadaDao.contarPorTemporada(1L)).thenReturn(0);

        service.eliminar(1L, false);

        verify(temporadaDao).eliminar(1L);
        org.mockito.Mockito.verify(jugadorTemporadaDao, org.mockito.Mockito.never()).eliminarPorTemporada(any());
    }

    @Test
    void laActivaNoSePuedeBorrarAunqueNoTengaFilas() {
        when(temporadaDao.obtenerPorId(1L)).thenReturn(temporada(1L, 1, "2026-09-01"));
        when(jugadorTemporadaDao.contarPorTemporada(1L)).thenReturn(0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.eliminar(1L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Esta temporada tiene historial de plantillas y no se puede borrar");

        org.mockito.Mockito.verify(temporadaDao, org.mockito.Mockito.never()).eliminar(any());
    }

    @Test
    void unaTemporadaQueYaEstuvoActivaConPlantillasNoSePuedeBorrarNiConfirmando() {
        when(temporadaDao.obtenerPorId(3L)).thenReturn(temporada(3L, 0, "2025-09-01"));
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporada(1L, 1, "2026-09-01"));
        when(jugadorTemporadaDao.contarPorTemporada(3L)).thenReturn(40);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.eliminar(3L, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Esta temporada tiene historial de plantillas y no se puede borrar");

        org.mockito.Mockito.verify(jugadorTemporadaDao, org.mockito.Mockito.never()).eliminarPorTemporada(any());
        org.mockito.Mockito.verify(temporadaDao, org.mockito.Mockito.never()).eliminar(any());
    }

    @Test
    void unaFuturaConBorradorPideConfirmacionYNoBorraNada() {
        when(temporadaDao.obtenerPorId(2L)).thenReturn(temporada(2L, 0, "2027-09-01"));
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporada(1L, 1, "2026-09-01"));
        when(jugadorTemporadaDao.contarPorTemporada(2L)).thenReturn(35);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.eliminar(2L, false))
                .isInstanceOf(com.mikedev.mutxamelcf.model.BorradorPlantillasException.class)
                .hasMessage("Se borrará también el borrador de plantillas de 35 jugadores");

        org.mockito.Mockito.verify(jugadorTemporadaDao, org.mockito.Mockito.never()).eliminarPorTemporada(any());
        org.mockito.Mockito.verify(temporadaDao, org.mockito.Mockito.never()).eliminar(any());
    }

    @Test
    void unaFuturaConBorradorConfirmadaBorraPrimeroElBorradorYLuegoLaTemporada() {
        when(temporadaDao.obtenerPorId(2L)).thenReturn(temporada(2L, 0, "2027-09-01"));
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporada(1L, 1, "2026-09-01"));
        when(jugadorTemporadaDao.contarPorTemporada(2L)).thenReturn(35);

        service.eliminar(2L, true);

        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(jugadorTemporadaDao, temporadaDao);
        orden.verify(jugadorTemporadaDao).eliminarPorTemporada(2L);
        orden.verify(temporadaDao).eliminar(2L);
    }

    @Test
    void unErrorDeIntegridadSeConvierteEnUnMensajeClaro() {
        when(temporadaDao.obtenerPorId(1L)).thenReturn(temporada(1L, 0, "2024-09-01"));
        when(jugadorTemporadaDao.contarPorTemporada(1L)).thenReturn(0);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("ORA-02292"))
                .when(temporadaDao).eliminar(1L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.eliminar(1L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("datos relacionados");
    }

    private static Temporada temporada(Long id, int activa, String inicio) {
        Temporada t = new Temporada();
        t.setId(id);
        t.setActiva(activa);
        t.setFechaInicio(java.sql.Date.valueOf(inicio));
        return t;
    }
}
