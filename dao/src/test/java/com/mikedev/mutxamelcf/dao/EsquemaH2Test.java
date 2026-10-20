package com.mikedev.mutxamelcf.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EsquemaH2Test {

    private static JdbcTemplate nuevaBase() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        EsquemaH2.crear(jdbc);
        return jdbc;
    }

    private static List<String> columnas(JdbcTemplate jdbc, String tabla) {
        return jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                String.class, tabla);
    }

    @Test
    void elScriptDeAjustesDeLosTestsEsElDeSql() throws Exception {
        // Maven ejecuta los tests desde la carpeta del módulo.
        String original = Files.readString(Path.of("..", "sql", "2026-10-21_ajustes_post_revision.sql"));

        assertThat(EsquemaH2.ajustes().replace("\r\n", "\n")).isEqualTo(original.replace("\r\n", "\n"));
    }

    @Test
    void convocatoriaJugadorQuedaSoloConIdConvocatoriaYJugador() {
        assertThat(columnas(nuevaBase(), "CONVOCATORIA_JUGADOR"))
                .containsExactly("ID", "CONVOCATORIA_ID", "JUGADOR_ID");
    }

    @Test
    void laTablaDeEventosTieneLasColumnasAplicadasMasLosAjustes() {
        JdbcTemplate jdbc = nuevaBase();

        assertThat(columnas(jdbc, "PARTIDO_LIVE_EVENTOS")).contains("FECHA_ANULACION", "RESULTADO_ANTERIOR",
                "GOLES_FAVOR_ANTERIOR", "GOLES_CONTRA_ANTERIOR", "AVISO_ANTERIOR", "ESTADISTICAS_GUARDADAS");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO PARTIDO_LIVE_EVENTOS (PARTIDO_ID, TIPO, USUARIO_APP_ID, FECHA, ESTADISTICAS_GUARDADAS)"
                        + " VALUES (1, 'INICIO', 1, CURRENT_TIMESTAMP, 2)")).isInstanceOf(Exception.class);
    }

    @Test
    void laTablaDeJustificacionesDePartidoTieneElEsquemaDelScript() {
        JdbcTemplate jdbc = nuevaBase();

        assertThat(columnas(jdbc, "JUSTIFICACIONES_FALTA_PARTIDO")).containsExactly(
                "ID", "PARTIDO_ID", "JUGADOR_ID", "USUARIO_APP_ID", "MOTIVO", "FECHA_CREACION");

        List<String> indices = jdbc.queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = 'JUSTIFICACIONES_FALTA_PARTIDO'",
                String.class);

        assertThat(indices).contains("IDX_JUSTIF_PART_JUGADOR");

        String insert = "INSERT INTO JUSTIFICACIONES_FALTA_PARTIDO"
                + " (PARTIDO_ID, JUGADOR_ID, USUARIO_APP_ID, MOTIVO, FECHA_CREACION)"
                + " VALUES (30, 100, 7, ?, CURRENT_TIMESTAMP)";

        // Motivo de 500 caracteres con acentos (VARCHAR2(500 CHAR)).
        jdbc.update(insert, "é".repeat(500));

        // UNIQUE (PARTIDO_ID, JUGADOR_ID)
        assertThatThrownBy(() -> jdbc.update(insert, "otra vez")).isInstanceOf(Exception.class);

        // MOTIVO NOT NULL
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO JUSTIFICACIONES_FALTA_PARTIDO"
                        + " (PARTIDO_ID, JUGADOR_ID, USUARIO_APP_ID, MOTIVO, FECHA_CREACION)"
                        + " VALUES (30, 101, 7, NULL, CURRENT_TIMESTAMP)")).isInstanceOf(Exception.class);
    }
}
