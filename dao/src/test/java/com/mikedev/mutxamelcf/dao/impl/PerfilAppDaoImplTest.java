package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.Familiar;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerfilAppDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void obtenerFamiliarPorUsuarioDevuelveNullCuandoNoExiste() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PerfilAppDaoImpl dao = new PerfilAppDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), anyInt()))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(dao.obtenerFamiliarPorUsuario(1)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerFamiliarPorUsuarioMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PerfilAppDaoImpl dao = new PerfilAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Familiar>> captor = ArgumentCaptor.forClass(RowMapper.class);
        Familiar esperado = new Familiar();
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), anyInt())).thenReturn(esperado);

        assertThat(dao.obtenerFamiliarPorUsuario(1)).isSameAs(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(3L);
        when(rs.getString("NOMBRE")).thenReturn("Ana");
        when(rs.getString("APELLIDOS")).thenReturn("Garcia");
        when(rs.getString("TELEFONO")).thenReturn("600000000");
        when(rs.getString("EMAIL")).thenReturn("ana@example.com");
        when(rs.getInt("RECIBE_INFO_CLUB")).thenReturn(1);
        when(rs.getInt("WHATSAPP_ACTIVO")).thenReturn(1);

        Familiar mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getNombre()).isEqualTo("Ana");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerJugadoresPorUsuarioMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PerfilAppDaoImpl dao = new PerfilAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Jugador>> captor = ArgumentCaptor.forClass(RowMapper.class);
        Jugador esperado = new Jugador();
        when(jdbcTemplate.query(anyString(), captor.capture(), anyInt(), anyInt())).thenReturn(List.of(esperado));

        assertThat(dao.obtenerJugadoresPorUsuario(1)).containsExactly(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(4L);
        when(rs.getString("NOMBRE")).thenReturn("Juan");
        when(rs.getString("APELLIDOS")).thenReturn("Perez");
        when(rs.getString("CATEGORIA")).thenReturn("SENIOR");
        when(rs.getString("DEPORTE")).thenReturn("FUTBOL");
        when(rs.getString("EQUIPO")).thenReturn("Senior A");
        when(rs.getInt("DORSAL")).thenReturn(9);
        when(rs.getString("POSICION")).thenReturn("DELANTERO");

        Jugador mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getId()).isEqualTo(4L);
        assertThat(mapeado.getDorsal()).isEqualTo(9);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerCuerpoTecnicoPorUsuarioMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PerfilAppDaoImpl dao = new PerfilAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<CuerpoTecnico>> captor = ArgumentCaptor.forClass(RowMapper.class);
        CuerpoTecnico esperado = new CuerpoTecnico();
        when(jdbcTemplate.query(anyString(), captor.capture(), anyInt())).thenReturn(List.of(esperado));

        assertThat(dao.obtenerCuerpoTecnicoPorUsuario(1)).containsExactly(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(5L);
        when(rs.getString("NOMBRE")).thenReturn("Ana");
        when(rs.getString("APELLIDOS")).thenReturn("Garcia");
        when(rs.getString("CATEGORIA")).thenReturn("SENIOR");
        when(rs.getString("DEPORTE")).thenReturn("FUTBOL");
        when(rs.getString("EQUIPO")).thenReturn("Senior A");
        when(rs.getString("PUESTO")).thenReturn("ENTRENADOR");

        CuerpoTecnico mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getId()).isEqualTo(5L);
        assertThat(mapeado.getPuesto()).isEqualTo("ENTRENADOR");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerEquiposPorUsuarioMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PerfilAppDaoImpl dao = new PerfilAppDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Equipo>> captor = ArgumentCaptor.forClass(RowMapper.class);
        Equipo esperado = new Equipo();
        when(jdbcTemplate.query(anyString(), captor.capture(), anyInt())).thenReturn(List.of(esperado));

        assertThat(dao.obtenerEquiposPorUsuario(1)).containsExactly(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID")).thenReturn(6L);
        when(rs.getString("CATEGORIA")).thenReturn("SENIOR");
        when(rs.getString("GRUPO")).thenReturn("A");
        when(rs.getString("ORDEN")).thenReturn("1");
        when(rs.getString("NOMBRE")).thenReturn("Senior A");
        when(rs.getString("DEPORTE")).thenReturn("FUTBOL");

        Equipo mapeado = captor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getId()).isEqualTo(6L);
        assertThat(mapeado.getNombre()).isEqualTo("Senior A");
    }
}
