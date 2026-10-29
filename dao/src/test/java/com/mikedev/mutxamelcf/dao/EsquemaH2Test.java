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
    void entrenamientosTieneLasColumnasDePasarLista() {
        JdbcTemplate jdbc = nuevaBase();

        assertThat(columnas(jdbc, "ENTRENAMIENTOS")).contains(
                "LISTA_REVISADA", "FECHA_REVISION", "USUARIO_REVISION_ID", "AVISO_LISTA_ENVIADO");

        jdbc.update("INSERT INTO ENTRENAMIENTOS (EQUIPO_ID, FECHA, USUARIO_ENTRENADOR_ID)"
                + " VALUES (5, CURRENT_DATE, 1)");

        // Por defecto, sin revisar y sin avisar.
        assertThat(jdbc.queryForObject(
                "SELECT LISTA_REVISADA * 10 + AVISO_LISTA_ENVIADO FROM ENTRENAMIENTOS", Integer.class)).isZero();

        // CHECK (LISTA_REVISADA IN (0, 1)) y CHECK (AVISO_LISTA_ENVIADO IN (0, 1))
        assertThatThrownBy(() -> jdbc.update("UPDATE ENTRENAMIENTOS SET LISTA_REVISADA = 2")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE ENTRENAMIENTOS SET AVISO_LISTA_ENVIADO = 2")).isInstanceOf(Exception.class);
    }

    @Test
    void partidosGanaFechaImportacionSiLaTablaExisteYSinTablaNoFalla() {
        // Sin PARTIDOS (la mayoría de los tests): el esquema se crea igual.
        nuevaBase();

        // Con una tabla PARTIDOS ya creada por el test: la columna aparece.
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE PARTIDOS (ID NUMBER PRIMARY KEY, ORIGEN VARCHAR2(10))");

        EsquemaH2.crear(jdbc);

        assertThat(columnas(jdbc, "PARTIDOS")).contains("FECHA_IMPORTACION");

        // Y si el test ya la había declarado, tampoco choca.
        JdbcTemplate conColumna = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        conColumna.execute("CREATE TABLE PARTIDOS (ID NUMBER PRIMARY KEY, FECHA_IMPORTACION TIMESTAMP)");

        EsquemaH2.crear(conColumna);
    }

    @Test
    void elScriptDeFechaImportacionDeSqlEsElDeLosTestsSalvoElUpdateYElIfExists() throws Exception {
        String original = Files.readString(Path.of("..", "sql", "2026-10-24_partidos_fecha_importacion.sql"));

        assertThat(original).startsWith("-- NO EJECUTAR: ya aplicado")
                .contains("ALTER TABLE PARTIDOS ADD (FECHA_IMPORTACION TIMESTAMP NULL);")
                .contains("UPDATE PARTIDOS SET FECHA_IMPORTACION = FECHA_CREACION WHERE ORIGEN = 'FFCV'"
                        + " AND FECHA_IMPORTACION IS NULL;");
    }

    @Test
    void elScriptDeModalidadDeSqlEsElDeLosTestsSalvoElIfExists() throws Exception {
        String original = Files.readString(Path.of("..", "sql", "2026-10-26_equipo_modalidad.sql"));

        assertThat(original).startsWith("-- NO EJECUTAR: ya aplicado")
                .contains("ALTER TABLE EQUIPO ADD (MODALIDAD VARCHAR2(3) NULL);")
                .contains("CHECK (MODALIDAD IN ('F11', 'F8', 'F5'));");

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY)");
        EsquemaH2.crear(jdbc);

        assertThat(columnas(jdbc, "EQUIPO")).contains("MODALIDAD");
    }

    @Test
    void elModeloDeTransicionTieneLasColumnasYLasTablasDelScript() {
        JdbcTemplate jdbc = nuevaBase();
        jdbc.execute("CREATE TABLE EQUIPOS_FFCV (ID NUMBER PRIMARY KEY, EQUIPO_ID NUMBER, COMPETICION VARCHAR2(200),"
                + " LETRA VARCHAR2(2), CONSTRAINT UQ_EQUIPOS_FFCV UNIQUE (COMPETICION, LETRA))");
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crearPlantillas(jdbc);
        EsquemaH2.crearTransicion(jdbc);

        assertThat(columnas(jdbc, "JUGADOR_TEMPORADA")).contains("ESTADO", "CONTINUIDAD", "FECHA_CONTINUIDAD",
                "USUARIO_APP_CONTINUIDAD_ID", "EQUIPO_DEFINITIVO");
        assertThat(columnas(jdbc, "EQUIPO_TEMPORADA")).containsExactly(
                "ID", "EQUIPO_ID", "TEMPORADA_ID", "ACTIVO", "MODALIDAD", "TIPO");
        assertThat(columnas(jdbc, "CUERPO_TECNICO_TEMPORADA")).containsExactly(
                "ID", "CUERPO_TECNICO_ID", "TEMPORADA_ID", "EQUIPO_ID", "PUESTO");
        assertThat(columnas(jdbc, "EQUIPOS_FFCV")).contains("TEMPORADA_ID");

        // CHECK de TIPO y de MODALIDAD, y UNIQUE (EQUIPO_ID, TEMPORADA_ID)
        jdbc.update("INSERT INTO EQUIPO_TEMPORADA (EQUIPO_ID, TEMPORADA_ID, MODALIDAD) VALUES (1, 2, 'F11')");
        assertThat(jdbc.queryForObject("SELECT ACTIVO * 10 FROM EQUIPO_TEMPORADA", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT TIPO FROM EQUIPO_TEMPORADA", String.class)).isEqualTo("NORMAL");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO EQUIPO_TEMPORADA (EQUIPO_ID, TEMPORADA_ID) VALUES (1, 2)"))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE EQUIPO_TEMPORADA SET TIPO = 'OTRO'")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE EQUIPO_TEMPORADA SET MODALIDAD = 'F7'"))
                .isInstanceOf(Exception.class);

        // UNIQUE (CUERPO_TECNICO_ID, TEMPORADA_ID, EQUIPO_ID)
        String ficha = "INSERT INTO CUERPO_TECNICO_TEMPORADA (CUERPO_TECNICO_ID, TEMPORADA_ID, EQUIPO_ID) VALUES (1, 2, 3)";
        jdbc.update(ficha);
        assertThatThrownBy(() -> jdbc.update(ficha)).isInstanceOf(Exception.class);

        // CHECK de ESTADO, CONTINUIDAD y EQUIPO_DEFINITIVO en JUGADOR_TEMPORADA
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS) VALUES (10, 'A', 'B')");
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID) VALUES (10, 2)");
        assertThat(jdbc.queryForObject("SELECT ESTADO || CONTINUIDAD || EQUIPO_DEFINITIVO FROM JUGADOR_TEMPORADA",
                String.class)).isEqualTo("CONFIRMADOPENDIENTE1");
        assertThatThrownBy(() -> jdbc.update("UPDATE JUGADOR_TEMPORADA SET ESTADO = 'OTRO'")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE JUGADOR_TEMPORADA SET CONTINUIDAD = 'OTRO'"))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE JUGADOR_TEMPORADA SET EQUIPO_DEFINITIVO = 2"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void elModeloDeTransicionNoFallaSinLasTablasQueModifica() {
        JdbcTemplate jdbc = nuevaBase();

        EsquemaH2.crearTransicion(jdbc);

        assertThat(columnas(jdbc, "EQUIPO_TEMPORADA")).isNotEmpty();
    }

    @Test
    void elScriptDeTransicionDeSqlTieneLasMismasSentenciasQueElDeLosTests() throws Exception {
        String original = Files.readString(Path.of("..", "sql", "2026-10-29_modelo_transicion_temporada.sql"));

        assertThat(original)
                .contains("CK_JT_ESTADO", "CK_JT_CONTINUIDAD", "CK_JT_EQUIPO_DEFINITIVO", "CK_JT_BAJA_ESTADO",
                        "CREATE TABLE EQUIPO_TEMPORADA", "CREATE TABLE CUERPO_TECNICO_TEMPORADA",
                        "UNIQUE (EQUIPO_ID, TEMPORADA_ID)", "UNIQUE (CUERPO_TECNICO_ID, TEMPORADA_ID, EQUIPO_ID)",
                        "UNIQUE (TEMPORADA_ID, COMPETICION, LETRA)",
                        "CHECK (ESTADO IN ('PENDIENTE', 'PARCIAL', 'PAGADO', 'INCOBRABLE'))",
                        "GRANT SELECT, INSERT, UPDATE, DELETE ON EQUIPO_TEMPORADA TO USER_WEB",
                        "GRANT SELECT, INSERT, UPDATE, DELETE ON CUERPO_TECNICO_TEMPORADA TO USER_WEB",
                        "CREATE SYNONYM USER_WEB.EQUIPO_TEMPORADA", "CREATE SYNONYM USER_WEB.CUERPO_TECNICO_TEMPORADA");
        for (String tabla : List.of("CLASIFICACIONES", "EQUIPOS_FFCV", "HORARIOS_ENTRENAMIENTO")) {
            assertThat(original).contains("ALTER TABLE " + tabla + " ADD (TEMPORADA_ID NUMBER NULL);",
                    "ALTER TABLE " + tabla + " MODIFY (TEMPORADA_ID NOT NULL);");
        }
    }

    @Test
    void laTablaDeReportesDeComunicacionTieneElEsquemaDelScript() {
        JdbcTemplate jdbc = nuevaBase();

        assertThat(columnas(jdbc, "REPORTES_COMUNICACION")).containsExactly(
                "ID", "COMUNICACION_ID", "AUTOR_ID", "REPORTADOR_ID", "MOTIVO", "TITULO_COPIA", "CONTENIDO_COPIA",
                "FECHA_MENSAJE", "FECHA_REPORTE", "ESTADO", "RESUELTO_POR_ID", "FECHA_RESOLUCION", "NOTA_RESOLUCION");

        String insert = "INSERT INTO REPORTES_COMUNICACION (COMUNICACION_ID, REPORTADOR_ID, FECHA_REPORTE)"
                + " VALUES (1, 2, CURRENT_TIMESTAMP)";

        jdbc.update(insert);

        // UNIQUE (COMUNICACION_ID, REPORTADOR_ID)
        assertThatThrownBy(() -> jdbc.update(insert)).isInstanceOf(Exception.class);

        // CHECK de ESTADO
        assertThatThrownBy(() -> jdbc.update("UPDATE REPORTES_COMUNICACION SET ESTADO = 'OTRO'"))
                .isInstanceOf(Exception.class);

        assertThat(jdbc.queryForObject("SELECT ESTADO FROM REPORTES_COMUNICACION", String.class))
                .isEqualTo("PENDIENTE");
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
