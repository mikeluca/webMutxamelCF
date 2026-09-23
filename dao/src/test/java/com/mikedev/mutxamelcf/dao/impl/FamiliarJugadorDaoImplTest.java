package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.FamiliarJugador;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FamiliarJugadorDaoImplTest {

    @Test
    void guardarFamiliarJugadorDevuelveFalseSiEsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        assertThat(dao.guardarFamiliarJugador(null)).isFalse();
    }

    @Test
    void insertaSinTocarOtrosPrincipalesCuandoNoEsPrincipal() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        FamiliarJugador relacion = new FamiliarJugador();
        relacion.setFamiliarId(1L);
        relacion.setJugadorId(2L);
        relacion.setEsPrincipal(0);

        when(jdbcTemplate.update(anyString(), eq(1L), eq(2L), any(), eq(0))).thenReturn(1);

        assertThat(dao.guardarFamiliarJugador(relacion)).isTrue();

        // No debe tocar el resto de principales del jugador
        verify(jdbcTemplate, never()).update(anyString(), eq(2L));
    }

    @Test
    void insertaComoPrincipalYQuitaElPrincipalAnteriorDelJugador() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        FamiliarJugador relacion = new FamiliarJugador();
        relacion.setFamiliarId(1L);
        relacion.setJugadorId(2L);
        relacion.setEsPrincipal(1);

        when(jdbcTemplate.update(anyString(), eq(2L))).thenReturn(1);
        when(jdbcTemplate.update(anyString(), eq(1L), eq(2L), any(), eq(1))).thenReturn(1);

        assertThat(dao.guardarFamiliarJugador(relacion)).isTrue();

        verify(jdbcTemplate).update(anyString(), eq(2L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void actualizaComoPrincipalExcluyendoseASiMismaDelDesmarcado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        FamiliarJugador relacion = new FamiliarJugador();
        relacion.setId(10L);
        relacion.setFamiliarId(1L);
        relacion.setJugadorId(2L);
        relacion.setEsPrincipal(1);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(10L)))
                .thenReturn(new FamiliarJugador());
        when(jdbcTemplate.update(anyString(), eq(2L), eq(10L))).thenReturn(1);
        when(jdbcTemplate.update(anyString(), eq(1L), eq(2L), any(), eq(1), eq(10L))).thenReturn(1);

        assertThat(dao.guardarFamiliarJugador(relacion)).isTrue();

        verify(jdbcTemplate).update(anyString(), eq(2L), eq(10L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(99L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerPorId(99L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapRowMapeaTodosLosCampos() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<FamiliarJugador>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(3L))).thenReturn(new FamiliarJugador());
        dao.obtenerPorId(3L);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getLong("FAMILIAR_ID")).thenReturn(1L);
        when(rs.getLong("JUGADOR_ID")).thenReturn(2L);
        when(rs.getString("PARENTESCO")).thenReturn("MADRE");
        when(rs.getInt("ES_PRINCIPAL")).thenReturn(1);

        FamiliarJugador mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getFamiliarId()).isEqualTo(1L);
        assertThat(mapeado.getJugadorId()).isEqualTo(2L);
        assertThat(mapeado.getParentesco()).isEqualTo("MADRE");
        assertThat(mapeado.getEsPrincipal()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        List<FamiliarJugador> esperado = List.of(new FamiliarJugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodos()).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerFamiliaresDeJugadorFiltraPorJugador() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        List<FamiliarJugador> esperado = List.of(new FamiliarJugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(2L))).thenReturn(esperado);

        assertThat(dao.obtenerFamiliaresDeJugador(2L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerJugadoresDeFamiliarFiltraPorFamiliar() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        List<FamiliarJugador> esperado = List.of(new FamiliarJugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L))).thenReturn(esperado);

        assertThat(dao.obtenerJugadoresDeFamiliar(1L)).isSameAs(esperado);
    }

    @Test
    void eliminarEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        dao.eliminar(4L);

        verify(jdbcTemplate).update(anyString(), eq(4L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPrincipalDeJugadorDevuelveNullSiNoHayPrincipal() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(2L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerPrincipalDeJugador(2L)).isNull();
    }

    @Test
    void existeRelacionDevuelveTrueCuandoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L), eq(2L))).thenReturn(1);

        assertThat(dao.existeRelacion(1L, 2L)).isTrue();
    }

    @Test
    void tieneJugadoresDevuelveFalseCuandoNoHayFilas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamiliarJugadorDaoImpl dao = new FamiliarJugadorDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(1L))).thenReturn(0);

        assertThat(dao.tieneJugadores(1L)).isFalse();
    }
}
