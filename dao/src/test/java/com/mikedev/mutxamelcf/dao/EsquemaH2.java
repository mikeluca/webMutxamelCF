package com.mikedev.mutxamelcf.dao;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Crea en H2 (modo Oracle) las tablas CONVOCATORIA_JUGADOR,
 * PARTIDO_LIVE_EVENTOS, JUSTIFICACIONES_FALTA_PARTIDO, ENTRENAMIENTOS (con las
 * columnas de pasar lista) y REPORTES_COMUNICACION con el esquema real: el que hay aplicado en DEV y PRO
 * más sql/2026-10-21_ajustes_post_revision.sql,
 * sql/2026-10-22_justificacion_falta_partido.sql (esquema final: CONVOCATORIA_JUGADOR
 * solo con ID, CONVOCATORIA_ID y JUGADOR_ID) y
 * sql/2026-10-23_entrenador_coordinador_admin.sql. Los tests no deben definir su
 * propia versión de estas tablas.
 *
 * <p>Los scripts viven en src/test/resources/h2; el de ajustes es una copia
 * exacta del de sql/ ({@code EsquemaH2Test} comprueba que no se separan).
 * Este módulo publica su jar de tests para que los tests de service lo usen.
 */
public final class EsquemaH2 {

    private EsquemaH2() {
    }

    public static void crear(JdbcTemplate jdbc) {
        ejecutar(jdbc, "/h2/esquema_aplicado.sql");
        ejecutar(jdbc, "/h2/ajustes_post_revision.sql");
        ejecutar(jdbc, "/h2/justificacion_falta_partido.sql");
        ejecutar(jdbc, "/h2/entrenador_coordinador_admin.sql");
    }

    /** El script de ajustes tal cual, para compararlo con el de sql/. */
    public static String ajustes() {
        return leer("/h2/ajustes_post_revision.sql");
    }

    private static void ejecutar(JdbcTemplate jdbc, String recurso) {

        StringBuilder sentencia = new StringBuilder();

        for (String linea : leer(recurso).split("\\R")) {

            if (linea.trim().startsWith("--")) {
                continue;
            }

            sentencia.append(linea).append('\n');

            if (linea.trim().endsWith(";")) {
                String sql = sentencia.toString().trim();
                jdbc.execute(sql.substring(0, sql.length() - 1));
                sentencia.setLength(0);
            }
        }

        if (!sentencia.toString().isBlank()) {
            throw new IllegalStateException("Sentencia sin ';' final en " + recurso);
        }
    }

    private static String leer(String recurso) {

        try (InputStream in = EsquemaH2.class.getResourceAsStream(recurso)) {

            if (in == null) {
                throw new IllegalStateException("No existe el recurso " + recurso);
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
