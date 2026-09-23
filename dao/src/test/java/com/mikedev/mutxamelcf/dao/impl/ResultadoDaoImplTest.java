package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Resultado;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultadoDaoImplTest {

    @Test
    void actualizarResultadoEjecutaElUpdateConLaFecha() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultadoDaoImpl dao = new ResultadoDaoImpl(jdbcTemplate);

        Resultado resultado = new Resultado();
        resultado.setCategoria("SENIOR");
        resultado.setEquipo("Senior A");
        resultado.setRival("Rival CF");
        resultado.setResultado("2-1");
        resultado.setDia(new Date());
        resultado.setHora("18:00");
        resultado.setCampo("Campo Municipal");

        dao.actualizarResultado(resultado);

        verify(jdbcTemplate).update(anyString(), eq("Rival CF"), eq("2-1"), any(java.sql.Date.class), eq("18:00"),
                eq("Campo Municipal"), eq("SENIOR"), eq("Senior A"));
    }

    @Test
    void actualizarResultadoAceptaFechaNula() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultadoDaoImpl dao = new ResultadoDaoImpl(jdbcTemplate);

        Resultado resultado = new Resultado();
        resultado.setCategoria("SENIOR");
        resultado.setEquipo("Senior A");
        resultado.setDia(null);

        dao.actualizarResultado(resultado);

        verify(jdbcTemplate).update(anyString(), any(), any(), eq((Object) null), any(), any(), eq("SENIOR"),
                eq("Senior A"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerResultadosFiltraPorDeporte() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultadoDaoImpl dao = new ResultadoDaoImpl(jdbcTemplate);

        List<Resultado> esperado = List.of(new Resultado());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("F"))).thenReturn(esperado);

        assertThat(dao.obtenerResultados("F")).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerResultadoPrimerEquipoDevuelveElPrimeroDeLaLista() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultadoDaoImpl dao = new ResultadoDaoImpl(jdbcTemplate);

        Resultado esperado = new Resultado();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("F"), eq("Primer Equipo"), eq("Mutxamel CF")))
                .thenReturn(List.of(esperado));

        assertThat(dao.obtenerResultadoPrimerEquipo()).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerResultadoPrimerEquipoDevuelveNullSiNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultadoDaoImpl dao = new ResultadoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("F"), eq("Primer Equipo"), eq("Mutxamel CF")))
                .thenReturn(Collections.emptyList());

        assertThat(dao.obtenerResultadoPrimerEquipo()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaTodosLosCampos() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultadoDaoImpl dao = new ResultadoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Resultado>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture(), eq("F"))).thenReturn(List.of());
        dao.obtenerResultados("F");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CATEGORIA")).thenReturn("SENIOR");
        when(rs.getString("EQUIPO")).thenReturn("Senior A");
        when(rs.getString("RIVAL")).thenReturn("Rival CF");
        when(rs.getString("RESULTADO")).thenReturn("2-1");
        when(rs.getDate("DIA")).thenReturn(java.sql.Date.valueOf("2026-01-01"));
        when(rs.getString("HORA")).thenReturn("18:00");
        when(rs.getString("CAMPO")).thenReturn("Campo Municipal");

        Resultado mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getCategoria()).isEqualTo("SENIOR");
        assertThat(mapeado.getEquipo()).isEqualTo("Senior A");
        assertThat(mapeado.getRival()).isEqualTo("Rival CF");
        assertThat(mapeado.getResultado()).isEqualTo("2-1");
        assertThat(mapeado.getHora()).isEqualTo("18:00");
        assertThat(mapeado.getCampo()).isEqualTo("Campo Municipal");
    }
}
