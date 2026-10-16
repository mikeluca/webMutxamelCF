package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.mikedev.mutxamelcf.model.DescansoEquipo;
import com.mikedev.mutxamelcf.model.Partido;

class ImportadorFfcvDaoImplTest {

    @Test
    void actualizarDatosFfcvNuncaEscribeResultadoGolesNiCancelacion() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoDaoImpl dao = new PartidoDaoImpl(jdbcTemplate);

        Partido partido = new Partido();
        partido.setId(5L);
        partido.setRival("Rival");
        partido.setHora("10:00");
        partido.setCampo("Campo");
        partido.setJornada(3);

        dao.actualizarDatosFfcv(partido);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq("Rival"), eq("10:00"), eq("Campo"), eq(3), eq(1), eq(5L));
        assertThat(sql.getValue())
                .contains("RIVAL", "HORA", "CAMPO", "JORNADA", "ORIGEN = 'FFCV'")
                .doesNotContain("RESULTADO", "GOLES", "CANCELADO", "DIA =", "TIPO");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorCompeticionYLetraSinLetraBuscaLetraNula() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoFfcvDaoImpl dao = new EquipoFfcvDaoImpl(jdbcTemplate);

        dao.obtenerPorCompeticionYLetra("Primera FFCV, Grup 4", null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq("Primera FFCV, Grup 4"));
        assertThat(sql.getValue()).contains("LETRA IS NULL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void obtenerPorCompeticionYLetraConLetraLaCompara() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EquipoFfcvDaoImpl dao = new EquipoFfcvDaoImpl(jdbcTemplate);

        dao.obtenerPorCompeticionYLetra("Segona FFCV, Grup 7", "B");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq("Segona FFCV, Grup 7"), eq("B"));
        assertThat(sql.getValue()).contains("LETRA = ?");
    }

    @Test
    @SuppressWarnings("unchecked")
    void descansosSeBuscanPorRangoDeFechas() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DescansoEquipoDaoImpl dao = new DescansoEquipoDaoImpl(jdbcTemplate);

        dao.obtenerPorRangoFechas(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 18));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class),
                eq(java.sql.Date.valueOf("2026-10-17")), eq(java.sql.Date.valueOf("2026-10-18")));
        assertThat(sql.getValue()).contains("DESCANSOS_EQUIPO").contains("BETWEEN");
    }

    @Test
    void crearDescansoInsertaEquipoFechaYJornada() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DescansoEquipoDaoImpl dao = new DescansoEquipoDaoImpl(jdbcTemplate);

        dao.crear(new DescansoEquipo(null, 3L, LocalDate.of(2026, 10, 17), null));

        verify(jdbcTemplate).update(anyString(), eq(3L), eq(java.sql.Date.valueOf("2026-10-17")), eq((Object) null));
    }

}
