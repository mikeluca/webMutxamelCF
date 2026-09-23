package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.Equipo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipoDaoImplTest {

    @Test
    void guardarNoInsertaNiActualizaSiYaExisteOtroEquipoConLaMismaCategoriaYGrupo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoDaoImpl dao = new EquipoDaoImpl(jdbcTemplate);

        Equipo equipo = new Equipo();
        equipo.setCategoria("SENIOR");
        equipo.setGrupo("A");

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("SENIOR"), eq("A"))).thenReturn(1);

        boolean resultado = dao.guardar(equipo);

        assertThat(resultado).isFalse();
        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
    }

    @Test
    void guardarInsertaCuandoNoTieneIdYNoExisteDuplicado() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoDaoImpl dao = new EquipoDaoImpl(jdbcTemplate);

        Equipo equipo = new Equipo();
        equipo.setCategoria("SENIOR");
        equipo.setGrupo("A");
        equipo.setOrden("1");
        equipo.setNombre("Senior A");
        equipo.setDeporte("FUTBOL");

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("SENIOR"), eq("A"))).thenReturn(0);
        when(jdbcTemplate.update(anyString(), eq("SENIOR"), eq("A"), eq("1"), eq("Senior A"), eq("FUTBOL")))
                .thenReturn(1);

        boolean resultado = dao.guardar(equipo);

        assertThat(resultado).isTrue();
    }

    @Test
    void guardarActualizaCuandoTieneIdYNoHayConflictoConOtroEquipo() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoDaoImpl dao = new EquipoDaoImpl(jdbcTemplate);

        Equipo equipo = new Equipo();
        equipo.setId(5L);
        equipo.setCategoria("SENIOR");
        equipo.setGrupo("A");
        equipo.setOrden("1");
        equipo.setNombre("Senior A");
        equipo.setDeporte("FUTBOL");

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("SENIOR"), eq("A"), eq(5L)))
                .thenReturn(0);
        when(jdbcTemplate.update(anyString(), eq("SENIOR"), eq("A"), eq("1"), eq("Senior A"), eq("FUTBOL"), eq(5L)))
                .thenReturn(1);

        boolean resultado = dao.guardar(equipo);

        assertThat(resultado).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerEquipoPorIdMapeaCorrectamenteElResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoDaoImpl dao = new EquipoDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<Equipo>> captor = ArgumentCaptor.forClass(RowMapper.class);

        Equipo esperado = new Equipo();
        when(jdbcTemplate.queryForObject(anyString(), captor.capture(), eq(3L))).thenReturn(esperado);

        Equipo resultado = dao.obtenerEquipoPorId(3L);

        assertThat(resultado).isSameAs(esperado);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(3L);
        when(rs.getString("categoria")).thenReturn("SENIOR");
        when(rs.getString("grupo")).thenReturn("A");
        when(rs.getString("orden")).thenReturn("1");
        when(rs.getString("nombre")).thenReturn("Senior A");
        when(rs.getString("deporte")).thenReturn("FUTBOL");

        Equipo mapeado = captor.getValue().mapRow(rs, 0);

        assertThat(mapeado.getId()).isEqualTo(3L);
        assertThat(mapeado.getCategoria()).isEqualTo("SENIOR");
        assertThat(mapeado.getGrupo()).isEqualTo("A");
        assertThat(mapeado.getOrden()).isEqualTo("1");
        assertThat(mapeado.getNombre()).isEqualTo("Senior A");
        assertThat(mapeado.getDeporte()).isEqualTo("FUTBOL");
    }

    @Test
    void eliminarEquipoEjecutaElDelete() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoDaoImpl dao = new EquipoDaoImpl(jdbcTemplate);

        dao.eliminarEquipo(7L);

        verify(jdbcTemplate).update(anyString(), eq(7L));
    }

    @Test
    void obtenerCategoriasDelegaEnJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoDaoImpl dao = new EquipoDaoImpl(jdbcTemplate);

        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("SENIOR", "JUVENIL"));

        List<String> categorias = dao.obtenerCategorias();

        assertThat(categorias).containsExactly("SENIOR", "JUVENIL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosDevuelveLaListaDelJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoDaoImpl dao = new EquipoDaoImpl(jdbcTemplate);

        List<Equipo> esperado = List.of(new Equipo());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(esperado);

        List<Equipo> resultado = dao.obtenerTodos();

        assertThat(resultado).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerTodosPorCategoriaFiltraPorCategoria() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoDaoImpl dao = new EquipoDaoImpl(jdbcTemplate);

        List<Equipo> esperado = List.of(new Equipo());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("SENIOR"))).thenReturn(esperado);

        List<Equipo> resultado = dao.obtenerTodosPorCategoria("SENIOR");

        assertThat(resultado).isSameAs(esperado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerEquiposAgrupadosPorCategoriaAgrupaPorOrden() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoDaoImpl dao = new EquipoDaoImpl(jdbcTemplate);

        Equipo equipoA1 = new Equipo();
        equipoA1.setOrden("1");
        Equipo equipoA2 = new Equipo();
        equipoA2.setOrden("1");
        Equipo equipoB = new Equipo();
        equipoB.setOrden("2");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("FUTBOL")))
                .thenReturn(List.of(equipoA1, equipoA2, equipoB));

        Map<String, List<Equipo>> agrupados = dao.obtenerEquiposAgrupadosPorCategoria("FUTBOL");

        assertThat(agrupados).containsOnlyKeys("1", "2");
        assertThat(agrupados.get("1")).hasSize(2);
        assertThat(agrupados.get("2")).hasSize(1);
    }
}
