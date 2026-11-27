package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorDeFamiliar;

/**
 * Los listados del panel (Jugadores y Familiares) con H2 en modo Oracle y datos inventados: el orden Categoría → Equipo →
 * Dorsal, el contador de activos por equipo y los jugadores de todos los familiares en una sola consulta.
 *
 * <p>Equipos (ORDEN = el de la categoría): 1 "Alevín B" (orden C), 2 "Alevín A" (orden C), 3 "Infantil A" (orden D).
 */
class ListadosPanelDaoH2Test {

    private JdbcTemplate jdbc;
    private final AtomicInteger sentencias = new AtomicInteger();
    private JugadorDaoImpl jugadorDao;
    private FamiliarJugadorDaoImpl familiarJugadorDao;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource base = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(base);

        jdbc.execute("""
                CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), CATEGORIA VARCHAR2(100),
                    DEPORTE VARCHAR2(10), GRUPO VARCHAR2(10), ORDEN VARCHAR2(5), MODALIDAD VARCHAR2(5))""");
        jdbc.execute("""
                CREATE TABLE TEMPORADAS (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE,
                    FECHA_FIN DATE, ACTIVA NUMBER(1))""");
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crearPlantillas(jdbc);
        EsquemaH2.crearTransicion(jdbc);
        jdbc.execute("CREATE TABLE FAMILIARES (ID NUMBER PRIMARY KEY)");
        jdbc.execute("CREATE TABLE FAMILIARES_JUGADOR (FAMILIAR_ID NUMBER, JUGADOR_ID NUMBER)");

        equipo(1, "Alevín B", "Alevín", "C");
        equipo(2, "Alevín A", "Alevín", "C");
        equipo(3, "Infantil A", "Infantil", "D");

        // Desordenados a propósito
        jugador(10, "Luis", "Zamora", 3L, 4, null);
        jugador(11, "Eva", "Pérez", 1L, 9, null);
        jugador(12, "Ana", "Mora", 1L, null, null); // sin dorsal: detrás de los que lo tienen
        jugador(13, "Bea", "Ibáñez", 1L, 2, null);
        jugador(14, "Raúl", "Sanz", 2L, 7, null);
        jugador(15, "Pau", "Alonso", 1L, null, null); // sin dorsal: por apellidos (Alonso antes que Mora)
        jugador(16, "Baja", "Última", null, 1, "2026-06-30"); // de baja: al final aunque tenga dorsal 1
        jugador(17, "Baja", "Primera", null, 5, "2026-06-30");

        for (int f = 100; f <= 102; f++) {
            jdbc.update("INSERT INTO FAMILIARES VALUES (?)", f);
        }
        // 100: sin jugadores; 101: uno; 102: dos (uno de ellos de baja)
        jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (101, 11)");
        jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (102, 14)");
        jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (102, 16)");

        DataSource contador = new DelegatingDataSource(base) {
            @Override
            public Connection getConnection() throws SQLException {
                Connection real = super.getConnection();
                return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] { Connection.class }, (proxy, metodo, args) -> {
                            if (metodo.getName().startsWith("prepare") || metodo.getName().equals("createStatement")) {
                                sentencias.incrementAndGet();
                            }
                            try {
                                return metodo.invoke(real, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        });
            }
        };
        JdbcTemplate conContador = new JdbcTemplate(contador);
        jugadorDao = new JugadorDaoImpl(conContador);
        familiarJugadorDao = new FamiliarJugadorDaoImpl(conContador);
    }

    private void equipo(long id, String nombre, String categoria, String orden) {
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA, DEPORTE, GRUPO, ORDEN) VALUES (?, ?, ?, 'F', 'A', ?)", id,
                nombre, categoria, orden);
    }

    private void jugador(long id, String nombre, String apellidos, Long equipoId, Integer dorsal, String baja) {
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, CATEGORIA, DEPORTE, DORSAL, EQUIPO_ID, FECHA_BAJA)"
                + " VALUES (?, ?, ?, 'Cat', 'F', ?, ?, " + (baja == null ? "NULL" : "DATE '" + baja + "'") + ")", id, nombre,
                apellidos, dorsal, equipoId);
    }

    private static List<Long> ids(List<Jugador> jugadores) {
        return jugadores.stream().map(Jugador::getId).toList();
    }

    @Test
    void losActivosSalenPorCategoriaEquipoYDorsalConLosSinDorsalAlFinalPorApellidos() {
        // Alevín (C): Alevín A (14) y luego Alevín B: dorsal 2 y 9, y después sin dorsal por apellidos (Alonso, Mora);
        // por último Infantil A (10)
        assertThat(ids(jugadorDao.obtenerActivos())).containsExactly(14L, 13L, 11L, 15L, 12L, 10L);
    }

    @Test
    void conTodosLasBajasVanAlFinalPorApellidosSinMirarSuDorsal() {
        // Primera (dorsal 5) antes que Última (dorsal 1): por apellidos
        assertThat(ids(jugadorDao.obtenerTodos())).containsExactly(14L, 13L, 11L, 15L, 12L, 10L, 17L, 16L);
    }

    @Test
    void elConteoPorEquipoSoloCuentaLosActivos() {
        assertThat(jugadorDao.contarActivosPorEquipo()).containsOnly(entry(1L, 4), entry(2L, 1), entry(3L, 1));

        jdbc.update("UPDATE JUGADORES SET FECHA_BAJA = DATE '2026-07-01', EQUIPO_ID = NULL WHERE ID = 13");

        assertThat(jugadorDao.contarActivosPorEquipo()).containsEntry(1L, 3);
    }

    @Test
    void losJugadoresDeTodosLosFamiliaresSalenEnUnaSolaConsulta() {
        sentencias.set(0);

        List<JugadorDeFamiliar> relaciones = familiarJugadorDao.obtenerJugadoresDeTodosLosFamiliares();

        assertThat(sentencias.get()).isEqualTo(1);
        // El familiar 100 no tiene jugadores: no sale
        assertThat(relaciones).extracting(JugadorDeFamiliar::familiarId).containsExactly(101L, 102L, 102L);
        JugadorDeFamiliar de101 = relaciones.get(0);
        assertThat(de101.nombreCompleto()).isEqualTo("Eva Pérez");
        assertThat(de101.equipo()).isEqualTo("Alevín B");
        assertThat(de101.equipoId()).isEqualTo(1L);
        assertThat(de101.categoria()).isEqualTo("Alevín");
        assertThat(de101.deBaja()).isFalse();
        // Del 102: primero Sanz (activo) y luego la baja (Última), que no tiene equipo ni categoría
        JugadorDeFamiliar baja = relaciones.get(2);
        assertThat(baja.deBaja()).isTrue();
        assertThat(baja.equipo()).isNull();
        assertThat(baja.equipoId()).isNull();
        assertThat(baja.categoria()).isNull();
    }
}
