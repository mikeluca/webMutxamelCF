package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.PreferenciasNotificacion;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreferenciasNotificacionDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorUsuarioDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PreferenciasNotificacionDaoImpl dao = new PreferenciasNotificacionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(List.of());

        assertThat(dao.obtenerPorUsuario(1L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorUsuarioMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PreferenciasNotificacionDaoImpl dao = new PreferenciasNotificacionDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<PreferenciasNotificacion>> captor = ArgumentCaptor.forClass(RowMapper.class);
        PreferenciasNotificacion esperado = new PreferenciasNotificacion();
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(esperado));

        assertThat(dao.obtenerPorUsuario(1L)).isSameAs(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(9L);
        when(rs.getLong("USUARIO_APP_ID")).thenReturn(1L);
        when(rs.getInt("NOTIFICACIONES_ACTIVADAS")).thenReturn(1);
        when(rs.getInt("NOTICIAS_ACTIVADAS")).thenReturn(1);
        when(rs.getInt("COMUNICACIONES_ACTIVADAS")).thenReturn(0);
        when(rs.getInt("MENSAJES_ACTIVADOS")).thenReturn(1);
        when(rs.getInt("RESULTADOS_ACTIVADOS")).thenReturn(0);

        PreferenciasNotificacion mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(9L);
        assertThat(mapeado.getUsuarioAppId()).isEqualTo(1L);
        assertThat(mapeado.getNotificacionesActivadas()).isEqualTo(1);
        assertThat(mapeado.getComunicacionesActivadas()).isEqualTo(0);
    }

    @Test
    void guardarObtieneLaSecuenciaYFijaElId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PreferenciasNotificacionDaoImpl dao = new PreferenciasNotificacionDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(77L);

        PreferenciasNotificacion preferencias = new PreferenciasNotificacion();
        preferencias.setUsuarioAppId(1L);

        dao.guardar(preferencias);

        assertThat(preferencias.getId()).isEqualTo(77L);
        verify(jdbcTemplate).update(anyString(), eq(77L), eq(1L), any(), any(), any(), any(), any());
    }

    @Test
    void actualizarEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PreferenciasNotificacionDaoImpl dao = new PreferenciasNotificacionDaoImpl(jdbcTemplate);

        PreferenciasNotificacion preferencias = new PreferenciasNotificacion();
        preferencias.setUsuarioAppId(1L);
        preferencias.setNotificacionesActivadas(1);

        dao.actualizar(preferencias);

        verify(jdbcTemplate).update(anyString(), eq(1), any(), any(), any(), any(), eq(1L));
    }
}
