package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;

class JustificacionFaltaPartidoDaoH2Test {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 10, 0);

    private JustificacionFaltaPartidoDaoImpl dao;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        EsquemaH2.crear(jdbc);

        jdbc.execute("CREATE TABLE PARTIDOS (ID NUMBER PRIMARY KEY, DIA DATE)");
        jdbc.update("INSERT INTO PARTIDOS VALUES (30, DATE '2026-10-24')");
        jdbc.update("INSERT INTO PARTIDOS VALUES (31, DATE '2026-10-31')");
        jdbc.update("INSERT INTO PARTIDOS VALUES (32, DATE '2026-11-07')");

        dao = new JustificacionFaltaPartidoDaoImpl(jdbc);
    }

    @Test
    void obtienePorJugadorYRangoEnUnaSolaConsulta() {
        crear(30, 100, "Uno");
        crear(31, 100, "Dos");
        crear(32, 100, "Fuera de rango");
        crear(30, 101, "Otro jugador");

        assertThat(dao.obtenerPorJugadorYRango(100L, java.time.LocalDate.of(2026, 10, 24),
                java.time.LocalDate.of(2026, 10, 31)))
                .extracting(JustificacionFaltaPartido::getPartidoId, JustificacionFaltaPartido::getMotivo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(30L, "Uno"),
                        org.assertj.core.groups.Tuple.tuple(31L, "Dos"));

        assertThat(dao.obtenerPorJugadorYRango(100L, java.time.LocalDate.of(2026, 11, 1),
                java.time.LocalDate.of(2026, 11, 30)))
                .extracting(JustificacionFaltaPartido::getPartidoId)
                .containsExactly(32L);
    }

    private JustificacionFaltaPartido crear(long partido, long jugador, String motivo) {
        JustificacionFaltaPartido j = new JustificacionFaltaPartido();
        j.setPartidoId(partido);
        j.setJugadorId(jugador);
        j.setUsuarioAppId(7L);
        j.setMotivo(motivo);
        j.setFechaCreacion(AHORA);
        return dao.crear(j);
    }

    @Test
    void creaYLeePorPartidoYJugador() {
        JustificacionFaltaPartido creada = crear(30, 100, "Examen");

        JustificacionFaltaPartido leida = dao.obtenerPorPartidoYJugador(30L, 100L);

        assertThat(creada.getId()).isNotNull();
        assertThat(leida.getMotivo()).isEqualTo("Examen");
        assertThat(leida.getUsuarioAppId()).isEqualTo(7L);
        assertThat(leida.getFechaCreacion()).isEqualTo(AHORA);
        assertThat(dao.obtenerPorPartidoYJugador(30L, 101L)).isNull();
        assertThat(dao.obtenerPorPartidoYJugador(31L, 100L)).isNull();
    }

    @Test
    void actualizaMotivoAutorYFecha() {
        JustificacionFaltaPartido j = crear(30, 100, "Examen");

        j.setMotivo("Enfermo");
        j.setUsuarioAppId(8L);
        j.setFechaCreacion(AHORA.plusHours(1));
        dao.actualizar(j);

        JustificacionFaltaPartido leida = dao.obtenerPorPartidoYJugador(30L, 100L);

        assertThat(leida.getMotivo()).isEqualTo("Enfermo");
        assertThat(leida.getUsuarioAppId()).isEqualTo(8L);
        assertThat(leida.getFechaCreacion()).isEqualTo(AHORA.plusHours(1));
    }

    @Test
    void listaPorPartidoYEliminaSoloLaIndicada() {
        crear(30, 100, "Examen");
        crear(30, 101, "Enfermo");
        crear(31, 100, "Viaje");

        assertThat(dao.obtenerPorPartido(30L)).extracting(JustificacionFaltaPartido::getJugadorId)
                .containsExactly(100L, 101L);

        assertThat(dao.eliminar(30L, 100L)).isEqualTo(1);
        assertThat(dao.eliminar(30L, 100L)).isZero();

        assertThat(dao.obtenerPorPartido(30L)).hasSize(1);
        assertThat(dao.obtenerPorPartido(31L)).hasSize(1);
    }
}
