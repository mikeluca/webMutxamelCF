package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.IdiomaApp;

/** El idioma de la cuenta (sql/2026-11-04_idioma_usuario_app.sql) contra H2. */
class UsuarioAppDaoIdiomaH2Test {

    private JdbcTemplate jdbc;
    private UsuarioAppDaoImpl dao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE USUARIOS_APP (ID NUMBER PRIMARY KEY, EMAIL VARCHAR(255), "
                + "ACTIVO NUMBER(1) DEFAULT 0 NOT NULL)");
        // EsquemaH2 añade IDIOMA a la tabla (VARCHAR2(2) DEFAULT 'es' NOT NULL), como el script de sql/.
        EsquemaH2.crear(jdbc);
        jdbc.update("INSERT INTO USUARIOS_APP (ID, EMAIL, ACTIVO) VALUES (1, 'ana@example.com', 1)");
        jdbc.update("INSERT INTO USUARIOS_APP (ID, EMAIL, ACTIVO) VALUES (2, 'luis@example.com', 1)");
        jdbc.update("INSERT INTO USUARIOS_APP (ID, EMAIL, ACTIVO) VALUES (3, 'marta@example.com', 1)");
        dao = new UsuarioAppDaoImpl(jdbc);
    }

    @Test
    void lasCuentasExistentesSeQuedanEnCastellano() {
        assertThat(dao.obtenerIdioma(1)).isEqualTo("es");
        assertThat(dao.obtenerIdiomas(List.of(1L, 2L, 3L))).containsEntry(1L, "es").containsEntry(2L, "es")
                .containsEntry(3L, "es");
    }

    @Test
    void guardaCadaIdiomaAdmitido() {
        dao.actualizarIdioma(1, "ca");
        dao.actualizarIdioma(2, "en");

        assertThat(dao.obtenerIdioma(1)).isEqualTo("ca");
        assertThat(dao.obtenerIdioma(2)).isEqualTo("en");
        assertThat(dao.obtenerIdioma(3)).isEqualTo("es");
        assertThat(dao.obtenerIdiomas(List.of(1L, 2L, 3L)))
                .containsEntry(1L, "ca").containsEntry(2L, "en").containsEntry(3L, "es");
    }

    @Test
    void cualquierOtroValorSeGuardaComoCastellano() {
        dao.actualizarIdioma(1, "ca");

        for (String raro : new String[] { "fr", "", "  ", "castellano", "xx-YY", null }) {
            dao.actualizarIdioma(1, "ca");
            dao.actualizarIdioma(1, raro);

            assertThat(dao.obtenerIdioma(1)).as("valor '%s'", raro).isEqualTo("es");
        }

        // Mayúsculas y espacios: se normaliza.
        dao.actualizarIdioma(1, " CA ");
        assertThat(dao.obtenerIdioma(1)).isEqualTo("ca");
    }

    @Test
    void unaCuentaQueNoExisteVaEnCastellanoYNoApareceEnElMapa() {
        assertThat(dao.obtenerIdioma(99)).isEqualTo(IdiomaApp.ES);
        assertThat(dao.obtenerIdiomas(List.of(99L))).isEmpty();
        assertThat(dao.obtenerIdiomas(List.of())).isEmpty();
        assertThat(dao.obtenerIdiomas(null)).isEmpty();
    }

    @Test
    void laConsultaDeVariasCuentasAguantaMasDeMilIds() {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 1500).boxed().toList();

        assertThat(dao.obtenerIdiomas(ids)).hasSize(3);
    }

    @Test
    void idiomaAppSoloAdmiteEsCaEn() {
        // El CHECK del script de Oracle solo admite estos tres: el DAO nunca escribe otro valor.
        assertThat(IdiomaApp.TODOS).containsExactly("es", "ca", "en");
        assertThat(IdiomaApp.normalizar("EN")).isEqualTo("en");
        assertThat(IdiomaApp.normalizar("va")).isEqualTo("es");
    }
}
