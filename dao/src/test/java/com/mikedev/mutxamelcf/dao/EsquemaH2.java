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
 * sql/2026-11-01_cuentas_sin_jugadores.sql (igual con USUARIOS_APP) y
 * sql/2026-11-02_convocatoria_invitados.sql (columnas de invitado en CONVOCATORIA_JUGADOR) y
 * sql/2026-11-04_idioma_usuario_app.sql (columna IDIOMA de USUARIOS_APP, igual que arriba) y
 * sql/2026-11-03_equipajes_convocatoria.sql (tabla EQUIPACIONES y, si el test ya tiene CONVOCATORIAS, sus columnas de
 * equipaje) y
 * sql/2026-11-12_ficha_partido.sql (PARTIDOS.FORMACION si la tabla existe, PARTIDO_ALINEACION, y en
 * PARTIDO_LIVE_EVENTOS JUGADOR_ENTRA_ID y los tipos CAMBIO y TARJETA_*) y
 * sql/2026-11-16_eventos_rival.sql (RIVAL y DORSAL_RIVAL de PARTIDO_LIVE_EVENTOS) y
 * sql/2026-11-17_escudos_rivales.sql (CLUBES_RIVALES y CLUBES_RIVALES_ALIAS, y CLUB_RIVAL_ID de PARTIDOS y CODIGO_CLUB de
 * CLASIFICACIONES si el test ya las ha creado). Los tests no deben definir su propia versión de estas tablas.
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
        ejecutar(jdbc, "/h2/convocatoria_invitados.sql");
        ejecutar(jdbc, "/h2/equipajes_convocatoria.sql");
        ejecutar(jdbc, "/h2/idioma_usuario_app.sql");
        ejecutar(jdbc, "/h2/ficha_partido.sql");
        ejecutar(jdbc, "/h2/eventos_rival.sql");
        ejecutar(jdbc, "/h2/escudos_rivales.sql");
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

    /**
     * CLUBES_RIVALES y CLUBES_RIVALES_ALIAS, y CLUB_RIVAL_ID de PARTIDOS y CODIGO_CLUB de CLASIFICACIONES si el test ya las
     * ha creado (sql/2026-11-17_escudos_rivales.sql). {@link #crear} ya lo hace: esto es para los tests que no la usan.
     */
    public static void crearEscudosRivales(JdbcTemplate jdbc) {
        ejecutar(jdbc, "/h2/escudos_rivales.sql");
    }

    /** SOLICITUDES_FAMILIA y SOLICITUDES_FAMILIA_JUGADOR (sql/2026-11-18_solicitudes_familia.sql), sin claves ajenas. */
    public static void crearSolicitudesFamilia(JdbcTemplate jdbc) {
        ejecutar(jdbc, "/h2/solicitudes_familia.sql");
    }

    /** IMAGENES_REDES_FONDOS (sql/2026-11-19_imagenes_redes_fondos.sql), sin permisos ni sinónimos. */
    public static void crearImagenesRedesFondos(JdbcTemplate jdbc) {
        ejecutar(jdbc, "/h2/imagenes_redes_fondos.sql");
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
