package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Pago;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PagoDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void guardarPagoInsertaYRecuperaElIdGenerado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        Pago pago = new Pago();
        pago.setCuotaJugadorId(10L);
        pago.setImporte(new BigDecimal("20.00"));

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        Pago ultimoInsertado = new Pago();
        ultimoInsertado.setId(55L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10L))).thenReturn(List.of(ultimoInsertado));

        boolean resultado = dao.guardarPago(pago);

        assertThat(resultado).isTrue();
        assertThat(pago.getId()).isEqualTo(55L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarPagoActualizaCuandoYaExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        Pago pago = new Pago();
        pago.setId(3L);
        pago.setCuotaJugadorId(10L);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(3L))).thenReturn(new Pago());
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(dao.guardarPago(pago)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(99L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerPorId(99L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaTodosLosCampos() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Pago>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(3L))).thenReturn(new Pago());
        dao.obtenerPorId(3L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getLong("CUOTA_JUGADOR_ID")).thenReturn(10L);
        when(rs.getBigDecimal("IMPORTE")).thenReturn(new BigDecimal("20.00"));
        when(rs.getString("METODO_PAGO")).thenReturn("EFECTIVO");
        when(rs.getString("REFERENCIA")).thenReturn("REF-1");
        when(rs.getString("OBSERVACIONES")).thenReturn("Sin observaciones");

        Pago mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getCuotaJugadorId()).isEqualTo(10L);
        assertThat(mapeado.getImporte()).isEqualByComparingTo("20.00");
        assertThat(mapeado.getMetodoPago()).isEqualTo("EFECTIVO");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorCuotaFiltraPorCuota() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        List<Pago> esperado = List.of(new Pago());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10L))).thenReturn(esperado);

        assertThat(dao.obtenerPorCuota(10L)).isSameAs(esperado);
    }

    @Test
    void obtenerPorCuotasDevuelveListaVaciaSiNoHayIds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        assertThat(dao.obtenerPorCuotas(null)).isEmpty();
        assertThat(dao.obtenerPorCuotas(List.of())).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorCuotasTroceaEnLotesDeNoveciento() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 1; i <= 950; i++) {
            ids.add(i);
        }

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(new Pago()));

        List<Pago> resultado = dao.obtenerPorCuotas(ids);

        // Dos lotes (900 + 50): dos llamadas a query, dos resultados acumulados
        assertThat(resultado).hasSize(2);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).query(anyString(), any(RowMapper.class),
                any(Object[].class));
    }

    @Test
    void obtenerTotalPagadoDevuelveCeroSiEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(10L))).thenReturn(null);

        assertThat(dao.obtenerTotalPagado(10L)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void obtenerTotalPagadoDevuelveElValorDevueltoPorLaBd() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), eq(10L)))
                .thenReturn(new BigDecimal("40.00"));

        assertThat(dao.obtenerTotalPagado(10L)).isEqualByComparingTo("40.00");
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PagoDaoImpl dao = new PagoDaoImpl(jdbcTemplate);

        dao.eliminar(2L);

        verify(jdbcTemplate).update(anyString(), eq(2L));
    }
}
