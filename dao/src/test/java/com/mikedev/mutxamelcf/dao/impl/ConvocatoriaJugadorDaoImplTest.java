package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;

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

class ConvocatoriaJugadorDaoImplTest {

    @Test
    void guardarFijaElIdGeneradoTrasElInsert() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaJugadorDaoImpl dao = new ConvocatoriaJugadorDaoImpl(jdbcTemplate);

        ConvocatoriaJugador convocatoriaJugador = new ConvocatoriaJugador();
        convocatoriaJugador.setConvocatoriaId(1L);
        convocatoriaJugador.setJugadorId(2L);

        ArgumentCaptor<PreparedStatementCreator> pscCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<KeyHolder> keyHolderCaptor = ArgumentCaptor.forClass(KeyHolder.class);

        when(jdbcTemplate.update(pscCaptor.capture(), keyHolderCaptor.capture())).thenAnswer(invocation -> {
            keyHolderCaptor.getValue().getKeyList().add(Map.of("ID", 33L));
            return 1;
        });

        ConvocatoriaJugador resultado = dao.guardar(convocatoriaJugador);

        assertThat(resultado.getId()).isEqualTo(33L);

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);
        pscCaptor.getValue().createPreparedStatement(connection);

        verify(ps).setLong(1, 1L);
        verify(ps).setLong(2, 2L);
    }

    @Test
    void guardarLanzaExcepcionSiNoSeGeneraId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaJugadorDaoImpl dao = new ConvocatoriaJugadorDaoImpl(jdbcTemplate);

        ConvocatoriaJugador convocatoriaJugador = new ConvocatoriaJugador();
        convocatoriaJugador.setConvocatoriaId(1L);
        convocatoriaJugador.setJugadorId(2L);

        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenReturn(1);

        assertThatThrownBy(() -> dao.guardar(convocatoriaJugador))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorConvocatoriaFiltraPorConvocatoria() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaJugadorDaoImpl dao = new ConvocatoriaJugadorDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<ConvocatoriaJugador>> captor = ArgumentCaptor.forClass(RowMapper.class);
        ConvocatoriaJugador esperado = new ConvocatoriaJugador();
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(esperado));

        assertThat(dao.obtenerPorConvocatoria(1L)).containsExactly(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getLong("CONVOCATORIA_ID")).thenReturn(1L);
        when(rs.getLong("JUGADOR_ID")).thenReturn(2L);

        ConvocatoriaJugador mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getConvocatoriaId()).isEqualTo(1L);
        assertThat(mapeado.getJugadorId()).isEqualTo(2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorConvocatoriaYJugadorDevuelveNullSiNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaJugadorDaoImpl dao = new ConvocatoriaJugadorDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L), eq(2L)))
                .thenReturn(Collections.emptyList());

        assertThat(dao.obtenerPorConvocatoriaYJugador(1L, 2L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorConvocatoriaYJugadorDevuelveElPrimerResultado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaJugadorDaoImpl dao = new ConvocatoriaJugadorDaoImpl(jdbcTemplate);

        ConvocatoriaJugador esperado = new ConvocatoriaJugador();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L), eq(2L)))
                .thenReturn(List.of(esperado));

        assertThat(dao.obtenerPorConvocatoriaYJugador(1L, 2L)).isSameAs(esperado);
    }

    @Test
    void eliminarPorConvocatoriaEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaJugadorDaoImpl dao = new ConvocatoriaJugadorDaoImpl(jdbcTemplate);

        dao.eliminarPorConvocatoria(1L);

        verify(jdbcTemplate).update(anyString(), eq(1L));
    }

    @Test
    void existePorJugadorDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaJugadorDaoImpl dao = new ConvocatoriaJugadorDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(2L))).thenReturn(1);

        assertThat(dao.existePorJugador(2L)).isTrue();
    }
}
