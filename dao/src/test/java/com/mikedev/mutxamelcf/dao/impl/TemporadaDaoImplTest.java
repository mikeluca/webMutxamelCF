package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Temporada;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporadaDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void guardarTemporadaInsertaCuandoNoTieneId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        Temporada temporada = new Temporada();
        temporada.setNombre("2025/2026");

        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

        assertThat(dao.guardarTemporada(temporada)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarTemporadaActualizaCuandoYaExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        Temporada temporada = new Temporada();
        temporada.setId(1L);
        temporada.setNombre("2025/2026");

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(1L))).thenReturn(new Temporada());
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(dao.guardarTemporada(temporada)).isTrue();
    }

    @Test
    void desactivarOtrasTemporadasConIdActualizaTodasMenosEsa() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        dao.desactivarOtrasTemporadas(5L);

        verify(jdbcTemplate).update(anyString(), eq(5L));
    }

    @Test
    void desactivarOtrasTemporadasSinIdActualizaTodas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        dao.desactivarOtrasTemporadas(null);

        verify(jdbcTemplate).update(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(99L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerPorId(99L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaTodosLosCampos() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Temporada>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(3L))).thenReturn(new Temporada());
        dao.obtenerPorId(3L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getString("NOMBRE")).thenReturn("2025/2026");
        when(rs.getDate("FECHA_INICIO")).thenReturn(java.sql.Date.valueOf("2025-09-01"));
        when(rs.getDate("FECHA_FIN")).thenReturn(java.sql.Date.valueOf("2026-06-30"));
        when(rs.getInt("ACTIVA")).thenReturn(1);

        Temporada mapeada = captor.getValue().mapRow(rs, 0);

        assertThat(mapeada.getId()).isEqualTo(3L);
        assertThat(mapeada.getNombre()).isEqualTo("2025/2026");
        assertThat(mapeada.getActiva()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTemporadaActivaDevuelveNullCuandoNoHayNinguna() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerTemporadaActiva()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTemporadaActivaDevuelveLaEncontrada() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        Temporada esperada = new Temporada();
        esperada.setId(1L);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class))).thenReturn(esperada);

        assertThat(dao.obtenerTemporadaActiva()).isSameAs(esperada);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        List<Temporada> esperado = List.of(new Temporada());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodos()).isSameAs(esperado);
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TemporadaDaoImpl dao = new TemporadaDaoImpl(jdbcTemplate);

        dao.eliminar(2L);

        verify(jdbcTemplate).update(anyString(), eq(2L));
    }
}
