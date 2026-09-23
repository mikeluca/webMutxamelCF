package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.ConceptoPago;

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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConceptoPagoDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void guardarConceptoPagoInsertaCuandoNoTieneId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConceptoPagoDaoImpl dao = new ConceptoPagoDaoImpl(jdbcTemplate);

        ConceptoPago concepto = new ConceptoPago();
        concepto.setNombre("Cuota mensual");

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(dao.guardarConceptoPago(concepto)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarConceptoPagoActualizaCuandoYaExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConceptoPagoDaoImpl dao = new ConceptoPagoDaoImpl(jdbcTemplate);

        ConceptoPago concepto = new ConceptoPago();
        concepto.setId(4L);
        concepto.setNombre("Cuota mensual");

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(4L))).thenReturn(new ConceptoPago());
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(dao.guardarConceptoPago(concepto)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConceptoPagoDaoImpl dao = new ConceptoPagoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(99L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerPorId(99L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaTodosLosCampos() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConceptoPagoDaoImpl dao = new ConceptoPagoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<ConceptoPago>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(3L))).thenReturn(new ConceptoPago());
        dao.obtenerPorId(3L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getLong("TEMPORADA_ID")).thenReturn(1L);
        when(rs.getString("NOMBRE")).thenReturn("Cuota mensual");
        when(rs.getString("DESCRIPCION")).thenReturn("Descripcion");
        when(rs.getBigDecimal("IMPORTE")).thenReturn(new BigDecimal("20.00"));
        when(rs.getInt("ACTIVO")).thenReturn(1);

        ConceptoPago mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getTemporadaId()).isEqualTo(1L);
        assertThat(mapeado.getNombre()).isEqualTo("Cuota mensual");
        assertThat(mapeado.getImporte()).isEqualByComparingTo("20.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorTemporadaFiltraPorTemporada() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConceptoPagoDaoImpl dao = new ConceptoPagoDaoImpl(jdbcTemplate);

        List<ConceptoPago> esperado = List.of(new ConceptoPago());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerPorTemporada(1L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerActivosPorTemporadaFiltraPorTemporada() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConceptoPagoDaoImpl dao = new ConceptoPagoDaoImpl(jdbcTemplate);

        List<ConceptoPago> esperado = List.of(new ConceptoPago());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerActivosPorTemporada(1L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConceptoPagoDaoImpl dao = new ConceptoPagoDaoImpl(jdbcTemplate);

        List<ConceptoPago> esperado = List.of(new ConceptoPago());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodos()).isSameAs(esperado);
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConceptoPagoDaoImpl dao = new ConceptoPagoDaoImpl(jdbcTemplate);

        dao.eliminar(2L);

        verify(jdbcTemplate).update(anyString(), eq(2L));
    }
}
