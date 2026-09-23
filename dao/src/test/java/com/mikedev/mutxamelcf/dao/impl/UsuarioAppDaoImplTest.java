package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.UsuarioApp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsuarioAppDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEmailEncuentraElUsuario() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        ArgumentCaptor<ResultSetExtractor<UsuarioApp>> rseCaptor = ArgumentCaptor.forClass(ResultSetExtractor.class);

        UsuarioApp esperado = new UsuarioApp();
        when(jdbcTemplate.query(anyString(), pssCaptor.capture(), rseCaptor.capture())).thenReturn(esperado);

        assertThat(dao.obtenerPorEmail("test@example.com")).isSameAs(esperado);

        PreparedStatement ps = mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setString(1, "test@example.com");

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getInt("ID")).thenReturn(1);
        when(rs.getString("EMAIL")).thenReturn("test@example.com");
        when(rs.getString("PASSWORD_HASH")).thenReturn("$2a$10$hash");
        when(rs.getInt("ACTIVO")).thenReturn(1);
        when(rs.getTimestamp("FECHA_ALTA")).thenReturn(Timestamp.valueOf("2026-01-01 00:00:00"));
        when(rs.getString("TOKEN_ACTIVACION")).thenReturn(null);
        when(rs.getInt("INTENTOS_ACTIVACION")).thenReturn(0);

        UsuarioApp mapeado = rseCaptor.getValue().extractData(rs);
        assertThat(mapeado.getId()).isEqualTo(1);
        assertThat(mapeado.getEmail()).isEqualTo("test@example.com");
        assertThat(mapeado.isActivo()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEmailDevuelveNullCuandoNoHayFila() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<ResultSetExtractor<UsuarioApp>> rseCaptor = ArgumentCaptor.forClass(ResultSetExtractor.class);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), rseCaptor.capture()))
                .thenReturn(null);

        assertThat(dao.obtenerPorEmail("no@example.com")).isNull();

        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        assertThat(rseCaptor.getValue().extractData(rs)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdUsaElIdEnLaConsulta() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        UsuarioApp esperado = new UsuarioApp();
        when(jdbcTemplate.query(anyString(), pssCaptor.capture(), any(ResultSetExtractor.class)))
                .thenReturn(esperado);

        assertThat(dao.obtenerPorId(7)).isSameAs(esperado);

        PreparedStatement ps = mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setInt(1, 7);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorTokenActivacionUsaElTokenEnLaConsulta() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<PreparedStatementSetter> pssCaptor = ArgumentCaptor.forClass(PreparedStatementSetter.class);
        UsuarioApp esperado = new UsuarioApp();
        when(jdbcTemplate.query(anyString(), pssCaptor.capture(), any(ResultSetExtractor.class)))
                .thenReturn(esperado);

        assertThat(dao.obtenerPorTokenActivacion("token-hash")).isSameAs(esperado);

        PreparedStatement ps = mock(PreparedStatement.class);
        pssCaptor.getValue().setValues(ps);
        verify(ps).setString(1, "token-hash");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarTodosMapeaLaListaCompleta() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<UsuarioApp>> captor = ArgumentCaptor.forClass(RowMapper.class);
        UsuarioApp esperado = new UsuarioApp();
        when(jdbcTemplate.query(anyString(), captor.capture())).thenReturn(List.of(esperado));

        assertThat(dao.listarTodos()).containsExactly(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("ID")).thenReturn(2);
        when(rs.getString("EMAIL")).thenReturn("otro@example.com");
        when(rs.getInt("ACTIVO")).thenReturn(0);

        UsuarioApp mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getId()).isEqualTo(2);
        assertThat(mapeado.isActivo()).isFalse();
    }

    @Test
    void guardarInsertaYRecuperaElUsuarioPorEmail() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        UsuarioApp usuario = new UsuarioApp();
        usuario.setEmail("test@example.com");
        usuario.setPasswordHash(null);
        usuario.setActivo(false);

        UsuarioApp guardado = new UsuarioApp();
        guardado.setId(9);

        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.PreparedStatementSetter.class),
                any(ResultSetExtractor.class))).thenReturn(guardado);

        int idGenerado = dao.guardar(usuario);

        assertThat(idGenerado).isEqualTo(9);
        verify(jdbcTemplate).update(anyString(), eq("test@example.com"), eq(null), eq(0), any(), any(), any());
    }

    @Test
    void guardarLanzaExcepcionSiNoEncuentraElUsuarioRecienCreado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        UsuarioApp usuario = new UsuarioApp();
        usuario.setEmail("test@example.com");

        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.PreparedStatementSetter.class),
                any(ResultSetExtractor.class))).thenReturn(null);

        assertThatThrownBy(() -> dao.guardar(usuario)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void actualizarPasswordEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        dao.actualizarPassword(1, "$2a$10$nuevoHash");

        verify(jdbcTemplate).update(anyString(), eq("$2a$10$nuevoHash"), eq(1));
    }

    @Test
    void activarUsuarioEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        dao.activarUsuario(1);

        verify(jdbcTemplate).update(anyString(), eq(1));
    }

    @Test
    void desactivarUsuarioEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        dao.desactivarUsuario(1);

        verify(jdbcTemplate).update(anyString(), eq(1));
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        dao.eliminar(1);

        verify(jdbcTemplate).update(anyString(), eq(1));
    }

    @Test
    void actualizarUltimoAccesoEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        dao.actualizarUltimoAcceso(1);

        verify(jdbcTemplate).update(anyString(), eq(1));
    }

    @Test
    void actualizarTokenActivacionEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        Timestamp expiracion = Timestamp.valueOf("2026-01-01 00:00:00");
        dao.actualizarTokenActivacion(1, "hash", expiracion);

        verify(jdbcTemplate).update(anyString(), eq("hash"), eq(expiracion), eq(1));
    }

    @Test
    void incrementarIntentosActivacionEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        dao.incrementarIntentosActivacion(1);

        verify(jdbcTemplate).update(anyString(), eq(1));
    }

    @Test
    void invalidarTokenActivacionEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UsuarioAppDaoImpl dao = new UsuarioAppDaoImpl(jdbcTemplate);

        dao.invalidarTokenActivacion(1);

        verify(jdbcTemplate).update(anyString(), eq(1));
    }
}
