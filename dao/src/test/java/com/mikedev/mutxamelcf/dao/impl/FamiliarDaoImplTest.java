package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Familiar;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
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

class FamiliarDaoImplTest {

    @Test
    void guardarFamiliarDevuelveFalseSiElFamiliarEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarDaoImpl dao = new FamiliarDaoImpl(jdbcTemplate);

        assertThat(dao.guardarFamiliar(null)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarFamiliarInsertaCuandoNoTieneId() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarDaoImpl dao = new FamiliarDaoImpl(jdbcTemplate);

        Familiar familiar = new Familiar();
        familiar.setNombre("Ana");
        familiar.setApellidos("Garcia");
        familiar.setTelefono("600000000");
        familiar.setEmail("ana@example.com");
        familiar.setRecibeInfoClub(1);
        familiar.setWhatsappActivo(1);

        ArgumentCaptor<PreparedStatementCreator> pscCaptor = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        ArgumentCaptor<KeyHolder> keyHolderCaptor = ArgumentCaptor.forClass(KeyHolder.class);

        when(jdbcTemplate.update(pscCaptor.capture(), keyHolderCaptor.capture())).thenAnswer(invocation -> {
            keyHolderCaptor.getValue().getKeyList().add(Map.of("ID", 42L));
            return 1;
        });

        boolean resultado = dao.guardarFamiliar(familiar);

        assertThat(resultado).isTrue();
        assertThat(familiar.getId()).isEqualTo(42L);

        // Verificamos que el PreparedStatementCreator monta el statement correctamente
        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);

        pscCaptor.getValue().createPreparedStatement(connection);

        verify(ps).setString(1, "Ana");
        verify(ps).setString(2, "Garcia");
        verify(ps).setString(3, "600000000");
        verify(ps).setString(4, "ana@example.com");
        verify(ps).setInt(5, 1);
        verify(ps).setInt(6, 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarFamiliarActualizaCuandoYaExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarDaoImpl dao = new FamiliarDaoImpl(jdbcTemplate);

        Familiar familiar = new Familiar();
        familiar.setId(5L);
        familiar.setNombre("Ana");

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(5L))).thenReturn(new Familiar());
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(dao.guardarFamiliar(familiar)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarDaoImpl dao = new FamiliarDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(99L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerPorId(99L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarDaoImpl dao = new FamiliarDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Familiar>> captor = ArgumentCaptor.forClass(RowMapper.class);
        Familiar esperado = new Familiar();
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(3L))).thenReturn(esperado);

        assertThat(dao.obtenerPorId(3L)).isSameAs(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getString("NOMBRE")).thenReturn("Ana");
        when(rs.getString("APELLIDOS")).thenReturn("Garcia");
        when(rs.getString("TELEFONO")).thenReturn("600000000");
        when(rs.getString("EMAIL")).thenReturn("ana@example.com");
        when(rs.getInt("RECIBE_INFO_CLUB")).thenReturn(1);
        when(rs.getInt("WHATSAPP_ACTIVO")).thenReturn(0);

        Familiar mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getNombre()).isEqualTo("Ana");
        assertThat(mapeado.getApellidos()).isEqualTo("Garcia");
        assertThat(mapeado.getTelefono()).isEqualTo("600000000");
        assertThat(mapeado.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarDaoImpl dao = new FamiliarDaoImpl(jdbcTemplate);

        List<Familiar> esperado = List.of(new Familiar());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodos()).isSameAs(esperado);
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarDaoImpl dao = new FamiliarDaoImpl(jdbcTemplate);

        dao.eliminar(8L);

        verify(jdbcTemplate).update(anyString(), eq(8L));
    }

    @Test
    void obtenerPorJugadorMapeaLosCamposDeParentesco() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarDaoImpl dao = new FamiliarDaoImpl(jdbcTemplate);

        Familiar esperado = new Familiar();
        esperado.setParentesco("MADRE");
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Familiar>>any(), eq(15L)))
                .thenReturn(List.of(esperado));

        List<Familiar> resultado = dao.obtenerPorJugador(15L);

        assertThat(resultado).containsExactly(esperado);
    }
}
