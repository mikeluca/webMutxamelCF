package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.mikedev.mutxamelcf.model.CuotaJugador;

class CuotaJugadorDaoImplTest {

    @Test
    void eliminarDebeBorrarPagosAntesDeLaCuota() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update("DELETE FROM PAGOS WHERE CUOTA_JUGADOR_ID = ?", 42L)).thenReturn(1);
        when(jdbcTemplate.update("DELETE FROM CUOTAS_JUGADOR WHERE ID = ?", 42L)).thenReturn(1);

        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        dao.eliminar(42L);

        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).update("DELETE FROM PAGOS WHERE CUOTA_JUGADOR_ID = ?", 42L);
        inOrder.verify(jdbcTemplate).update("DELETE FROM CUOTAS_JUGADOR WHERE ID = ?", 42L);
    }

    @Test
    void eliminarNoHaceNadaSiElIdEsNulo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        dao.eliminar(null);

        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarCuotaInsertaCuandoNoExisteTodavia() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        CuotaJugador cuota = new CuotaJugador();
        cuota.setJugadorId(1L);

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(dao.guardarCuota(cuota)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarCuotaActualizaCuandoYaExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        CuotaJugador cuota = new CuotaJugador();
        cuota.setId(5L);
        cuota.setJugadorId(1L);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(5L))).thenReturn(new CuotaJugador());
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(dao.guardarCuota(cuota)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(99L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerPorId(99L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaTodosLosCampos() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<CuotaJugador>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(3L))).thenReturn(new CuotaJugador());
        dao.obtenerPorId(3L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getLong("JUGADOR_ID")).thenReturn(1L);
        when(rs.getLong("CONCEPTO_PAGO_ID")).thenReturn(2L);
        when(rs.getString("PERIODO")).thenReturn("2026-01");
        when(rs.getBigDecimal("IMPORTE")).thenReturn(new java.math.BigDecimal("20.00"));
        when(rs.getString("ESTADO")).thenReturn("PENDIENTE");
        when(rs.getString("OBSERVACIONES")).thenReturn(null);

        CuotaJugador mapeada = captor.getValue().mapRow(rs, 0);

        assertThat(mapeada.getId()).isEqualTo(3L);
        assertThat(mapeada.getJugadorId()).isEqualTo(1L);
        assertThat(mapeada.getEstado()).isEqualTo("PENDIENTE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorJugadorFiltraPorJugador() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        List<CuotaJugador> esperado = List.of(new CuotaJugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerPorJugador(1L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEstadoFiltraPorEstado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        List<CuotaJugador> esperado = List.of(new CuotaJugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("PENDIENTE"))).thenReturn(esperado);

        assertThat(dao.obtenerPorEstado("PENDIENTE")).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        List<CuotaJugador> esperado = List.of(new CuotaJugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodos()).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorTemporadaFiltraPorTemporada() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        List<CuotaJugador> esperado = List.of(new CuotaJugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerPorTemporada(1L)).isSameAs(esperado);
    }

    @Test
    void actualizarEstadoEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        dao.actualizarEstado(1L);

        verify(jdbcTemplate).update(anyString(), eq(1L));
    }

    @Test
    void eliminarEnLoteNoHaceNadaSiLaListaEsNulaOVacia() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        dao.eliminarEnLote(null);
        dao.eliminarEnLote(List.of());

        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void eliminarEnLoteNoHaceNadaSiSoloHayIdsNulos() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        dao.eliminarEnLote(Arrays.asList((Long) null, null));

        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void eliminarEnLoteBorraPagosAntesQueLasCuotasYDeduplicaIds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuotaJugadorDaoImpl dao = new CuotaJugadorDaoImpl(jdbcTemplate);

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(2);

        dao.eliminarEnLote(Arrays.asList(1L, 1L, 2L, null));

        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).update(eq("DELETE FROM PAGOS WHERE CUOTA_JUGADOR_ID IN (?,?)"), any(Object[].class));
        inOrder.verify(jdbcTemplate).update(eq("DELETE FROM CUOTAS_JUGADOR WHERE ID IN (?,?)"), any(Object[].class));
    }
}
