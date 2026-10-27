package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Jugador;

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
import static org.mockito.Mockito.when;

class JugadorDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void guardarJugadorInsertaCuandoNoExisteTodavia() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JugadorDaoImpl dao = new JugadorDaoImpl(jdbcTemplate);

        Jugador jugador = new Jugador();
        jugador.setDni("12345678A");

        // Sin id no hay nada que buscar: es un alta. El INSERT recupera el id generado con un KeyHolder.
        when(jdbcTemplate.update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),
                any(org.springframework.jdbc.support.KeyHolder.class))).thenReturn(1);

        boolean resultado = dao.guardarJugador(jugador);

        assertThat(resultado).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarJugadorActualizaCuandoYaExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JugadorDaoImpl dao = new JugadorDaoImpl(jdbcTemplate);

        Jugador jugador = new Jugador();
        jugador.setId(9L);
        jugador.setDni("12345678A");

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(9L))).thenReturn(new Jugador());
        // actualizarJugador pasa 11 valores como varargs: hay que igualar el numero exacto de matchers
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any())).thenReturn(1);

        boolean resultado = dao.guardarJugador(jugador);

        assertThat(resultado).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JugadorDaoImpl dao = new JugadorDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(99L)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerPorId(99L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorIdMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JugadorDaoImpl dao = new JugadorDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Jugador>> captor = ArgumentCaptor.forClass(RowMapper.class);
        Jugador esperado = new Jugador();
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(3L))).thenReturn(esperado);

        Jugador resultado = dao.obtenerPorId(3L);
        assertThat(resultado).isSameAs(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(3L);
        when(rs.getString("nombre")).thenReturn("Juan");
        when(rs.getString("apellidos")).thenReturn("Perez");
        when(rs.getString("categoria")).thenReturn("SENIOR");
        when(rs.getString("deporte")).thenReturn("FUTBOL");
        // El equipo sale de EQUIPO (por EQUIPO_ID), no de la copia JUGADORES.EQUIPO
        when(rs.getString("equipo_actual")).thenReturn("Senior A");
        when(rs.getLong("equipo_id")).thenReturn(7L);
        when(rs.getInt("anio_nacimiento")).thenReturn(2012);
        when(rs.getDate("fecha_baja")).thenReturn(null);
        when(rs.getInt("dorsal")).thenReturn(10);
        when(rs.getString("posicion")).thenReturn("DELANTERO");
        when(rs.getBytes("foto")).thenReturn(new byte[] { 1, 2, 3 });

        Jugador mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getNombre()).isEqualTo("Juan");
        assertThat(mapeado.getApellidos()).isEqualTo("Perez");
        assertThat(mapeado.getCategoria()).isEqualTo("SENIOR");
        assertThat(mapeado.getDeporte()).isEqualTo("FUTBOL");
        assertThat(mapeado.getEquipo()).isEqualTo("Senior A");
        assertThat(mapeado.getEquipoId()).isEqualTo(7L);
        assertThat(mapeado.getAnioNacimiento()).isEqualTo(2012);
        assertThat(mapeado.getFechaBaja()).isNull();
        assertThat(mapeado.estaDeBaja()).isFalse();
        assertThat(mapeado.getDorsal()).isEqualTo(10);
        assertThat(mapeado.getPosicion()).isEqualTo("DELANTERO");
        assertThat(mapeado.getFoto()).containsExactly(1, 2, 3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosPorCategoriaFiltraPorCategoria() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JugadorDaoImpl dao = new JugadorDaoImpl(jdbcTemplate);

        List<Jugador> esperado = List.of(new Jugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("SENIOR"))).thenReturn(esperado);

        assertThat(dao.obtenerTodosPorCategoria("SENIOR")).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosPorEquipoIdFiltraPorEquipoId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JugadorDaoImpl dao = new JugadorDaoImpl(jdbcTemplate);

        List<Jugador> esperado = List.of(new Jugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5L))).thenReturn(esperado);

        assertThat(dao.obtenerTodosPorEquipoId(5L)).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosPorNombreEquipoResuelveElNombreEnLaTablaEquipo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JugadorDaoImpl dao = new JugadorDaoImpl(jdbcTemplate);

        List<Jugador> esperado = List.of(new Jugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("Senior A"))).thenReturn(esperado);

        assertThat(dao.obtenerTodosPorNombreEquipo("Senior A")).isSameAs(esperado);
    }

    @Test
    void eliminarBorraSuFilaDeTemporadaYElJugador() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JugadorDaoImpl dao = new JugadorDaoImpl(jdbcTemplate);

        dao.eliminar(4L);

        org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(anyString(), eq(4L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosDevuelveLaListaCompleta() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JugadorDaoImpl dao = new JugadorDaoImpl(jdbcTemplate);

        List<Jugador> esperado = List.of(new Jugador(), new Jugador());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        assertThat(dao.obtenerTodos()).isSameAs(esperado);
    }
}
