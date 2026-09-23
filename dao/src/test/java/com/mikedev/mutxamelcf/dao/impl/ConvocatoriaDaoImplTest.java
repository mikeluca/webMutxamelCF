package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Convocatoria;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
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

class ConvocatoriaDaoImplTest {

    @Test
    void guardarFijaElIdGeneradoTrasElInsert() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaDaoImpl dao = new ConvocatoriaDaoImpl(jdbcTemplate);

        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setEquipoId(1L);
        convocatoria.setRival("Rival CF");
        convocatoria.setCampo("Campo Municipal");
        convocatoria.setFechaPartido(LocalDate.of(2026, 3, 1));
        convocatoria.setHoraPartido("18:00");
        convocatoria.setHoraConvocatoria("17:00");
        convocatoria.setLugarConvocatoria("Vestuarios");
        convocatoria.setUsuarioEntrenadorId(9L);

        ArgumentCaptor<PreparedStatementCreator> pscCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<KeyHolder> keyHolderCaptor = ArgumentCaptor.forClass(KeyHolder.class);

        when(jdbcTemplate.update(pscCaptor.capture(), keyHolderCaptor.capture())).thenAnswer(invocation -> {
            keyHolderCaptor.getValue().getKeyList().add(Map.of("ID", 21L));
            return 1;
        });

        Convocatoria resultado = dao.guardar(convocatoria);

        assertThat(resultado.getId()).isEqualTo(21L);

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);
        pscCaptor.getValue().createPreparedStatement(connection);

        verify(ps).setLong(1, 1L);
        verify(ps).setString(2, "Rival CF");
        verify(ps).setLong(8, 9L);
    }

    @Test
    void guardarLanzaExcepcionSiNoSeGeneraId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaDaoImpl dao = new ConvocatoriaDaoImpl(jdbcTemplate);

        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setFechaPartido(LocalDate.of(2026, 3, 1));

        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenReturn(1);

        assertThatThrownBy(() -> dao.guardar(convocatoria)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void actualizarEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaDaoImpl dao = new ConvocatoriaDaoImpl(jdbcTemplate);

        Convocatoria convocatoria = new Convocatoria();
        convocatoria.setId(1L);
        convocatoria.setRival("Rival CF");

        dao.actualizar(convocatoria);

        verify(jdbcTemplate).update(anyString(), eq("Rival CF"), any(), any(), any(), any(), any(), eq(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullSiNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaDaoImpl dao = new ConvocatoriaDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(Collections.emptyList());

        assertThat(dao.obtenerPorId(1L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaTodosLosCamposIncluidasLasFechas() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaDaoImpl dao = new ConvocatoriaDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Convocatoria>> captor = ArgumentCaptor.forClass(RowMapper.class);
        Convocatoria esperado = new Convocatoria();
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(esperado));
        dao.obtenerPorId(1L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(1L);
        when(rs.getLong("EQUIPO_ID")).thenReturn(2L);
        when(rs.getString("RIVAL")).thenReturn("Rival CF");
        when(rs.getString("CAMPO")).thenReturn("Campo Municipal");
        when(rs.getDate("FECHA_PARTIDO")).thenReturn(java.sql.Date.valueOf("2026-03-01"));
        when(rs.getString("HORA_PARTIDO")).thenReturn("18:00");
        when(rs.getString("HORA_CONVOCATORIA")).thenReturn("17:00");
        when(rs.getString("LUGAR_CONVOCATORIA")).thenReturn("Vestuarios");
        when(rs.getLong("USUARIO_ENTRENADOR_ID")).thenReturn(9L);
        when(rs.getTimestamp("FECHA_CREACION")).thenReturn(Timestamp.valueOf("2026-01-01 10:00:00"));

        Convocatoria mapeada = captor.getValue().mapRow(rs, 0);

        assertThat(mapeada.getId()).isEqualTo(1L);
        assertThat(mapeada.getRival()).isEqualTo("Rival CF");
        assertThat(mapeada.getFechaPartido()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(mapeada.getFechaCreacion()).isNotNull();

        // Rama sin fecha ni fecha de creacion
        when(rs.getDate("FECHA_PARTIDO")).thenReturn(null);
        when(rs.getTimestamp("FECHA_CREACION")).thenReturn(null);

        Convocatoria mapeadaSinFechas = captor.getValue().mapRow(rs, 0);
        assertThat(mapeadaSinFechas.getFechaPartido()).isNull();
        assertThat(mapeadaSinFechas.getFechaCreacion()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEquipoFiltraPorEquipo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaDaoImpl dao = new ConvocatoriaDaoImpl(jdbcTemplate);

        List<Convocatoria> esperado = List.of(new Convocatoria());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(2L))).thenReturn(esperado);

        assertThat(dao.obtenerPorEquipo(2L)).isSameAs(esperado);
    }

    @Test
    void existePorEquipoYFechaDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaDaoImpl dao = new ConvocatoriaDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(2L), any())).thenReturn(1);

        assertThat(dao.existePorEquipoYFecha(2L, LocalDate.of(2026, 3, 1))).isTrue();
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ConvocatoriaDaoImpl dao = new ConvocatoriaDaoImpl(jdbcTemplate);

        dao.eliminar(1L);

        verify(jdbcTemplate).update(anyString(), eq(1L));
    }
}
