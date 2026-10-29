package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;

/** La purga de comunicaciones con H2 en modo Oracle y con las FK reales de las tablas dependientes. */
class PurgaComunicacionesDaoH2Test {

    private JdbcTemplate jdbc;
    private PurgaComunicacionesDaoImpl dao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

        jdbc.execute("CREATE TABLE TEMPORADAS (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE,"
                + " FECHA_FIN DATE, ACTIVA NUMBER(1))");
        jdbc.execute("""
                CREATE TABLE COMUNICACIONES (ID NUMBER PRIMARY KEY, TITULO VARCHAR2(200), CONTENIDO VARCHAR2(1000),
                    USUARIO_AUTOR_ID NUMBER, FECHA_CREACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FECHA_PUBLICACION TIMESTAMP, ACTIVA NUMBER(1) DEFAULT 1, TIPO VARCHAR2(10) DEFAULT 'GRUPAL')""");
        // Las tablas dependientes llevan FK a COMUNICACIONES: si la purga no las vaciara antes, fallaría
        jdbc.execute("CREATE TABLE COMUNICACION_EQUIPO (COMUNICACION_ID NUMBER, EQUIPO_ID NUMBER,"
                + " FOREIGN KEY (COMUNICACION_ID) REFERENCES COMUNICACIONES(ID))");
        jdbc.execute("CREATE TABLE COMUNICACION_CATEGORIA (COMUNICACION_ID NUMBER, CATEGORIA VARCHAR2(100),"
                + " FOREIGN KEY (COMUNICACION_ID) REFERENCES COMUNICACIONES(ID))");
        jdbc.execute("CREATE TABLE COMUNICACION_USUARIO (COMUNICACION_ID NUMBER, USUARIO_APP_ID NUMBER,"
                + " FOREIGN KEY (COMUNICACION_ID) REFERENCES COMUNICACIONES(ID))");
        EsquemaH2.crear(jdbc);

        dao = new PurgaComunicacionesDaoImpl(jdbc);
    }

    private void temporada(long id, String nombre, String inicio, boolean activa) {
        jdbc.update("INSERT INTO TEMPORADAS VALUES (?, ?, ?, ?, ?)", id, nombre, Date.valueOf(inicio),
                Date.valueOf(inicio.substring(0, 4).concat("-12-31")), activa ? 1 : 0);
    }

    private void comunicacion(long id, String publicacion) {
        jdbc.update("INSERT INTO COMUNICACIONES (ID, TITULO, FECHA_PUBLICACION) VALUES (?, 'T', ?)", id,
                Timestamp.valueOf(publicacion));
        jdbc.update("INSERT INTO COMUNICACION_EQUIPO VALUES (?, 1)", id);
        jdbc.update("INSERT INTO COMUNICACION_CATEGORIA VALUES (?, 'Alevín')", id);
        jdbc.update("INSERT INTO COMUNICACION_USUARIO VALUES (?, 5)", id);
    }

    private void reporte(long comunicacionId, String estado, String fechaMensaje) {
        jdbc.update("INSERT INTO REPORTES_COMUNICACION (COMUNICACION_ID, REPORTADOR_ID, FECHA_MENSAJE, FECHA_REPORTE, ESTADO)"
                + " VALUES (?, ?, ?, ?, ?)", comunicacionId, 100 + comunicacionId + estado.length(),
                Timestamp.valueOf(fechaMensaje), Timestamp.valueOf(fechaMensaje), estado);
    }

    private int filas(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    @Test
    void elLimiteEsElInicioDeLaTemporadaDeHaceDosTemporadas() {
        temporada(1, "2025/2026", "2025-07-01", false);
        temporada(2, "2026/2027", "2026-07-01", false);
        temporada(3, "2027/2028", "2027-07-01", false);
        temporada(4, "2028/2029", "2028-07-01", true);

        assertThat(dao.inicioDeHaceDosTemporadas()).isEqualTo(Timestamp.valueOf("2026-07-01 00:00:00"));
    }

    @Test
    void sinDosTemporadasAnterioresONiActivaNoHayLimite() {
        assertThat(dao.inicioDeHaceDosTemporadas()).isNull();

        temporada(1, "2026/2027", "2026-07-01", false);
        temporada(2, "2027/2028", "2027-07-01", true);
        assertThat(dao.inicioDeHaceDosTemporadas()).isNull();

        jdbc.update("UPDATE TEMPORADAS SET ACTIVA = 0");
        temporada(3, "2025/2026", "2025-07-01", false);
        assertThat(dao.inicioDeHaceDosTemporadas()).isNull();
    }

    @Test
    void borraLasAntiguasConSusDependientesYConservaLasRecientes() {
        comunicacion(1, "2026-06-30 23:00:00");
        comunicacion(2, "2025-01-10 10:00:00");
        comunicacion(3, "2026-07-01 00:00:00");
        comunicacion(4, "2027-02-01 10:00:00");

        var borradas = dao.purgarAnterioresA(Timestamp.valueOf("2026-07-01 00:00:00"));

        assertThat(borradas).containsEntry("COMUNICACION_EQUIPO", 2).containsEntry("COMUNICACION_CATEGORIA", 2)
                .containsEntry("COMUNICACION_USUARIO", 2).containsEntry("COMUNICACIONES", 2);
        assertThat(jdbc.queryForList("SELECT ID FROM COMUNICACIONES ORDER BY ID", Long.class)).containsExactly(3L, 4L);
        assertThat(filas("COMUNICACION_EQUIPO")).isEqualTo(2);
        assertThat(filas("COMUNICACION_CATEGORIA")).isEqualTo(2);
        assertThat(filas("COMUNICACION_USUARIO")).isEqualTo(2);
    }

    @Test
    void losReportesPendientesNoSeBorranNiImpidenBorrar_ElResueltoDeEsasFechasSi() {
        comunicacion(1, "2025-01-10 10:00:00");
        comunicacion(2, "2025-02-10 10:00:00");
        reporte(1, "PENDIENTE", "2025-01-10 10:00:00");
        reporte(2, "DESCARTADO", "2025-02-10 10:00:00");
        // Un resuelto reciente y un resuelto de un mensaje que ya no existe, pero de fecha antigua
        reporte(3, "DESCARTADO", "2026-09-01 10:00:00");
        reporte(77, "MENSAJE_BORRADO", "2024-05-01 10:00:00");

        var borradas = dao.purgarAnterioresA(Timestamp.valueOf("2026-07-01 00:00:00"));

        assertThat(borradas).containsEntry("REPORTES_COMUNICACION", 2).containsEntry("COMUNICACIONES", 2);
        assertThat(jdbc.queryForList("SELECT ESTADO FROM REPORTES_COMUNICACION ORDER BY ID", String.class))
                .containsExactly("PENDIENTE", "DESCARTADO");
        assertThat(filas("COMUNICACIONES")).isZero();
    }

    @Test
    void sinNadaAntiguoNoBorraNada() {
        comunicacion(1, "2027-01-10 10:00:00");

        var borradas = dao.purgarAnterioresA(Timestamp.valueOf("2026-07-01 00:00:00"));

        assertThat(borradas.values()).allMatch(n -> n == 0);
        assertThat(filas("COMUNICACIONES")).isEqualTo(1);
    }
}
