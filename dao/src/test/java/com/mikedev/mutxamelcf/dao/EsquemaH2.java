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
 * sql/2026-10-23_entrenador_coordinador_admin.sql y
 * sql/2026-10-24_partidos_fecha_importacion.sql (PARTIDOS no se crea aquí: la columna
 * se añade a la tabla PARTIDOS de cada test, si la tiene) y
 * sql/2026-10-25_recuperar_contrasena.sql (igual con USUARIOS_APP) y
 * sql/2026-10-26_equipo_modalidad.sql (igual con EQUIPO) y
 * sql/2026-10-27_mejoras_roles.sql (igual con USUARIOS_APP_DISPOSITIVOS) y
 * sql/2026-11-01_cuentas_sin_jugadores.sql (igual con USUARIOS_APP). Los tests no deben definir su
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
        ejecutar(jdbc, "/h2/partidos_fecha_importacion.sql");
        ejecutar(jdbc, "/h2/recuperar_contrasena.sql");
        ejecutar(jdbc, "/h2/equipo_modalidad.sql");
        ejecutar(jdbc, "/h2/mejoras_roles.sql");
        ejecutar(jdbc, "/h2/cuentas_sin_jugadores.sql");
    }

    /**
     * JUGADORES y CUERPO_TECNICO sin fecha_nacimiento, poblacion ni nacionalidad. Aparte de {@link #crear}
     * porque varios tests definen su propia JUGADORES reducida sobre el esquema compartido.
     */
    public static void crearPersonal(JdbcTemplate jdbc) {
        ejecutar(jdbc, "/h2/personal.sql");
    }

    /**
     * CATEGORIAS_EDAD y JUGADOR_TEMPORADA (sql/2026-10-29_plantillas_temporada.sql). JUGADORES tiene que existir ya,
     * con EQUIPO_ID, ANIO_NACIMIENTO y FECHA_BAJA (personal.sql las trae).
     */
    public static void crearPlantillas(JdbcTemplate jdbc) {
        ejecutar(jdbc, "/h2/plantillas_temporada.sql");
    }

    /**
     * EQUIPO_TEMPORADA y CUERPO_TECNICO_TEMPORADA, más TEMPORADA_ID obligatorio, la UNIQUE nueva de EQUIPOS_FFCV y el
     * CHECK de estados de CUOTAS_JUGADOR en las tablas CLASIFICACIONES, EQUIPOS_FFCV, HORARIOS_ENTRENAMIENTO y
     * CUOTAS_JUGADOR que el test ya haya creado (sql/2026-10-29_modelo_transicion_temporada.sql). Las columnas nuevas de
     * JUGADOR_TEMPORADA vienen con {@link #crearPlantillas}.
     */
    public static void crearTransicion(JdbcTemplate jdbc) {
        ejecutar(jdbc, "/h2/transicion_temporada.sql");
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
