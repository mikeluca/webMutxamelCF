package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.CuentaSinJugadores;

/** Qué cuentas entran en "sin jugadores activos" y cómo se marcan, con H2 en modo Oracle. */
class CuentaSinJugadoresDaoH2Test {

    private static final LocalDate HOY = LocalDate.of(2027, 3, 10);
    private static final Timestamp AVISO = Timestamp.valueOf("2027-01-05 04:00:00");

    private JdbcTemplate jdbc;
    private CuentaSinJugadoresDaoImpl dao;
    private long siguienteJugador = 1;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

        jdbc.execute("""
                CREATE TABLE USUARIOS_APP (ID NUMBER PRIMARY KEY, EMAIL VARCHAR2(100), NOMBRE VARCHAR2(100),
                    ACTIVO NUMBER(1) DEFAULT 1, FECHA_ELIMINACION TIMESTAMP)""");
        jdbc.execute("CREATE TABLE ROLES_APP (ID NUMBER PRIMARY KEY, CODIGO VARCHAR2(30))");
        jdbc.execute("CREATE TABLE USUARIOS_APP_ROLES (USUARIO_APP_ID NUMBER, ROL_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_JUGADORES (USUARIO_APP_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE FAMILIARES (ID NUMBER PRIMARY KEY)");
        jdbc.execute("CREATE TABLE FAMILIARES_JUGADOR (FAMILIAR_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_FAMILIARES (USUARIO_APP_ID NUMBER, FAMILIAR_ID NUMBER)");
        jdbc.execute("CREATE TABLE TEMPORADAS (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE,"
                + " FECHA_FIN DATE, ACTIVA NUMBER(1))");
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crearPlantillas(jdbc);
        EsquemaH2.crear(jdbc);

        long rol = 1;
        for (String codigo : new String[] { "FAMILIAR", "JUGADOR", "ENTRENADOR", "COORDINADOR", "ADMIN_APP", "RETRANSMISION" }) {
            jdbc.update("INSERT INTO ROLES_APP VALUES (?, ?)", rol++, codigo);
        }

        jdbc.update("INSERT INTO TEMPORADAS VALUES (1, '2026/2027', ?, ?, 1)", Date.valueOf("2026-07-01"),
                Date.valueOf("2027-06-30"));
        jdbc.update("INSERT INTO TEMPORADAS VALUES (2, '2027/2028', ?, ?, 0)", Date.valueOf("2027-07-01"),
                Date.valueOf("2028-06-30"));

        dao = new CuentaSinJugadoresDaoImpl(jdbc);
    }

    private void cuenta(long id, String... roles) {
        jdbc.update("INSERT INTO USUARIOS_APP (ID, EMAIL, NOMBRE) VALUES (?, ?, 'N')", id, "u" + id + "@x.es");

        for (String rol : roles) {
            jdbc.update("INSERT INTO USUARIOS_APP_ROLES SELECT ?, ID FROM ROLES_APP WHERE CODIGO = ?", id, rol);
        }
    }

    private long jugador(boolean deBaja) {
        long id = siguienteJugador++;
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, CATEGORIA, DEPORTE, DORSAL, FECHA_BAJA)"
                + " VALUES (?, 'J', 'G', 'Alevín', 'F', 1, ?)", id, deBaja ? Date.valueOf("2026-09-01") : null);

        return id;
    }

    private void vinculoDirecto(long cuenta, long jugador) {
        jdbc.update("INSERT INTO USUARIOS_APP_JUGADORES VALUES (?, ?)", cuenta, jugador);
    }

    private void vinculoFamiliar(long cuenta, long jugador) {
        jdbc.update("INSERT INTO FAMILIARES VALUES (?)", cuenta);
        jdbc.update("INSERT INTO USUARIOS_APP_FAMILIARES VALUES (?, ?)", cuenta, cuenta);
        jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (?, ?)", cuenta, jugador);
    }

    private java.util.List<Long> ids() {
        return dao.listarSinJugadoresActivos(HOY).stream().map(CuentaSinJugadores::id).toList();
    }

    @Test
    void unaFamiliaConTodosSusJugadoresDeBajaOSinNingunoEntra() {
        cuenta(1, "FAMILIAR");
        vinculoFamiliar(1, jugador(true));
        cuenta(2, "FAMILIAR"); // sin ningún jugador
        cuenta(3, "JUGADOR");
        vinculoDirecto(3, jugador(true));

        assertThat(ids()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void conUnSoloJugadorActivoNoEntra_ConDosHijosUnoDeBajaTampoco() {
        cuenta(1, "FAMILIAR");
        vinculoFamiliar(1, jugador(false));
        cuenta(2, "JUGADOR");
        vinculoDirecto(2, jugador(false));
        cuenta(3, "FAMILIAR");
        long activo = jugador(false);
        jdbc.update("INSERT INTO FAMILIARES VALUES (30)");
        jdbc.update("INSERT INTO USUARIOS_APP_FAMILIARES VALUES (3, 30)");
        jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (30, ?)", activo);
        jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (30, ?)", jugador(true));

        assertThat(ids()).isEmpty();
    }

    @Test
    void conCualquierOtroRolNuncaEntra() {
        cuenta(1, "FAMILIAR", "ENTRENADOR");
        cuenta(2, "FAMILIAR", "COORDINADOR");
        cuenta(3, "JUGADOR", "ADMIN_APP");
        cuenta(4, "FAMILIAR", "RETRANSMISION");
        cuenta(5, "ENTRENADOR");
        cuenta(6); // sin roles

        assertThat(ids()).isEmpty();
    }

    @Test
    void unaPruebaOcultaDeUnaTemporadaVigenteCuentaComoJugadorActivo() {
        // Las pruebas nacen con FECHA_BAJA para ocultarlas, pero su familia no debe recibir el aviso
        cuenta(1, "FAMILIAR");
        long prueba = jugador(true);
        vinculoFamiliar(1, prueba);
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, ESTADO) VALUES (?, 2, 'PRUEBA')", prueba);

        // Una prueba de una temporada ya terminada no protege la cuenta
        cuenta(2, "FAMILIAR");
        long vieja = jugador(true);
        vinculoFamiliar(2, vieja);
        jdbc.update("INSERT INTO TEMPORADAS VALUES (9, '2020/2021', ?, ?, 0)", Date.valueOf("2020-07-01"),
                Date.valueOf("2021-06-30"));
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, ESTADO) VALUES (?, 9, 'PRUEBA')", vieja);

        // Una ficha CONFIRMADO de un jugador de baja tampoco
        cuenta(3, "FAMILIAR");
        long confirmado = jugador(true);
        vinculoFamiliar(3, confirmado);
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, ESTADO) VALUES (?, 2, 'CONFIRMADO')", confirmado);

        assertThat(ids()).containsExactly(2L, 3L);
    }

    @Test
    void lasCuentasEliminadasNoEntran() {
        cuenta(1, "FAMILIAR");
        jdbc.update("UPDATE USUARIOS_APP SET FECHA_ELIMINACION = ? WHERE ID = 1", AVISO);

        assertThat(ids()).isEmpty();
    }

    @Test
    void marcarAvisoSoloUnaVezYSoloSiEstaActiva() {
        cuenta(1, "FAMILIAR");
        cuenta(2, "FAMILIAR");
        jdbc.update("UPDATE USUARIOS_APP SET ACTIVO = 0 WHERE ID = 2");

        assertThat(dao.marcarAviso(1, AVISO)).isEqualTo(1);
        assertThat(dao.marcarAviso(1, Timestamp.valueOf("2027-02-01 04:00:00"))).isZero();
        assertThat(dao.marcarAviso(2, AVISO)).isZero();

        CuentaSinJugadores c = dao.listarSinJugadoresActivos(HOY).get(0);
        assertThat(c.fechaAviso()).isEqualTo(AVISO);
        assertThat(c.activa()).isTrue();
        assertThat(c.desactivadaPorEsto()).isFalse();
    }

    @Test
    void desactivarMarcaQueFueElJobYNoSePuedeSinAviso() {
        cuenta(1, "FAMILIAR");
        cuenta(2, "FAMILIAR");
        dao.marcarAviso(1, AVISO);

        assertThat(dao.desactivar(2)).isZero();
        assertThat(dao.desactivar(1)).isEqualTo(1);
        assertThat(dao.desactivar(1)).isZero();

        CuentaSinJugadores c = dao.listarMarcadas().get(0);
        assertThat(c.activa()).isFalse();
        assertThat(c.desactivadaPorEsto()).isTrue();
        assertThat(c.fechaAviso()).isEqualTo(AVISO);
    }

    @Test
    void reactivarSoloValeParaLasQueDesactivoElJob() {
        cuenta(1, "FAMILIAR");
        cuenta(2, "FAMILIAR"); // desactivada a mano
        jdbc.update("UPDATE USUARIOS_APP SET ACTIVO = 0 WHERE ID = 2");
        dao.marcarAviso(1, AVISO);
        dao.desactivar(1);

        assertThat(dao.reactivar(2)).isZero();
        assertThat(dao.reactivar(1)).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT ACTIVO FROM USUARIOS_APP WHERE ID = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT ACTIVO FROM USUARIOS_APP WHERE ID = 2", Integer.class)).isZero();
        assertThat(dao.listarMarcadas()).isEmpty();
    }

    @Test
    void cancelarAvisoBorraLaMarcaPeroNoTocaUnaCuentaDesactivadaPorElJob() {
        cuenta(1, "FAMILIAR");
        cuenta(2, "FAMILIAR");
        dao.marcarAviso(1, AVISO);
        dao.marcarAviso(2, AVISO);
        dao.desactivar(2);

        assertThat(dao.cancelarAviso(1)).isEqualTo(1);
        assertThat(dao.cancelarAviso(1)).isZero();
        assertThat(dao.cancelarAviso(2)).isZero();

        assertThat(dao.listarMarcadas()).extracting(CuentaSinJugadores::id).containsExactly(2L);
    }
}
