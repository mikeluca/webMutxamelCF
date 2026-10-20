package com.mikedev.mutxamelcf.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class EsquemaH2Test {

    private static JdbcTemplate nuevaBase() {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    @Test
    void elScriptDeAjustesDeLosTestsEsElDeSql() throws Exception {
        // Maven ejecuta los tests desde la carpeta del módulo.
        String original = Files.readString(Path.of("..", "sql", "2026-10-21_ajustes_post_revision.sql"));

        assertThat(EsquemaH2.ajustes().replace("\r\n", "\n")).isEqualTo(original.replace("\r\n", "\n"));
    }

    @Test
    void elEsquemaTieneLasColumnasAplicadasMasLosAjustes() {
        JdbcTemplate jdbc = nuevaBase();
        EsquemaH2.crear(jdbc);

        List<String> eventos = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'PARTIDO_LIVE_EVENTOS'",
                String.class);

        assertThat(eventos).contains("FECHA_ANULACION", "RESULTADO_ANTERIOR", "GOLES_FAVOR_ANTERIOR",
                "GOLES_CONTRA_ANTERIOR", "AVISO_ANTERIOR", "ESTADISTICAS_GUARDADAS");

        List<String> indices = jdbc.queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = 'CONVOCATORIA_JUGADOR'",
                String.class);

        assertThat(indices).contains("IDX_CONV_JUG_JUGADOR");
    }

    @Test
    void laRestriccionDeEstadisticasGuardadasSeAplica() {
        JdbcTemplate jdbc = nuevaBase();
        EsquemaH2.crear(jdbc);

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> jdbc.update(
                "INSERT INTO PARTIDO_LIVE_EVENTOS (PARTIDO_ID, TIPO, USUARIO_APP_ID, FECHA, ESTADISTICAS_GUARDADAS)"
                        + " VALUES (1, 'INICIO', 1, CURRENT_TIMESTAMP, 2)"));
    }

    @Test
    void elMotivoAdmiteQuinientosCaracteresConAcentos() {
        JdbcTemplate jdbc = nuevaBase();
        EsquemaH2.crear(jdbc);

        jdbc.update("INSERT INTO CONVOCATORIA_JUGADOR (CONVOCATORIA_ID, JUGADOR_ID, RESPUESTA, MOTIVO)"
                + " VALUES (1, 1, 'NO_PUEDE', ?)", "é".repeat(500));

        assertThat(jdbc.queryForObject("SELECT LENGTH(MOTIVO) FROM CONVOCATORIA_JUGADOR", Integer.class))
                .isEqualTo(500);
    }
}
