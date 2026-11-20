package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Quién accede a las imágenes para redes (RRSS y ADMIN_APP) y a quién va el recordatorio, con H2 en modo Oracle. */
class EquipoGestionDaoRolesH2Test {

    private JdbcTemplate jdbc;
    private EquipoGestionDaoImpl dao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE USUARIOS_APP (ID NUMBER PRIMARY KEY, ACTIVO NUMBER(1))");
        jdbc.execute("CREATE TABLE ROLES_APP (ID NUMBER PRIMARY KEY, CODIGO VARCHAR2(30))");
        jdbc.execute("CREATE TABLE USUARIOS_APP_ROLES (USUARIO_APP_ID NUMBER, ROL_ID NUMBER)");

        long rol = 1;
        for (String codigo : new String[] { "ENTRENADOR", "COORDINADOR", "ADMIN_APP", "RETRANSMISION", "RRSS" }) {
            jdbc.update("INSERT INTO ROLES_APP VALUES (?, ?)", rol++, codigo);
        }

        cuenta(1, 1, "ENTRENADOR");
        cuenta(2, 1, "COORDINADOR");
        cuenta(3, 1, "ADMIN_APP");
        cuenta(4, 1, "RETRANSMISION");
        cuenta(5, 1, "RRSS");
        cuenta(6, 0, "RRSS"); // inactiva

        dao = new EquipoGestionDaoImpl(jdbc);
    }

    private void cuenta(long id, int activo, String rol) {
        jdbc.update("INSERT INTO USUARIOS_APP VALUES (?, ?)", id, activo);
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES SELECT ?, ID FROM ROLES_APP WHERE CODIGO = ?", id, rol);
    }

    @Test
    void lasImagenesParaRedesSoloLasVenRrssYAdminApp() {
        assertThat(dao.esRrssOAdmin(5L)).isTrue();
        assertThat(dao.esRrssOAdmin(3L)).isTrue();

        assertThat(dao.esRrssOAdmin(4L)).as("RETRANSMISION").isFalse();
        assertThat(dao.esRrssOAdmin(2L)).as("COORDINADOR").isFalse();
        assertThat(dao.esRrssOAdmin(1L)).as("ENTRENADOR").isFalse();
        assertThat(dao.esRrssOAdmin(99L)).as("sin roles").isFalse();
    }

    @Test
    void elRecordatorioVaALasCuentasActivasConRrssYNoALasDeRetransmision() {
        assertThat(dao.obtenerRrss()).containsExactly(5L);
        assertThat(dao.obtenerAdministradoresApp()).containsExactly(3L);
    }

    @Test
    void adminAppGestionaCualquierEquipoYAlgunEquipoSinSerEntrenador() {
        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_CUERPO_TECNICO (USUARIO_APP_ID NUMBER, CUERPO_TECNICO_ID NUMBER)");
        jdbc.execute("CREATE TABLE CUERPO_TECNICO_EQUIPO (CUERPO_TECNICO_ID NUMBER, EQUIPO_ID NUMBER)");
        jdbc.update("INSERT INTO EQUIPO VALUES (10)");

        assertThat(dao.esCoordinadorOAdmin(3L)).isTrue();
        assertThat(dao.puedeGestionarEquipo(3L, 10L)).isTrue();
        assertThat(dao.gestionaAlgunEquipo(3L)).isTrue();

        // RRSS y RETRANSMISION no gestionan equipos.
        assertThat(dao.esCoordinadorOAdmin(5L)).isFalse();
        assertThat(dao.puedeGestionarEquipo(5L, 10L)).isFalse();
        assertThat(dao.gestionaAlgunEquipo(4L)).isFalse();
    }
}
