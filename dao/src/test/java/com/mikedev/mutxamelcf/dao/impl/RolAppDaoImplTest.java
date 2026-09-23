package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.RolApp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RolAppDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorCodigoEncuentraElRol() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDaoImpl dao = new RolAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        ArgumentCaptor<ResultSetExtractor<RolApp>> rseCaptor = ArgumentCaptor.forClass(ResultSetExtractor.class);

        RolApp esperado = new RolApp();
        esperado.setCodigo("JUGADOR");
        when(jdbcTemplate.query(anyString(), pssCaptor.capture(), rseCaptor.capture())).thenReturn(esperado);

        assertThat(dao.obtenerPorCodigo("JUGADOR")).isSameAs(esperado);

        PreparedStatement ps = mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setString(1, "JUGADOR");

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("ID")).thenReturn(1);
        when(rs.getString("CODIGO")).thenReturn("JUGADOR");
        when(rs.getString("NOMBRE")).thenReturn("Jugador");

        RolApp mapeado = rseCaptor.getValue().extractData(rs);
        assertThat(mapeado.getCodigo()).isEqualTo("JUGADOR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorCodigoDevuelveNullCuandoNoHayFila() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDaoImpl dao = new RolAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<ResultSetExtractor<RolApp>> rseCaptor = ArgumentCaptor.forClass(ResultSetExtractor.class);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), rseCaptor.capture()))
                .thenReturn(null);

        assertThat(dao.obtenerPorCodigo("NO_EXISTE")).isNull();

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        assertThat(rseCaptor.getValue().extractData(rs)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdEncuentraElRol() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDaoImpl dao = new RolAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);

        RolApp esperado = new RolApp();
        when(jdbcTemplate.query(anyString(), pssCaptor.capture(), any(ResultSetExtractor.class)))
                .thenReturn(esperado);

        assertThat(dao.obtenerPorId(1)).isSameAs(esperado);

        PreparedStatement ps = mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setInt(1, 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDaoImpl dao = new RolAppDaoImpl(jdbcTemplate);

        List<RolApp> esperado = List.of(new RolApp());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodos()).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorUsuarioFiltraPorUsuario() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDaoImpl dao = new RolAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        List<RolApp> esperado = List.of(new RolApp());
        when(jdbcTemplate.query(anyString(), pssCaptor.capture(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerPorUsuario(7)).isSameAs(esperado);

        PreparedStatement ps = mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setInt(1, 7);
    }

    @Test
    void asignarRolEjecutaElInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDaoImpl dao = new RolAppDaoImpl(jdbcTemplate);

        dao.asignarRol(1, 2);

        verify(jdbcTemplate).update(anyString(), eq(1), eq(2));
    }

    @Test
    void eliminarRolEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDaoImpl dao = new RolAppDaoImpl(jdbcTemplate);

        dao.eliminarRol(1, 2);

        verify(jdbcTemplate).update(anyString(), eq(1), eq(2));
    }

    @Test
    void eliminarTodosLosRolesEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        RolAppDaoImpl dao = new RolAppDaoImpl(jdbcTemplate);

        dao.eliminarTodosLosRoles(1);

        verify(jdbcTemplate).update(anyString(), eq(1));
    }
}
