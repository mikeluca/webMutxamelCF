package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Entrenamiento;

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

class EntrenamientoDaoImplTest {

    @Test
    void guardarFijaElIdGeneradoTrasElInsert() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        Entrenamiento entrenamiento = new Entrenamiento();
        entrenamiento.setEquipoId(1L);
        entrenamiento.setFecha(LocalDate.of(2026, 3, 1));
        entrenamiento.setUsuarioEntrenadorId(9L);
        entrenamiento.setSesionEntrenamientoId(77L);

        ArgumentCaptor<PreparedStatementCreator> pscCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<KeyHolder> keyHolderCaptor = ArgumentCaptor.forClass(KeyHolder.class);

        when(jdbcTemplate.update(pscCaptor.capture(), keyHolderCaptor.capture())).thenAnswer(invocation -> {
            keyHolderCaptor.getValue().getKeyList().add(Map.of("ID", 12L));
            return 1;
        });

        Entrenamiento resultado = dao.guardar(entrenamiento);

        assertThat(resultado.getId()).isEqualTo(12L);

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);
        pscCaptor.getValue().createPreparedStatement(connection);

        verify(ps).setLong(1, 1L);
        verify(ps).setLong(3, 9L);
        verify(ps).setLong(4, 77L);
    }

    @Test
    void guardarDejaNullElSesionEntrenamientoIdSiNoSeIndica() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        Entrenamiento entrenamiento = new Entrenamiento();
        entrenamiento.setEquipoId(1L);
        entrenamiento.setFecha(LocalDate.of(2026, 3, 1));
        entrenamiento.setUsuarioEntrenadorId(9L);

        ArgumentCaptor<PreparedStatementCreator> pscCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<KeyHolder> keyHolderCaptor = ArgumentCaptor.forClass(KeyHolder.class);

        when(jdbcTemplate.update(pscCaptor.capture(), keyHolderCaptor.capture())).thenAnswer(invocation -> {
            keyHolderCaptor.getValue().getKeyList().add(Map.of("ID", 12L));
            return 1;
        });

        dao.guardar(entrenamiento);

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);
        pscCaptor.getValue().createPreparedStatement(connection);

        verify(ps).setNull(eq(4), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void guardarLanzaExcepcionSiNoSeGeneraId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        Entrenamiento entrenamiento = new Entrenamiento();
        entrenamiento.setFecha(LocalDate.of(2026, 3, 1));

        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenReturn(1);

        assertThatThrownBy(() -> dao.guardar(entrenamiento)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void actualizarEjecutaElUpdate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        Entrenamiento entrenamiento = new Entrenamiento();
        entrenamiento.setId(1L);
        entrenamiento.setFecha(LocalDate.of(2026, 3, 2));

        dao.actualizar(entrenamiento);

        verify(jdbcTemplate).update(anyString(), eq(LocalDate.of(2026, 3, 2)), eq(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullSiNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(Collections.emptyList());

        assertThat(dao.obtenerPorId(1L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaTodosLosCamposIncluidasLasFechas() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Entrenamiento>> captor = ArgumentCaptor.forClass(RowMapper.class);
        Entrenamiento esperado = new Entrenamiento();
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(esperado));
        dao.obtenerPorId(1L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(1L);
        when(rs.getLong("EQUIPO_ID")).thenReturn(2L);
        when(rs.getDate("FECHA")).thenReturn(java.sql.Date.valueOf("2026-03-01"));
        when(rs.getLong("USUARIO_ENTRENADOR_ID")).thenReturn(9L);
        when(rs.getLong("SESION_ENTRENAMIENTO_ID")).thenReturn(77L);
        when(rs.getTimestamp("FECHA_CREACION")).thenReturn(Timestamp.valueOf("2026-01-01 10:00:00"));

        Entrenamiento mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(1L);
        assertThat(mapeado.getFecha()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(mapeado.getSesionEntrenamientoId()).isEqualTo(77L);
        assertThat(mapeado.getFechaCreacion()).isNotNull();

        when(rs.getDate("FECHA")).thenReturn(null);
        when(rs.getTimestamp("FECHA_CREACION")).thenReturn(null);
        when(rs.getLong("SESION_ENTRENAMIENTO_ID")).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);

        Entrenamiento mapeadoSinFechas = captor.getValue().mapRow(rs, 0);
        assertThat(mapeadoSinFechas.getFecha()).isNull();
        assertThat(mapeadoSinFechas.getFechaCreacion()).isNull();
        assertThat(mapeadoSinFechas.getSesionEntrenamientoId()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorSesionEntrenamientoIdDevuelveNullSiNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(30L))).thenReturn(Collections.emptyList());

        assertThat(dao.obtenerPorSesionEntrenamientoId(30L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorSesionEntrenamientoIdDevuelveElResultado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        Entrenamiento esperado = new Entrenamiento();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(30L))).thenReturn(List.of(esperado));

        assertThat(dao.obtenerPorSesionEntrenamientoId(30L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEquipoFiltraPorEquipo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        List<Entrenamiento> esperado = List.of(new Entrenamiento());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(2L))).thenReturn(esperado);

        assertThat(dao.obtenerPorEquipo(2L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEquipoOrdenaPorCercaniaAHoyNoSoloPorFechaDescendente() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), any(RowMapper.class), eq(2L))).thenReturn(List.of());

        dao.obtenerPorEquipo(2L);

        // Con "ORDER BY FECHA DESC" a secas, el entrenamiento generado
        // mas lejos en el futuro (las sesiones se generan hasta 2 meses
        // vista) quedaba arriba del todo, enterrando el de hoy/reciente.
        assertThat(sqlCaptor.getValue()).contains("ABS(FECHA - TRUNC(SYSDATE))");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEquipoEnRangoFiltraPorLasDosFechas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        LocalDate desde = LocalDate.of(2026, 10, 8);
        LocalDate hasta = LocalDate.of(2026, 10, 22);

        dao.obtenerPorEquipoEnRango(2L, desde, hasta);

        assertThat(sqlCaptor.getValue())
                .contains("FECHA >= ?")
                .contains("FECHA <= ?")
                .contains("ABS(FECHA - TRUNC(SYSDATE))");

        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue()).containsExactly(
                2L, java.sql.Date.valueOf(desde), java.sql.Date.valueOf(hasta));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEquipoEnRangoConUnSoloExtremoSoloAplicaEseFiltro() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        dao.obtenerPorEquipoEnRango(2L, null, LocalDate.of(2026, 10, 7));

        assertThat(sqlCaptor.getValue()).contains("FECHA <= ?").doesNotContain("FECHA >= ?");
    }

    @Test
    void existeDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(1);

        assertThat(dao.existe(1L)).isTrue();
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EntrenamientoDaoImpl dao = new EntrenamientoDaoImpl(jdbcTemplate);

        dao.eliminar(1L);

        verify(jdbcTemplate).update(anyString(), eq(1L));
    }
}
