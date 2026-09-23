package com.mikedev.mutxamelcf.dao.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipoGestionDaoImplTest {

    @Test
    void existeEquipoDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);

        assertThat(dao.existeEquipo(1L)).isTrue();
    }

    @Test
    void existeEquipoDevuelveFalseCuandoElCountEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(null);

        assertThat(dao.existeEquipo(1L)).isFalse();
    }

    @Test
    void puedeGestionarEquipoDevuelveTrueCuandoHayPermiso() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L), eq(2L), eq(2L))).thenReturn(1);

        assertThat(dao.puedeGestionarEquipo(2L, 1L)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerJugadoresPorEquipoMapeaLosIds() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Long>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(10L, 20L));

        assertThat(dao.obtenerJugadoresPorEquipo(1L)).containsExactly(10L, 20L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(30L);
        assertThat(captor.getValue().mapRow(rs, 0)).isEqualTo(30L);
    }

    @Test
    void obtenerNombreEquipoDelegaEnJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(1L))).thenReturn("Senior A");

        assertThat(dao.obtenerNombreEquipo(1L)).isEqualTo("Senior A");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerCoordinadoresDelegaEnJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(1L, 2L));

        assertThat(dao.obtenerCoordinadores()).containsExactly(1L, 2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerUsuariosPorJugadorFiltraPorJugador() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5L))).thenReturn(List.of(1L));

        assertThat(dao.obtenerUsuariosPorJugador(5L)).containsExactly(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerUsuariosFamiliaresPorJugadorFiltraPorJugador() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5L))).thenReturn(List.of(2L));

        assertThat(dao.obtenerUsuariosFamiliaresPorJugador(5L)).containsExactly(2L);
    }

    @Test
    void obtenerNombreJugadorDelegaEnJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(5L))).thenReturn("Juan Perez");

        assertThat(dao.obtenerNombreJugador(5L)).isEqualTo("Juan Perez");
    }

    @Test
    void perteneceJugadorAEquipoDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoGestionDaoImpl dao = new EquipoGestionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(5L), eq(1L))).thenReturn(1);

        assertThat(dao.perteneceJugadorAEquipo(5L, 1L)).isTrue();
    }
}
