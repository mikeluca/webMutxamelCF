package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;

class ConvocatoriaJugadorDaoH2Test {

    private ConvocatoriaJugadorDaoImpl dao;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        EsquemaH2.crear(jdbc);

        dao = new ConvocatoriaJugadorDaoImpl(jdbc);
    }

    private void convocar(long convocatoria, long... jugadores) {
        for (long jugador : jugadores) {
            dao.guardar(new ConvocatoriaJugador(null, convocatoria, jugador));
        }
    }

    @Test
    void guardaYLeePorConvocatoriaYJugador() {
        convocar(1, 100, 101);

        assertThat(dao.obtenerPorConvocatoria(1L)).extracting(ConvocatoriaJugador::getJugadorId)
                .containsExactly(100L, 101L);
        assertThat(dao.obtenerPorConvocatoriaYJugador(1L, 101L).getConvocatoriaId()).isEqualTo(1L);
        assertThat(dao.obtenerPorConvocatoriaYJugador(1L, 999L)).isNull();
    }

    @Test
    void eliminarPorConvocatoriaYJugadorBorraSoloEsaFila() {
        convocar(1, 100, 101);
        convocar(2, 100);

        assertThat(dao.eliminarPorConvocatoriaYJugador(1L, 100L)).isEqualTo(1);
        assertThat(dao.eliminarPorConvocatoriaYJugador(1L, 100L)).isZero();

        List<ConvocatoriaJugador> primera = dao.obtenerPorConvocatoria(1L);

        assertThat(primera).extracting(ConvocatoriaJugador::getJugadorId).containsExactly(101L);
        assertThat(dao.obtenerPorConvocatoria(2L)).hasSize(1);
    }

    @Test
    void existePorJugador() {
        convocar(1, 100);

        assertThat(dao.existePorJugador(100L)).isTrue();
        assertThat(dao.existePorJugador(101L)).isFalse();
    }
}
