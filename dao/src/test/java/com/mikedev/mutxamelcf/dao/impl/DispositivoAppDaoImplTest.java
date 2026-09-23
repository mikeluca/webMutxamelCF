package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.DispositivoApp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispositivoAppDaoImplTest {

    @Test
    void registrarUsaLaSecuenciaYGuardaElDispositivo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DispositivoAppDaoImpl dao = new DispositivoAppDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(99L);

        DispositivoApp dispositivo = new DispositivoApp();
        dispositivo.setUsuarioAppId(1L);
        dispositivo.setTokenFcm("token-abc");
        dispositivo.setPlataforma("ANDROID");

        dao.registrar(dispositivo);

        verify(jdbcTemplate).update(anyString(), eq(99L), eq(1L), eq("token-abc"), eq("ANDROID"), eq(1),
                any(Timestamp.class), any(Timestamp.class));
    }

    @Test
    void actualizarAccesoEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DispositivoAppDaoImpl dao = new DispositivoAppDaoImpl(jdbcTemplate);

        dao.actualizarAcceso(1L, "token-abc");

        verify(jdbcTemplate).update(anyString(), any(Timestamp.class), eq(1L), eq("token-abc"));
    }

    @Test
    void desactivarEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DispositivoAppDaoImpl dao = new DispositivoAppDaoImpl(jdbcTemplate);

        dao.desactivar(1L, "token-abc");

        verify(jdbcTemplate).update(anyString(), eq(1L), eq("token-abc"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorUsuarioYTokenDevuelveNullCuandoNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DispositivoAppDaoImpl dao = new DispositivoAppDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L), eq("token-abc")))
                .thenReturn(List.of());

        assertThat(dao.obtenerPorUsuarioYToken(1L, "token-abc")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorUsuarioYTokenMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DispositivoAppDaoImpl dao = new DispositivoAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<DispositivoApp>> captor = ArgumentCaptor.forClass(RowMapper.class);
        DispositivoApp esperado = new DispositivoApp();
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L), eq("token-abc")))
                .thenReturn(List.of(esperado));

        assertThat(dao.obtenerPorUsuarioYToken(1L, "token-abc")).isSameAs(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(5L);
        when(rs.getLong("USUARIO_APP_ID")).thenReturn(1L);
        when(rs.getString("TOKEN_FCM")).thenReturn("token-abc");
        when(rs.getString("PLATAFORMA")).thenReturn("ANDROID");
        when(rs.getInt("ACTIVO")).thenReturn(1);
        when(rs.getTimestamp("FECHA_REGISTRO")).thenReturn(Timestamp.valueOf(LocalDateTime.now()));
        when(rs.getTimestamp("FECHA_ULTIMO_ACCESO")).thenReturn(null);

        DispositivoApp mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(5L);
        assertThat(mapeado.getTokenFcm()).isEqualTo("token-abc");
        assertThat(mapeado.getFechaRegistro()).isNotNull();
        assertThat(mapeado.getFechaUltimoAcceso()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerActivosPorUsuarioDelegaEnJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DispositivoAppDaoImpl dao = new DispositivoAppDaoImpl(jdbcTemplate);

        List<DispositivoApp> esperado = List.of(new DispositivoApp());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerActivosPorUsuario(1L)).isSameAs(esperado);
    }

    @Test
    void desactivarTokenDeOtrosUsuariosEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DispositivoAppDaoImpl dao = new DispositivoAppDaoImpl(jdbcTemplate);

        dao.desactivarTokenDeOtrosUsuarios(1L, "token-abc");

        verify(jdbcTemplate).update(anyString(), eq("token-abc"), eq(1L));
    }
}
