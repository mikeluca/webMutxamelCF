package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Partido;

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
import java.sql.Types;
import java.util.Collections;
import java.util.Date;
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

class PartidoDaoImplTest {

    @Test
    void crearFijaElIdGeneradoTrasElInsert() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        Partido partido = new Partido();
        partido.setEquipoId(1L);
        partido.setRival("Rival CF");
        partido.setDia(Date.from(java.time.LocalDate.of(2026, 3, 1)
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        partido.setHora("18:00");
        partido.setCampo("Campo Municipal");
        partido.setTipo("COPA");

        ArgumentCaptor<PreparedStatementCreator> pscCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<KeyHolder> keyHolderCaptor = ArgumentCaptor.forClass(KeyHolder.class);

        when(jdbcTemplate.update(pscCaptor.capture(), keyHolderCaptor.capture())).thenAnswer(invocation -> {
            keyHolderCaptor.getValue().getKeyList().add(Map.of("ID", 12L));
            return 1;
        });

        Partido resultado = dao.crear(partido);

        assertThat(resultado.getId()).isEqualTo(12L);

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);
        pscCaptor.getValue().createPreparedStatement(connection);

        verify(ps).setLong(1, 1L);
        verify(ps).setString(2, "Rival CF");
        verify(ps).setString(4, "18:00");
        verify(ps).setString(5, "Campo Municipal");
        verify(ps).setString(7, "COPA");
    }

    @Test
    void crearFijaNuloEnLaFechaCuandoNoSeIndica() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        Partido partido = new Partido();
        partido.setEquipoId(1L);
        partido.setRival("Rival CF");

        ArgumentCaptor<PreparedStatementCreator> pscCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<KeyHolder> keyHolderCaptor = ArgumentCaptor.forClass(KeyHolder.class);

        when(jdbcTemplate.update(pscCaptor.capture(), keyHolderCaptor.capture())).thenAnswer(invocation -> {
            keyHolderCaptor.getValue().getKeyList().add(Map.of("ID", 1L));
            return 1;
        });

        dao.crear(partido);

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);
        pscCaptor.getValue().createPreparedStatement(connection);

        verify(ps).setNull(3, Types.DATE);
    }

    @Test
    void crearLanzaExcepcionSiNoSeGeneraId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        Partido partido = new Partido();
        partido.setEquipoId(1L);
        partido.setRival("Rival CF");

        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenReturn(1);

        assertThatThrownBy(() -> dao.crear(partido)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void actualizarEjecutaElUpdateConTodosLosCampos() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        Partido partido = new Partido();
        partido.setId(1L);
        partido.setRival("Rival CF");
        partido.setHora("18:00");
        partido.setCampo("Campo Municipal");
        partido.setResultado("2-1");
        partido.setTipo("LIGA");
        partido.setUsuarioActualizoId(9L);
        Timestamp ahora = Timestamp.valueOf("2026-03-02 10:00:00");
        partido.setFechaActualizacion(ahora);

        dao.actualizar(partido);

        verify(jdbcTemplate).update(anyString(), eq("Rival CF"), eq((Object) null), eq("18:00"),
                eq("Campo Municipal"), eq("2-1"), eq("LIGA"), eq(9L), eq(ahora), eq(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullSiNoHayResultados() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(Collections.emptyList());

        assertThat(dao.obtenerPorId(1L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveElPartidoEncontrado() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Partido>> captor = ArgumentCaptor.forClass(RowMapper.class);
        Partido esperado = new Partido();
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of(esperado));

        assertThat(dao.obtenerPorId(1L)).isSameAs(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(1L);
        when(rs.getLong("EQUIPO_ID")).thenReturn(2L);
        when(rs.getString("RIVAL")).thenReturn("Rival CF");
        when(rs.getDate("DIA")).thenReturn(java.sql.Date.valueOf("2026-03-01"));
        when(rs.getString("HORA")).thenReturn("18:00");
        when(rs.getString("CAMPO")).thenReturn("Campo Municipal");
        when(rs.getString("RESULTADO")).thenReturn("2-1");
        when(rs.getString("TIPO")).thenReturn("AMISTOSO");
        when(rs.getLong("USUARIO_ACTUALIZO_ID")).thenReturn(9L);
        when(rs.wasNull()).thenReturn(false);
        Timestamp ahora = Timestamp.valueOf("2026-03-02 10:00:00");
        when(rs.getTimestamp("FECHA_ACTUALIZACION")).thenReturn(ahora);

        Partido mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(1L);
        assertThat(mapeado.getEquipoId()).isEqualTo(2L);
        assertThat(mapeado.getRival()).isEqualTo("Rival CF");
        assertThat(mapeado.getHora()).isEqualTo("18:00");
        assertThat(mapeado.getCampo()).isEqualTo("Campo Municipal");
        assertThat(mapeado.getResultado()).isEqualTo("2-1");
        assertThat(mapeado.getTipo()).isEqualTo("AMISTOSO");
        assertThat(mapeado.getUsuarioActualizoId()).isEqualTo(9L);
        assertThat(mapeado.getFechaActualizacion()).isEqualTo(ahora);
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowDejaUsuarioActualizoNuloCuandoLaColumnaEsNula() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Partido>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture(), eq(1L))).thenReturn(List.of());
        dao.obtenerPorId(1L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("USUARIO_ACTUALIZO_ID")).thenReturn(0L);
        when(rs.wasNull()).thenReturn(true);
        when(rs.getDate("DIA")).thenReturn(null);
        when(rs.getTimestamp("FECHA_ACTUALIZACION")).thenReturn(null);

        Partido mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getUsuarioActualizoId()).isNull();
        assertThat(mapeado.getDia()).isNull();
        assertThat(mapeado.getFechaActualizacion()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorEquipoFiltraPorEquipo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        List<Partido> esperado = List.of(new Partido());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(2L))).thenReturn(esperado);

        assertThat(dao.obtenerPorEquipo(2L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerUltimosPorEquipoPasaElLimite() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        List<Partido> esperado = List.of(new Partido());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(2L), eq(5))).thenReturn(esperado);

        assertThat(dao.obtenerUltimosPorEquipo(2L, 5)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerMasRelevantePorEquipoNombreDevuelveElProximoPartidoSinResultado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        Partido proximo = new Partido();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("Mutxamel CF"), eq("Primer Equipo")))
                .thenReturn(List.of(proximo));

        Partido resultado = dao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo");

        assertThat(resultado).isSameAs(proximo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerMasRelevantePorEquipoNombreDevuelveElUltimoJugadoSiNoHayProximo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        Partido ultimo = new Partido();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("Mutxamel CF"), eq("Primer Equipo")))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(ultimo));

        Partido resultado = dao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo");

        assertThat(resultado).isSameAs(ultimo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerMasRelevantePorEquipoNombreDevuelveNullSiNoHayNinguno() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("Mutxamel CF"), eq("Primer Equipo")))
                .thenReturn(Collections.emptyList());

        assertThat(dao.obtenerMasRelevantePorEquipoNombre("Mutxamel CF", "Primer Equipo")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerMasRelevantePorEquipoDevuelveElProximoPartidoSinResultado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        Partido proximo = new Partido();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(proximo));

        assertThat(dao.obtenerMasRelevantePorEquipo(1L)).isSameAs(proximo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerMasRelevantePorEquipoDevuelveElUltimoJugadoSiNoHayProximo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        Partido ultimo = new Partido();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(ultimo));

        assertThat(dao.obtenerMasRelevantePorEquipo(1L)).isSameAs(ultimo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerMasRelevantePorEquipoDevuelveNullSiNoHayNinguno() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(Collections.emptyList());

        assertThat(dao.obtenerMasRelevantePorEquipo(1L)).isNull();
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        dao.eliminar(1L);

        verify(jdbcTemplate).update(anyString(), eq(1L));
    }
}
