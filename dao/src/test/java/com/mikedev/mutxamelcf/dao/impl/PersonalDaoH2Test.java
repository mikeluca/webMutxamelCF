package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.Jugador;

/**
 * Alta y edición de cuerpo técnico y de jugador contra tablas SIN fecha_nacimiento, poblacion ni
 * nacionalidad (como producción): un INSERT que las use falla aquí.
 */
class PersonalDaoH2Test {

    private JdbcTemplate jdbc;
    private CuerpoTecnicoDaoImpl cuerpoTecnicoDao;
    private JugadorDaoImpl jugadorDao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        EsquemaH2.crearPersonal(jdbc);
        // JugadorDaoImpl lee el equipo de EQUIPO (por EQUIPO_ID)
        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), CATEGORIA VARCHAR2(100),"
                + " DEPORTE VARCHAR2(10), ORDEN VARCHAR2(5))");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA) VALUES (1, 'Cadete A', 'Cadete')");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA) VALUES (2, 'Cadete B', 'Cadete')");

        cuerpoTecnicoDao = new CuerpoTecnicoDaoImpl(jdbc);
        jugadorDao = new JugadorDaoImpl(jdbc);
    }

    @Test
    void lasTablasNoTienenLasColumnasDePrivacidad() {
        for (String columna : new String[] { "FECHA_NACIMIENTO", "POBLACION", "NACIONALIDAD" }) {
            assertThatThrownBy(() -> jdbc.queryForList("SELECT " + columna + " FROM JUGADORES"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.queryForList("SELECT " + columna + " FROM CUERPO_TECNICO"))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    private static CuerpoTecnico cuerpoTecnico() {
        CuerpoTecnico c = new CuerpoTecnico();
        c.setNombre("Ana");
        c.setApellidos("Ruiz");
        c.setCategoria("Cadete");
        c.setDeporte("Futbol 11");
        c.setEquipo("Cadete A");
        c.setPuesto("Entrenador");
        c.setFoto(new byte[] { 1, 2, 3 });
        return c;
    }

    private static Jugador jugador() {
        Jugador j = new Jugador();
        j.setDni("12345678Z");
        j.setNombre("Pablo");
        j.setApellidos("Garcia");
        j.setCategoria("Cadete");
        j.setDeporte("Futbol 11");
        j.setEquipo("Cadete A");
        j.setEquipoId(1L);
        j.setAnioNacimiento(2011);
        j.setDorsal(9);
        j.setPosicion("DEL");
        j.setFoto(new byte[] { 4, 5 });
        return j;
    }

    @Test
    void altaYEdicionDeCuerpoTecnico() {
        CuerpoTecnico nuevo = cuerpoTecnico();
        assertThat(cuerpoTecnicoDao.guardar(nuevo)).isTrue();
        assertThat(nuevo.getId()).isNotNull();
        cuerpoTecnicoDao.reemplazarEquipos(nuevo.getId(),
                java.util.List.of(new com.mikedev.mutxamelcf.model.CuerpoTecnicoEquipo(1L, null)));

        CuerpoTecnico alta = cuerpoTecnicoDao.obtenerTodos().get(0);
        assertThat(alta.getNombre()).isEqualTo("Ana");
        assertThat(alta.getPuesto()).isEqualTo("Entrenador");
        assertThat(alta.getFoto()).containsExactly(1, 2, 3);
        assertThat(alta.getEquipos()).extracting(e -> e.getEquipoNombre()).containsExactly("Cadete A");

        alta.setPuesto("Segundo entrenador");
        assertThat(cuerpoTecnicoDao.guardar(alta)).isTrue();
        cuerpoTecnicoDao.reemplazarEquipos(alta.getId(),
                java.util.List.of(new com.mikedev.mutxamelcf.model.CuerpoTecnicoEquipo(2L, null)));

        assertThat(cuerpoTecnicoDao.obtenerTodos()).hasSize(1);
        CuerpoTecnico editado = cuerpoTecnicoDao.obtenerPorId(alta.getId());
        assertThat(editado.getPuesto()).isEqualTo("Segundo entrenador");
        // El equipo sale de la tabla intermedia (por EQUIPO_ID), no de la copia CUERPO_TECNICO.EQUIPO
        assertThat(editado.getEquipos()).extracting(e -> e.getEquipoNombre()).containsExactly("Cadete B");
        assertThat(cuerpoTecnicoDao.obtenerTodosPorEquipo("Cadete B")).hasSize(1);
        assertThat(cuerpoTecnicoDao.obtenerTodosPorEquipo("Cadete A")).isEmpty();
        assertThat(cuerpoTecnicoDao.obtenerTodosPorCategoria("Cadete")).hasSize(1);
    }

    @Test
    void altaYEdicionDeJugador() {
        assertThat(jugadorDao.guardarJugador(jugador())).isTrue();

        Jugador alta = jugadorDao.obtenerTodos().get(0);
        assertThat(alta.getNombre()).isEqualTo("Pablo");
        assertThat(alta.getDorsal()).isEqualTo(9);
        assertThat(alta.getFoto()).containsExactly(4, 5);
        assertThat(alta.getEquipoId()).isEqualTo(1L);
        assertThat(alta.getEquipo()).isEqualTo("Cadete A");
        assertThat(alta.getAnioNacimiento()).isEqualTo(2011);

        alta.setDorsal(10);
        alta.setPosicion("MED");
        assertThat(jugadorDao.guardarJugador(alta)).isTrue();

        assertThat(jugadorDao.obtenerTodos()).hasSize(1);
        Jugador editado = jugadorDao.obtenerPorId(alta.getId());
        assertThat(editado.getDorsal()).isEqualTo(10);
        assertThat(editado.getPosicion()).isEqualTo("MED");
        assertThat(jugadorDao.obtenerTodosPorEquipoId(1L)).hasSize(1);
        assertThat(jugadorDao.obtenerTodosPorNombreEquipo("Cadete A")).hasSize(1);
    }

    @Test
    void unInsertQueUseLasColumnasRetiradasFalla() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO cuerpo_tecnico (nombre, apellidos, nacionalidad) VALUES ('a', 'b', 'c')"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO jugadores (nombre, fecha_nacimiento, poblacion) VALUES ('a', NULL, 'c')"))
                .isInstanceOf(DataAccessException.class);
    }
}
