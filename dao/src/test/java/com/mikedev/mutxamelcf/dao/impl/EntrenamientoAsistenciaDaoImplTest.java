package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.EntrenamientoAsistencia;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntrenamientoAsistenciaDaoImplTest {

    @Test
    void guardarFijaElIdGeneradoTrasElInsert() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoAsistenciaDaoImpl dao = new EntrenamientoAsistenciaDaoImpl(jdbcTemplate);

        EntrenamientoAsistencia asistencia = new EntrenamientoAsistencia();
        asistencia.setEntrenamientoId(1L);
        asistencia.setJugadorId(2L);
        asistencia.setEstado("CONFIRMADA");

        ArgumentCaptor<PreparedStatementCreator> pscCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<KeyHolder> keyHolderCaptor = ArgumentCaptor.forClass(KeyHolder.class);

        when(jdbcTemplate.update(pscCaptor.capture(), keyHolderCaptor.capture())).thenAnswer(invocation -> {
            keyHolderCaptor.getValue().getKeyList().add(Map.of("ID", 44L));
            return 1;
        });

        EntrenamientoAsistencia resultado = dao.guardar(asistencia);

        assertThat(resultado.getId()).isEqualTo(44L);

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);
        pscCaptor.getValue().createPreparedStatement(connection);

        verify(ps).setLong(1, 1L);
        verify(ps).setLong(2, 2L);
        verify(ps).setString(3, "CONFIRMADA");
    }

    @Test
    void guardarLanzaExcepcionSiNoSeGeneraId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoAsistenciaDaoImpl dao = new EntrenamientoAsistenciaDaoImpl(jdbcTemplate);

        EntrenamientoAsistencia asistencia = new EntrenamientoAsistencia();
        asistencia.setEntrenamientoId(1L);
        asistencia.setJugadorId(2L);

        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenReturn(1);

        assertThatThrownBy(() -> dao.guardar(asistencia)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEntrenamientoFiltraPorEntrenamiento() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoAsistenciaDaoImpl dao = new EntrenamientoAsistenciaDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<EntrenamientoAsistencia>> captor = ArgumentCaptor.forClass(RowMapper.class);
        EntrenamientoAsistencia esperado = new EntrenamientoAsistencia();
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(esperado));

        assertThat(dao.obtenerPorEntrenamiento(1L)).containsExactly(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getLong("ENTRENAMIENTO_ID")).thenReturn(1L);
        when(rs.getLong("JUGADOR_ID")).thenReturn(2L);
        when(rs.getString("ESTADO")).thenReturn("CONFIRMADA");

        EntrenamientoAsistencia mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getEstado()).isEqualTo("CONFIRMADA");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEntrenamientoYJugadorDevuelveNullSiNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoAsistenciaDaoImpl dao = new EntrenamientoAsistenciaDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L), eq(2L)))
                .thenReturn(Collections.emptyList());

        assertThat(dao.obtenerPorEntrenamientoYJugador(1L, 2L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEntrenamientoYJugadorDevuelveElPrimerResultado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoAsistenciaDaoImpl dao = new EntrenamientoAsistenciaDaoImpl(jdbcTemplate);

        EntrenamientoAsistencia esperado = new EntrenamientoAsistencia();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L), eq(2L)))
                .thenReturn(List.of(esperado));

        assertThat(dao.obtenerPorEntrenamientoYJugador(1L, 2L)).isSameAs(esperado);
    }

    @Test
    void eliminarPorEntrenamientoEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoAsistenciaDaoImpl dao = new EntrenamientoAsistenciaDaoImpl(jdbcTemplate);

        dao.eliminarPorEntrenamiento(1L);

        verify(jdbcTemplate).update(anyString(), eq(1L));
    }

    @Test
    void existePorJugadorDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoAsistenciaDaoImpl dao = new EntrenamientoAsistenciaDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(2L))).thenReturn(1);

        assertThat(dao.existePorJugador(2L)).isTrue();
    }
}
