package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.model.Jugador;

/**
 * El perfil de la app devuelve el equipo de cada jugador por JUGADORES.EQUIPO_ID. Un jugador sin equipo o de baja
 * sigue saliendo en el perfil, con equipo y equipoId a null.
 */
class PerfilAppDaoJugadoresH2Test {

    private JdbcTemplate jdbc;
    private PerfilAppDaoImpl dao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100))");
        jdbc.execute("""
                CREATE TABLE JUGADORES (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), APELLIDOS VARCHAR2(100),
                    CATEGORIA VARCHAR2(100), DEPORTE VARCHAR2(10), EQUIPO VARCHAR2(100),
                    DORSAL NUMBER, POSICION VARCHAR2(50), EQUIPO_ID NUMBER, FECHA_BAJA DATE)""");
        jdbc.execute("CREATE TABLE USUARIOS_APP_JUGADORES (USUARIO_APP_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE FAMILIARES_JUGADOR (FAMILIAR_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_FAMILIARES (USUARIO_APP_ID NUMBER, FAMILIAR_ID NUMBER)");

        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (5, 'Cadete A')");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (8, 'Alevín B')");

        // Un familiar (FAMILIAR_ID 100) del usuario 1 con cuatro hijos.
        jdbc.update("INSERT INTO USUARIOS_APP_FAMILIARES VALUES (1, 100)");
        jugador(10, "Pablo", "Cadete A", 5L, false);
        jugador(11, "Lucia", "Alevín B", 8L, false);
        jugador(12, "SinEquipo", "Cadete A", null, false);
        jugador(13, "Baja", "Cadete A", null, true);
        for (int id : new int[] { 10, 11, 12, 13 }) {
            jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (100, ?)", id);
        }

        dao = new PerfilAppDaoImpl(jdbc);
    }

    /** {@code copiaEquipo} es el texto de JUGADORES.EQUIPO: puede estar desfasado y no debe influir. */
    private void jugador(int id, String nombre, String copiaEquipo, Long equipoId, boolean baja) {
        jdbc.update("INSERT INTO JUGADORES VALUES (?, ?, 'X', 'Cat', 'F', ?, 7, 'DEL', ?, "
                + (baja ? "CURRENT_DATE" : "NULL") + ")", id, nombre, copiaEquipo, equipoId);
    }

    @Test
    void cadaJugadorTraeElIdDeSuEquipo() {
        List<Jugador> jugadores = dao.obtenerJugadoresPorUsuario(1);

        assertThat(jugadores).extracting(j -> j.getNombre() + ":" + j.getEquipoId())
                .containsExactly("Baja:null", "Lucia:8", "Pablo:5", "SinEquipo:null");
    }

    @Test
    void sinEquipoOEnBajaElEquipoVieneANull() {
        List<Jugador> jugadores = dao.obtenerJugadoresPorUsuario(1);

        assertThat(jugadores).filteredOn(j -> j.getNombre().equals("SinEquipo") || j.getNombre().equals("Baja"))
                .extracting(Jugador::getEquipo).containsOnlyNulls();
    }

    @Test
    void renombrarElEquipoNoRompeElPerfil() {
        jdbc.update("UPDATE EQUIPO SET NOMBRE = 'Cadete B' WHERE ID = 5");

        List<Jugador> jugadores = dao.obtenerJugadoresPorUsuario(1);

        assertThat(jugadores).filteredOn(j -> j.getNombre().equals("Pablo"))
                .extracting(j -> j.getEquipo() + ":" + j.getEquipoId()).containsExactly("Cadete B:5");
    }

}
