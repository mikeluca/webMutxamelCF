package com.mikedev.mutxamelcf.dao.impl;

import com.mikedev.mutxamelcf.model.PartidoLiveEstado;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartidoLiveDaoImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void obtenerEstadoCombinaElMarcadorYLosGoleadores() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoLiveDaoImpl dao = new PartidoLiveDaoImpl(jdbcTemplate);

        ArgumentCaptor<RowMapper<PartidoLiveEstado>> estadoCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.queryForObject(anyString(), estadoCaptor.capture()))
                .thenReturn(new PartidoLiveEstado(2, 1, null));

        ArgumentCaptor<RowMapper<String>> goleadorCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), goleadorCaptor.capture())).thenReturn(List.of("Autor 1"));

        PartidoLiveEstado estado = dao.obtenerEstado();

        assertThat(estado.getGolesFavor()).isEqualTo(2);
        assertThat(estado.getGolesContra()).isEqualTo(1);
        assertThat(estado.getGoleadores()).containsExactly("Autor 1");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("GOLES_FAVOR")).thenReturn(3);
        when(rs.getInt("GOLES_CONTRA")).thenReturn(0);
        PartidoLiveEstado mapeado = estadoCaptor.getValue().mapRow(rs, 0);
        assertThat(mapeado.getGolesFavor()).isEqualTo(3);

        ResultSet rsGoleador = mock(ResultSet.class);
        when(rsGoleador.getString("NOMBRE")).thenReturn("Otro autor");
        assertThat(goleadorCaptor.getValue().mapRow(rsGoleador, 0)).isEqualTo("Otro autor");
    }

    @Test
    void reiniciarResetaElMarcadorYBorraLosGoleadores() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoLiveDaoImpl dao = new PartidoLiveDaoImpl(jdbcTemplate);

        dao.reiniciar();

        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(anyString());
        verify(jdbcTemplate).update("DELETE FROM PARTIDO_LIVE_GOLEADOR");
    }

    @Test
    void sumarGolFavorActualizaElMarcadorYRegistraElAutor() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoLiveDaoImpl dao = new PartidoLiveDaoImpl(jdbcTemplate);

        dao.sumarGolFavor("Autor 1");

        verify(jdbcTemplate).update("INSERT INTO PARTIDO_LIVE_GOLEADOR (NOMBRE) VALUES (?)", "Autor 1");
    }

    @Test
    void sumarGolContraActualizaElMarcador() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PartidoLiveDaoImpl dao = new PartidoLiveDaoImpl(jdbcTemplate);

        dao.sumarGolContra();

        verify(jdbcTemplate).update(anyString());
    }
}
