package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.invocation.Invocation;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.mikedev.mutxamelcf.model.Partido;

/**
 * Los tests con JdbcTemplate simulado no ejecutan el SQL, y una columna que
 * el mapper lee pero un SELECT no pide es un error de Oracle en producción.
 * Se lee una fila con el mapper real, se recogen las columnas que pide al
 * ResultSet y se comprueba que CADA SELECT que lo alimenta las proyecta.
 */
class PartidoDaoImplColumnasTest {

    @Test
    @SuppressWarnings("unchecked")
    void todasLasConsultasPidenTodasLasColumnasQueLeeElMapper() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        dao.obtenerPorId(1L);
        dao.obtenerPorEquipo(1L);
        dao.obtenerUltimosPorEquipo(1L, 5);
        dao.obtenerPartidosSinConvocatoria(1L, null);
        dao.obtenerMasRelevantePorEquipoNombre("Primer Equipo", "Primer Equipo");
        dao.obtenerMasRelevantePorEquipo(1L);
        dao.obtenerPorEquipoYRangoFechas(1L, LocalDate.now(), LocalDate.now());
        dao.obtenerPorRangoFechas(LocalDate.now(), LocalDate.now());
        dao.obtenerPendientesDeAvisoResultado(LocalDateTime.now());

        List<String> consultas = new ArrayList<>();
        RowMapper<Partido> mapper = null;

        for (Invocation invocacion : Mockito.mockingDetails(jdbcTemplate).getInvocations()) {
            if (invocacion.getMethod().getName().equals("query")
                    && invocacion.getArguments().length >= 2
                    && invocacion.getArgument(0) instanceof String sql
                    && invocacion.getArgument(1) instanceof RowMapper<?> rowMapper) {
                consultas.add(sql);
                mapper = (RowMapper<Partido>) rowMapper;
            }
        }

        // 1+1+1+1+2+2+1+1+1 consultas distintas
        assertThat(consultas).hasSize(11);

        Set<String> columnasLeidas = new LinkedHashSet<>();
        ResultSet rs = mock(ResultSet.class, invocation -> {
            if (invocation.getArguments().length == 1 && invocation.getArgument(0) instanceof String columna) {
                columnasLeidas.add(columna);
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });

        mapper.mapRow(rs, 0);

        assertThat(columnasLeidas).contains("ORIGEN", "JORNADA", "ES_LOCAL", "RESULTADO", "AVISO_RESULTADO_ENVIADO");

        assertThat(consultas).allSatisfy(sql -> {
            String proyeccion = sql.substring(0, sql.indexOf("FROM"));
            assertThat(columnasLeidas).allSatisfy(columna -> assertThat(proyeccion).contains(columna));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void elMapperLeeOrigenYJornada() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        dao.obtenerPorRangoFechas(LocalDate.now(), LocalDate.now());

        org.mockito.ArgumentCaptor<RowMapper<Partido>> captor = org.mockito.ArgumentCaptor
                .forClass(RowMapper.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(Object[].class));

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ORIGEN")).thenReturn("FFCV");
        when(rs.getInt("JORNADA")).thenReturn(4);

        Partido partido = captor.getValue().mapRow(rs, 0);

        assertThat(partido.getOrigen()).isEqualTo("FFCV");
        assertThat(partido.getJornada()).isEqualTo(4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinOrigenNiJornadaSeMapeaComoManualSinJornada() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        dao.obtenerPorRangoFechas(LocalDate.now(), LocalDate.now());

        org.mockito.ArgumentCaptor<RowMapper<Partido>> captor = org.mockito.ArgumentCaptor
                .forClass(RowMapper.class);
        verify(jdbcTemplate).query(anyString(), captor.capture(), any(Object[].class));

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("ORIGEN")).thenReturn(null);
        when(rs.getInt("JORNADA")).thenReturn(0);
        when(rs.wasNull()).thenReturn(true);
        when(rs.getDate("DIA")).thenReturn(Date.valueOf("2026-10-17"));

        Partido partido = captor.getValue().mapRow(rs, 0);

        assertThat(partido.getOrigen()).isEqualTo(Partido.ORIGEN_MANUAL);
        assertThat(partido.getJornada()).isNull();
    }

}
