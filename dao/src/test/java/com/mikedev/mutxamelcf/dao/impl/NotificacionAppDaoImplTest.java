package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.NotificacionApp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificacionAppDaoImplTest {

    @Test
    void guardarUsaLaSecuenciaYFijaElId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(50L);

        NotificacionApp notificacion = new NotificacionApp();
        notificacion.setUsuarioAppId(1L);
        notificacion.setTipo("NOTICIA");
        notificacion.setTitulo("Titulo");
        notificacion.setMensaje("Mensaje");
        notificacion.setFecha(LocalDateTime.now());

        Long id = dao.guardar(notificacion);

        assertThat(id).isEqualTo(50L);
        assertThat(notificacion.getId()).isEqualTo(50L);
        verify(jdbcTemplate).update(anyString(), eq(50L), eq(1L), eq("NOTICIA"), eq("Titulo"), eq("Mensaje"), any(),
                any(Timestamp.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullSiNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(Collections.emptyList());

        assertThat(dao.obtenerPorId(1L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaReferenciaNulaYNoNula() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<NotificacionApp>> captor = ArgumentCaptor.forClass(RowMapper.class);
        NotificacionApp esperado = new NotificacionApp();
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(esperado));
        dao.obtenerPorId(1L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(1L);
        when(rs.getLong("USUARIO_APP_ID")).thenReturn(2L);
        when(rs.getString("TIPO")).thenReturn("NOTICIA");
        when(rs.getString("TITULO")).thenReturn("Titulo");
        when(rs.getString("MENSAJE")).thenReturn("Mensaje");
        when(rs.getLong("REFERENCIA_ID")).thenReturn(9L);
        when(rs.wasNull()).thenReturn(false);
        when(rs.getTimestamp("FECHA")).thenReturn(Timestamp.valueOf("2026-01-01 10:00:00"));
        when(rs.getInt("LEIDA")).thenReturn(1);

        NotificacionApp conReferencia = captor.getValue().mapRow(rs, 0);
        assertThat(conReferencia.getReferenciaId()).isEqualTo(9L);
        assertThat(conReferencia.getFecha()).isNotNull();

        when(rs.wasNull()).thenReturn(true);
        NotificacionApp sinReferencia = captor.getValue().mapRow(rs, 0);
        assertThat(sinReferencia.getReferenciaId()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorUsuarioFiltraPorUsuario() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        List<NotificacionApp> esperado = List.of(new NotificacionApp());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerPorUsuario(1L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerNoLeidasFiltraPorUsuario() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        List<NotificacionApp> esperado = List.of(new NotificacionApp());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerNoLeidas(1L)).isSameAs(esperado);
    }

    @Test
    void marcarComoLeidaEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        dao.marcarComoLeida(1L, 2L);

        verify(jdbcTemplate).update(anyString(), eq(1L), eq(2L));
    }

    @Test
    void marcarTodasComoLeidasEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        dao.marcarTodasComoLeidas(1L);

        verify(jdbcTemplate).update(anyString(), eq(1L));
    }

    @Test
    void marcarLeidasPorReferenciasNoHaceNadaSiLaListaEsVaciaONula() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        dao.marcarLeidasPorReferencias(1L, null);
        dao.marcarLeidasPorReferencias(1L, List.of());

        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void marcarLeidasPorReferenciasConstruyeElInClauseCorrectamente() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        dao.marcarLeidasPorReferencias(1L, List.of(10L, 20L, 30L));

        verify(jdbcTemplate).update(anyString(), eq(1L), eq(10L), eq(20L), eq(30L));
    }

    @Test
    void contarNoLeidasDevuelveCeroSiEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(null);

        assertThat(dao.contarNoLeidas(1L)).isZero();
    }

    @Test
    void contarNoLeidasDevuelveElValor() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(5);

        assertThat(dao.contarNoLeidas(1L)).isEqualTo(5);
    }

    @Test
    void contarComunicacionesNoLeidasDevuelveCeroSiEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(null);

        assertThat(dao.contarComunicacionesNoLeidas(1L)).isZero();
    }

    @Test
    void contarComunicacionesNoLeidasDevuelveElValor() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NotificacionAppDaoImpl dao = new NotificacionAppDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(3);

        assertThat(dao.contarComunicacionesNoLeidas(1L)).isEqualTo(3);
    }
}
