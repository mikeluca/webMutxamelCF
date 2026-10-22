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
 * El perfil de la app devuelve el ID del equipo de cada jugador, resuelto por
 * el nombre del equipo (JUGADORES.EQUIPO = EQUIPO.NOMBRE, sin distinguir
 * mayúsculas ni espacios).
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
                    DORSAL NUMBER, POSICION VARCHAR2(50))""");
        jdbc.execute("CREATE TABLE USUARIOS_APP_JUGADORES (USUARIO_APP_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE FAMILIARES_JUGADOR (FAMILIAR_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_FAMILIARES (USUARIO_APP_ID NUMBER, FAMILIAR_ID NUMBER)");

        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (5, 'Cadete A')");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (8, 'Alevín B')");

        // Un familiar (FAMILIAR_ID 100) del usuario 1 con dos hijos en equipos distintos.
        jdbc.update("INSERT INTO USUARIOS_APP_FAMILIARES VALUES (1, 100)");
        jugador(10, "Pablo", "Cadete A");
        jugador(11, "Lucia", " alevín b ");
        jugador(12, "SinEquipo", "Equipo inexistente");
        for (int id : new int[] { 10, 11, 12 }) {
            jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (100, ?)", id);
        }

        dao = new PerfilAppDaoImpl(jdbc);
    }

    private void jugador(int id, String nombre, String equipo) {
        jdbc.update("INSERT INTO JUGADORES VALUES (?, ?, 'X', 'Cat', 'F', ?, 7, 'DEL')", id, nombre, equipo);
    }

    @Test
    void cadaJugadorTraeElIdDeSuEquipo() {
        List<Jugador> jugadores = dao.obtenerJugadoresPorUsuario(1);

        assertThat(jugadores).extracting(j -> j.getNombre() + ":" + j.getEquipoId())
                .containsExactly("Lucia:8", "Pablo:5", "SinEquipo:null");
    }

}
