package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.CuerpoTecnico;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CuerpoTecnicoDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void guardarInsertaCuandoNoExisteTodavia() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuerpoTecnicoDaoImpl dao = new CuerpoTecnicoDaoImpl(jdbcTemplate);

        CuerpoTecnico staff = new CuerpoTecnico();
        staff.setNombre("Ana");

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), isNull()))
                .thenThrow(new EmptyResultDataAccessException(1));
        // insertarCuerpoTecnico pasa 10 valores como varargs
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        assertThat(dao.guardar(staff)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarActualizaCuandoYaExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuerpoTecnicoDaoImpl dao = new CuerpoTecnicoDaoImpl(jdbcTemplate);

        CuerpoTecnico staff = new CuerpoTecnico();
        staff.setId(2L);
        staff.setNombre("Ana");

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(2L))).thenReturn(new CuerpoTecnico());
        // actualizarCuerpoTecnico pasa 8 valores como varargs
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        assertThat(dao.guardar(staff)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuerpoTecnicoDaoImpl dao = new CuerpoTecnicoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(99L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerPorId(99L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuerpoTecnicoDaoImpl dao = new CuerpoTecnicoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<CuerpoTecnico>> captor = ArgumentCaptor.forClass(RowMapper.class);
        CuerpoTecnico esperado = new CuerpoTecnico();
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(3L))).thenReturn(esperado);

        assertThat(dao.obtenerPorId(3L)).isSameAs(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(3L);
        when(rs.getString("nombre")).thenReturn("Ana");
        when(rs.getString("apellidos")).thenReturn("Garcia");
        when(rs.getString("categoria")).thenReturn("SENIOR");
        when(rs.getString("deporte")).thenReturn("FUTBOL");
        when(rs.getString("equipo")).thenReturn("Senior A");
        when(rs.getString("puesto")).thenReturn("ENTRENADOR");
        when(rs.getBytes("foto")).thenReturn(new byte[] { 9 });

        CuerpoTecnico mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getNombre()).isEqualTo("Ana");
        assertThat(mapeado.getApellidos()).isEqualTo("Garcia");
        assertThat(mapeado.getCategoria()).isEqualTo("SENIOR");
        assertThat(mapeado.getDeporte()).isEqualTo("FUTBOL");
        assertThat(mapeado.getEquipo()).isEqualTo("Senior A");
        assertThat(mapeado.getPuesto()).isEqualTo("ENTRENADOR");
        assertThat(mapeado.getFoto()).containsExactly(9);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosPorCategoriaFiltraPorCategoria() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuerpoTecnicoDaoImpl dao = new CuerpoTecnicoDaoImpl(jdbcTemplate);

        List<CuerpoTecnico> esperado = List.of(new CuerpoTecnico());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("SENIOR"))).thenReturn(esperado);

        assertThat(dao.obtenerTodosPorCategoria("SENIOR")).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosPorEquipoFiltraPorEquipo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuerpoTecnicoDaoImpl dao = new CuerpoTecnicoDaoImpl(jdbcTemplate);

        List<CuerpoTecnico> esperado = List.of(new CuerpoTecnico());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("Senior A"))).thenReturn(esperado);

        assertThat(dao.obtenerTodosPorEquipo("Senior A")).isSameAs(esperado);
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuerpoTecnicoDaoImpl dao = new CuerpoTecnicoDaoImpl(jdbcTemplate);

        dao.eliminar(6L);

        verify(jdbcTemplate).update(anyString(), eq(6L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CuerpoTecnicoDaoImpl dao = new CuerpoTecnicoDaoImpl(jdbcTemplate);

        List<CuerpoTecnico> esperado = List.of(new CuerpoTecnico());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodos()).isSameAs(esperado);
    }
}
