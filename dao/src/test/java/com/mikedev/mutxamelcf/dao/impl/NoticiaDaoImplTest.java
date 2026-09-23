package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Noticia;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticiaDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void guardarNoticiaInsertaCuandoNoTieneId() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NoticiaDaoImpl dao = new NoticiaDaoImpl(jdbcTemplate);

        Noticia noticia = new Noticia();
        noticia.setTitulo("Titulo");
        noticia.setContenido("Contenido");
        noticia.setFecha(new java.util.Date());
        noticia.setImagen(new byte[] { 1, 2 });

        ArgumentCaptor<PreparedStatementCreator> pscCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<KeyHolder> keyHolderCaptor = ArgumentCaptor.forClass(KeyHolder.class);

        when(jdbcTemplate.update(pscCaptor.capture(), keyHolderCaptor.capture())).thenAnswer(invocation -> {
            keyHolderCaptor.getValue().getKeyList().add(Map.of("id", 7));
            return 1;
        });

        boolean resultado = dao.guardarNoticia(noticia);

        assertThat(resultado).isTrue();
        assertThat(noticia.getId()).isEqualTo(7);

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);

        pscCaptor.getValue().createPreparedStatement(connection);

        verify(ps).setString(1, "Titulo");
        verify(ps).setString(2, "Contenido");
        verify(ps).setBytes(4, new byte[] { 1, 2 });
    }

    @Test
    void guardarNoticiaActualizaCuandoYaTieneId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NoticiaDaoImpl dao = new NoticiaDaoImpl(jdbcTemplate);

        Noticia noticia = new Noticia();
        noticia.setId(3);
        noticia.setTitulo("Titulo editado");
        noticia.setContenido("Contenido editado");
        noticia.setImagen(new byte[] { 9 });

        when(jdbcTemplate.update(anyString(), eq("Titulo editado"), eq("Contenido editado"), eq(new byte[] { 9 }),
                eq(3))).thenReturn(1);

        assertThat(dao.guardarNoticia(noticia)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerNoticiaPorIdDelegaEnJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NoticiaDaoImpl dao = new NoticiaDaoImpl(jdbcTemplate);

        Noticia esperada = new Noticia();
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(5))).thenReturn(esperada);

        assertThat(dao.obtenerNoticiaPorId(5)).isSameAs(esperada);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowConvierteElBlobEnBytesYManejaElBlobNulo() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NoticiaDaoImpl dao = new NoticiaDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Noticia>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(5))).thenReturn(new Noticia());
        dao.obtenerNoticiaPorId(5);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("id")).thenReturn(5);
        when(rs.getString("titulo")).thenReturn("Titulo");
        when(rs.getString("contenido")).thenReturn("Contenido");
        when(rs.getDate("fecha")).thenReturn(Date.valueOf("2026-01-01"));

        Blob blob = mock(Blob.class);
        when(blob.length()).thenReturn(3L);
        when(blob.getBytes(1, 3)).thenReturn(new byte[] { 1, 2, 3 });
        when(rs.getBlob("imagen")).thenReturn(blob);

        Noticia conImagen = captor.getValue().mapRow(rs, 0);
        assertThat(conImagen.getTitulo()).isEqualTo("Titulo");
        assertThat(conImagen.getImagen()).containsExactly(1, 2, 3);

        when(rs.getBlob("imagen")).thenReturn(null);
        Noticia sinImagen = captor.getValue().mapRow(rs, 0);
        assertThat(sinImagen.getImagen()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerNoticiasParaMostrarLimitaAlPrimerCuatro() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NoticiaDaoImpl dao = new NoticiaDaoImpl(jdbcTemplate);

        List<Noticia> esperado = List.of(new Noticia());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerNoticiasParaMostrar()).isSameAs(esperado);
    }

    @Test
    void eliminarNoticiaEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NoticiaDaoImpl dao = new NoticiaDaoImpl(jdbcTemplate);

        dao.eliminarNoticia(4);

        verify(jdbcTemplate).update(anyString(), eq(4));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodasDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NoticiaDaoImpl dao = new NoticiaDaoImpl(jdbcTemplate);

        List<Noticia> esperado = List.of(new Noticia(), new Noticia());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodas()).isSameAs(esperado);
    }
}
