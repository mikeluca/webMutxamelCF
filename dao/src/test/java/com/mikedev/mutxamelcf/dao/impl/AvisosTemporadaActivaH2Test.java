package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.model.Comunicacion;

/** En la app solo se ven los avisos de equipo y de categoría de la temporada activa; los chats privados, siempre. */
class AvisosTemporadaActivaH2Test {

    private static final long EQUIPO = 1;

    private JdbcTemplate jdbc;
    private ComunicacionDaoImpl dao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

        jdbc.execute("CREATE TABLE TEMPORADAS (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE,"
                + " FECHA_FIN DATE, ACTIVA NUMBER(1))");
        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, CATEGORIA VARCHAR2(100))");
        jdbc.execute("""
                CREATE TABLE COMUNICACIONES (ID NUMBER PRIMARY KEY, TITULO VARCHAR2(200), CONTENIDO VARCHAR2(1000),
                    USUARIO_AUTOR_ID NUMBER, FECHA_CREACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FECHA_PUBLICACION TIMESTAMP, ACTIVA NUMBER(1) DEFAULT 1, TIPO VARCHAR2(10) DEFAULT 'GRUPAL')""");
        jdbc.execute("CREATE TABLE COMUNICACION_EQUIPO (COMUNICACION_ID NUMBER, EQUIPO_ID NUMBER)");
        jdbc.execute("CREATE TABLE COMUNICACION_CATEGORIA (COMUNICACION_ID NUMBER, CATEGORIA VARCHAR2(100))");
        jdbc.execute("CREATE TABLE COMUNICACION_USUARIO (COMUNICACION_ID NUMBER, USUARIO_APP_ID NUMBER)");

        jdbc.update("INSERT INTO EQUIPO VALUES (?, 'Alevín')", EQUIPO);

        dao = new ComunicacionDaoImpl(jdbc, mock(RolAppDao.class));
    }

    private void temporadaActiva() {
        jdbc.update("INSERT INTO TEMPORADAS VALUES (2, '2026/2027', ?, ?, 1)", Date.valueOf("2026-07-01"),
                Date.valueOf("2027-06-30"));
    }

    private void comunicacion(long id, String tipo, String publicacion) {
        jdbc.update("INSERT INTO COMUNICACIONES (ID, TITULO, USUARIO_AUTOR_ID, FECHA_PUBLICACION, TIPO) VALUES (?, 'T', 9, ?, ?)",
                id, publicacion == null ? null : Timestamp.valueOf(publicacion), tipo);
    }

    @Test
    void losAvisosDeEquipoYDeCategoriaAnterioresAlInicioDeLaActivaNoSeVen() {
        temporadaActiva();
        comunicacion(1, "GRUPAL", "2026-06-30 23:59:59"); // la víspera del inicio: oculta
        comunicacion(2, "GRUPAL", "2026-07-01 00:00:00"); // el mismo instante del inicio: se ve
        comunicacion(3, "GRUPAL", "2026-10-05 10:00:00");
        comunicacion(4, "GRUPAL", "2025-11-01 10:00:00"); // de la temporada pasada, por categoría: oculta
        jdbc.update("INSERT INTO COMUNICACION_EQUIPO VALUES (1, ?)", EQUIPO);
        jdbc.update("INSERT INTO COMUNICACION_EQUIPO VALUES (2, ?)", EQUIPO);
        jdbc.update("INSERT INTO COMUNICACION_EQUIPO VALUES (3, ?)", EQUIPO);
        jdbc.update("INSERT INTO COMUNICACION_CATEGORIA VALUES (4, 'Alevín')");

        assertThat(dao.obtenerPorEquiposYCategorias(List.of(EQUIPO))).extracting(Comunicacion::getId)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void sinFechaDePublicacionManda_LaDeCreacion() {
        temporadaActiva();
        comunicacion(1, "GRUPAL", null);
        jdbc.update("UPDATE COMUNICACIONES SET FECHA_CREACION = ? WHERE ID = 1", Timestamp.valueOf("2025-01-01 10:00:00"));
        comunicacion(2, "GRUPAL", null);
        jdbc.update("UPDATE COMUNICACIONES SET FECHA_CREACION = ? WHERE ID = 2", Timestamp.valueOf("2026-09-01 10:00:00"));
        jdbc.update("INSERT INTO COMUNICACION_EQUIPO VALUES (1, ?)", EQUIPO);
        jdbc.update("INSERT INTO COMUNICACION_EQUIPO VALUES (2, ?)", EQUIPO);

        assertThat(dao.obtenerPorEquiposYCategorias(List.of(EQUIPO))).extracting(Comunicacion::getId)
                .containsExactly(2L);
    }

    @Test
    void sinTemporadaActivaNoSeFiltraNada() {
        comunicacion(1, "GRUPAL", "2019-01-01 10:00:00");
        comunicacion(2, "GRUPAL", "2026-10-05 10:00:00");
        jdbc.update("INSERT INTO COMUNICACION_EQUIPO VALUES (1, ?)", EQUIPO);
        jdbc.update("INSERT INTO COMUNICACION_EQUIPO VALUES (2, ?)", EQUIPO);

        assertThat(dao.obtenerPorEquiposYCategorias(List.of(EQUIPO))).extracting(Comunicacion::getId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void losChatsPrivadosSeVenSiempreAunqueSeanDeOtraTemporada() {
        temporadaActiva();
        comunicacion(1, "PRIVADA", "2024-03-01 10:00:00");
        jdbc.update("INSERT INTO COMUNICACION_USUARIO VALUES (1, 5)");

        assertThat(dao.obtenerConversacion(9L, 5L)).extracting(Comunicacion::getId).containsExactly(1L);
    }
}
