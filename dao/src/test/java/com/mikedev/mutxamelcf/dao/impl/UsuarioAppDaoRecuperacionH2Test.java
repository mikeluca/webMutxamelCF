package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.UsuarioApp;

/** SQL de la recuperación de contraseña contra H2 con las columnas de sql/2026-10-25. */
class UsuarioAppDaoRecuperacionH2Test {

    private static final Instant AHORA = Instant.parse("2026-10-25T10:00:00Z");

    private JdbcTemplate jdbc;
    private UsuarioAppDaoImpl dao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
                CREATE TABLE USUARIOS_APP (
                    ID NUMBER PRIMARY KEY,
                    EMAIL VARCHAR(255),
                    PASSWORD_HASH VARCHAR(255),
                    ACTIVO NUMBER(1) DEFAULT 0 NOT NULL,
                    FECHA_ALTA TIMESTAMP,
                    FECHA_ACTIVACION TIMESTAMP,
                    FECHA_ULTIMO_ACCESO TIMESTAMP,
                    TOKEN_ACTIVACION VARCHAR(255),
                    TOKEN_ACTIVACION_EXPIRA TIMESTAMP,
                    INTENTOS_ACTIVACION NUMBER(2) DEFAULT 0 NOT NULL,
                    FECHA_ELIMINACION TIMESTAMP,
                    NOMBRE VARCHAR(100),
                    APELLIDOS VARCHAR(150)
                )
                """);
        EsquemaH2.crear(jdbc);
        jdbc.update("INSERT INTO USUARIOS_APP (ID, EMAIL, PASSWORD_HASH, ACTIVO, TOKEN_ACTIVACION) "
                + "VALUES (1, 'ana@example.com', 'hash-viejo', 1, 'activacion-pendiente')");
        dao = new UsuarioAppDaoImpl(jdbc);
    }

    private UsuarioApp usuario() {
        return dao.obtenerPorId(1);
    }

    @Test
    void guardaElCodigoConSuCaducidadYPonePorDefectoLosIntentosACero() {
        assertThat(usuario().getIntentosRecuperacion()).isZero();

        dao.actualizarTokenRecuperacion(1, "hash", Timestamp.from(AHORA.plusSeconds(1800)));

        UsuarioApp u = usuario();
        assertThat(u.getTokenRecuperacion()).isEqualTo("hash");
        assertThat(u.getFechaExpiracionRecuperacion()).isEqualTo(Timestamp.from(AHORA.plusSeconds(1800)));
        assertThat(u.getIntentosRecuperacion()).isZero();
    }

    @Test
    void consumeIntentosHastaElMaximoYDespuesYaNoAfectaAFilas() {
        dao.actualizarTokenRecuperacion(1, "hash", Timestamp.from(AHORA.plusSeconds(1800)));
        Timestamp ahora = Timestamp.from(AHORA);

        for (int i = 0; i < 5; i++) {
            assertThat(dao.consumirIntentoRecuperacion(1, 5, ahora)).isEqualTo(1);
        }

        assertThat(dao.consumirIntentoRecuperacion(1, 5, ahora)).isZero();
        assertThat(usuario().getIntentosRecuperacion()).isEqualTo(5);
    }

    @Test
    void conCincoIntentosGastadosElCodigoSeBorra() {
        dao.actualizarTokenRecuperacion(1, "hash", Timestamp.from(AHORA.plusSeconds(1800)));
        Timestamp ahora = Timestamp.from(AHORA);

        for (int i = 0; i < 4; i++) {
            dao.consumirIntentoRecuperacion(1, 5, ahora);
            dao.invalidarRecuperacionSiAgotada(1, 5);
        }
        assertThat(usuario().getTokenRecuperacion()).isEqualTo("hash");

        dao.consumirIntentoRecuperacion(1, 5, ahora);
        dao.invalidarRecuperacionSiAgotada(1, 5);

        assertThat(usuario().getTokenRecuperacion()).isNull();
        assertThat(usuario().getFechaExpiracionRecuperacion()).isNull();
    }

    @Test
    void unCodigoCaducadoNoConsumeIntentos() {
        dao.actualizarTokenRecuperacion(1, "hash", Timestamp.from(AHORA.minusSeconds(1)));

        assertThat(dao.consumirIntentoRecuperacion(1, 5, Timestamp.from(AHORA))).isZero();
    }

    @Test
    void unaCuentaInactivaOEliminadaNoConsumeIntentos() {
        dao.actualizarTokenRecuperacion(1, "hash", Timestamp.from(AHORA.plusSeconds(1800)));

        dao.desactivarUsuario(1);
        assertThat(dao.consumirIntentoRecuperacion(1, 5, Timestamp.from(AHORA))).isZero();

        jdbc.update("UPDATE USUARIOS_APP SET ACTIVO = 1, FECHA_ELIMINACION = ? WHERE ID = 1", Timestamp.from(AHORA));
        assertThat(dao.consumirIntentoRecuperacion(1, 5, Timestamp.from(AHORA))).isZero();
    }

    @Test
    void restablecerGuardaElHashBorraLosCodigosYFijaLaFechaDeCambio() {
        dao.actualizarTokenRecuperacion(1, "hash", Timestamp.from(AHORA.plusSeconds(1800)));
        dao.consumirIntentoRecuperacion(1, 5, Timestamp.from(AHORA));

        dao.restablecerPassword(1, "hash-nuevo", Timestamp.from(AHORA));

        UsuarioApp u = usuario();
        assertThat(u.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(u.getTokenRecuperacion()).isNull();
        assertThat(u.getFechaExpiracionRecuperacion()).isNull();
        assertThat(u.getIntentosRecuperacion()).isZero();
        assertThat(u.getFechaCambioPassword()).isEqualTo(Timestamp.from(AHORA));
        assertThat(u.getTokenActivacion()).isNull();
    }
}
