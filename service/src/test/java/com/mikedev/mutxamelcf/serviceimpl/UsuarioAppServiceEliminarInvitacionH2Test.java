package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.impl.LimpiezaCuentaAppDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.RolAppDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.UsuarioAppDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.UsuarioAppVinculoDaoImpl;
import com.mikedev.mutxamelcf.service.JwtService;

/**
 * Eliminar una invitación pendiente con los DAO reales sobre H2: se borran los datos propios de la cuenta (solicitudes
 * de familia con sus hijos, notificaciones, preferencias, dispositivos, destinatarios de comunicaciones) y, si tiene
 * actividad del club, no se toca nada.
 *
 * <p>Cuentas: 10 sin nada, 11 creada por una solicitud de familia, 12 con datos propios, 13 con una justificación de
 * falta (actividad), 14 activa.
 */
class UsuarioAppServiceEliminarInvitacionH2Test {

    private JdbcTemplate jdbc;
    private UsuarioAppServiceImpl service;

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
        EsquemaH2.crearSolicitudesFamilia(jdbc);

        // Las tablas que EsquemaH2 no trae, reducidas a las columnas que apuntan a la cuenta.
        for (String tabla : new String[] {
                "USUARIOS_APP_JUGADORES (USUARIO_APP_ID NUMBER, JUGADOR_ID NUMBER)",
                "USUARIOS_APP_FAMILIARES (USUARIO_APP_ID NUMBER, FAMILIAR_ID NUMBER)",
                "USUARIOS_APP_CUERPO_TECNICO (USUARIO_APP_ID NUMBER, CUERPO_TECNICO_ID NUMBER)",
                "USUARIOS_APP_ROLES (USUARIO_APP_ID NUMBER, ROL_ID NUMBER)",
                "NOTIFICACIONES_APP (ID NUMBER, USUARIO_APP_ID NUMBER)",
                "PREFERENCIAS_NOTIFICACION (ID NUMBER, USUARIO_APP_ID NUMBER)",
                "USUARIOS_APP_DISPOSITIVOS (ID NUMBER, USUARIO_APP_ID NUMBER)",
                "COMUNICACIONES (ID NUMBER, USUARIO_AUTOR_ID NUMBER)",
                "COMUNICACION_USUARIO (COMUNICACION_ID NUMBER, USUARIO_APP_ID NUMBER)",
                "JUSTIFICACIONES_FALTA_ENTRENAMIENTO (ID NUMBER, USUARIO_APP_ID NUMBER)",
                "HORARIOS_ENTRENAMIENTO (ID NUMBER, USUARIO_ACTUALIZO_ID NUMBER)",
                "PARTIDOS (ID NUMBER, USUARIO_ACTUALIZO_ID NUMBER)",
                "JUGADOR_TEMPORADA (ID NUMBER, USUARIO_APP_CONTINUIDAD_ID NUMBER)" }) {
            jdbc.execute("CREATE TABLE " + tabla);
        }

        for (int id : new int[] { 10, 11, 12, 13 }) {
            jdbc.update("INSERT INTO USUARIOS_APP (ID, EMAIL, PASSWORD_HASH, ACTIVO) VALUES (?, ?, 'h', 0)",
                    id, "cuenta" + id + "@example.com");
        }
        jdbc.update("INSERT INTO USUARIOS_APP (ID, EMAIL, PASSWORD_HASH, ACTIVO) VALUES (14, 'activa@example.com', 'h', 1)");

        service = new UsuarioAppServiceImpl(new UsuarioAppDaoImpl(jdbc), new UsuarioAppVinculoDaoImpl(jdbc),
                new RolAppDaoImpl(jdbc), new LimpiezaCuentaAppDaoImpl(jdbc), mock(PasswordEncoder.class),
                mock(JwtService.class), Clock.fixed(Instant.parse("2026-11-28T10:00:00Z"), ZoneOffset.UTC));
    }

    private int filas(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    private int filas(String tabla, String columna, int id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE " + columna + " = ?", Integer.class, id);
    }

    private long solicitud(int usuario, String estado, int hijos) {
        jdbc.update("INSERT INTO SOLICITUDES_FAMILIA (USUARIO_APP_ID, NOMBRE, APELLIDOS, EMAIL, PARENTESCO, ESTADO, "
                + "ACEPTA_PRIVACIDAD, DECLARA_REPRESENTANTE) VALUES (?, 'Ana', 'Pons', 'mal@example.com', 'MADRE', ?, 1, 1)",
                usuario, estado);
        long id = jdbc.queryForObject("SELECT MAX(ID) FROM SOLICITUDES_FAMILIA", Long.class);
        for (int i = 0; i < hijos; i++) {
            jdbc.update("INSERT INTO SOLICITUDES_FAMILIA_JUGADOR (SOLICITUD_ID, NOMBRE_JUGADOR, APELLIDOS_JUGADOR) "
                    + "VALUES (?, 'Hijo" + i + "', 'Pons')", id);
        }
        return id;
    }

    @Test
    void unaInvitacionSinNadaAsociadoSeBorra() {
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES (USUARIO_APP_ID, ROL_ID) VALUES (10, 1)");
        jdbc.update("INSERT INTO USUARIOS_APP_JUGADORES (USUARIO_APP_ID, JUGADOR_ID) VALUES (10, 1)");

        service.eliminarInvitacion(10);

        assertThat(filas("USUARIOS_APP", "ID", 10)).isZero();
        assertThat(filas("USUARIOS_APP_ROLES", "USUARIO_APP_ID", 10)).isZero();
        assertThat(filas("USUARIOS_APP_JUGADORES", "USUARIO_APP_ID", 10)).isZero();
        assertThat(filas("USUARIOS_APP")).isEqualTo(4);
    }

    @Test
    void laCuentaCreadaPorUnaSolicitudDeFamiliaSeBorraConSuSolicitudYSusHijos() {
        solicitud(11, "PENDIENTE_EMAIL", 2);
        solicitud(11, "PENDIENTE", 1);
        // La solicitud de otra cuenta no se toca.
        solicitud(12, "PENDIENTE", 1);

        service.eliminarInvitacion(11);

        assertThat(filas("USUARIOS_APP", "ID", 11)).isZero();
        assertThat(filas("SOLICITUDES_FAMILIA", "USUARIO_APP_ID", 11)).isZero();
        assertThat(filas("SOLICITUDES_FAMILIA")).isEqualTo(1);
        assertThat(filas("SOLICITUDES_FAMILIA_JUGADOR")).isEqualTo(1);
    }

    @Test
    void conNotificacionesPreferenciasDispositivoYComunicacionesSeBorraConTodo() {
        jdbc.update("INSERT INTO NOTIFICACIONES_APP (ID, USUARIO_APP_ID) VALUES (1, 12)");
        jdbc.update("INSERT INTO NOTIFICACIONES_APP (ID, USUARIO_APP_ID) VALUES (2, 12)");
        jdbc.update("INSERT INTO NOTIFICACIONES_APP (ID, USUARIO_APP_ID) VALUES (3, 10)");
        jdbc.update("INSERT INTO PREFERENCIAS_NOTIFICACION (ID, USUARIO_APP_ID) VALUES (1, 12)");
        jdbc.update("INSERT INTO USUARIOS_APP_DISPOSITIVOS (ID, USUARIO_APP_ID) VALUES (1, 12)");
        jdbc.update("INSERT INTO COMUNICACION_USUARIO (COMUNICACION_ID, USUARIO_APP_ID) VALUES (5, 12)");
        jdbc.update("INSERT INTO COMUNICACION_USUARIO (COMUNICACION_ID, USUARIO_APP_ID) VALUES (5, 10)");

        service.eliminarInvitacion(12);

        assertThat(filas("USUARIOS_APP", "ID", 12)).isZero();
        assertThat(filas("NOTIFICACIONES_APP", "USUARIO_APP_ID", 12)).isZero();
        assertThat(filas("PREFERENCIAS_NOTIFICACION")).isZero();
        assertThat(filas("USUARIOS_APP_DISPOSITIVOS")).isZero();
        assertThat(filas("COMUNICACION_USUARIO", "USUARIO_APP_ID", 12)).isZero();
        // Lo de otras cuentas sigue ahí.
        assertThat(filas("NOTIFICACIONES_APP")).isEqualTo(1);
        assertThat(filas("COMUNICACION_USUARIO")).isEqualTo(1);
    }

    @Test
    void conActividadDelClubNoSeBorraNadaYElMensajeEsClaro() {
        jdbc.update("INSERT INTO JUSTIFICACIONES_FALTA_PARTIDO (PARTIDO_ID, JUGADOR_ID, USUARIO_APP_ID, MOTIVO, "
                + "FECHA_CREACION) VALUES (1, 1, 13, 'Enfermo', CURRENT_TIMESTAMP)");
        solicitud(13, "PENDIENTE", 1);
        jdbc.update("INSERT INTO NOTIFICACIONES_APP (ID, USUARIO_APP_ID) VALUES (1, 13)");
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES (USUARIO_APP_ID, ROL_ID) VALUES (13, 1)");

        assertThatThrownBy(() -> service.eliminarInvitacion(13))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Esta cuenta tiene actividad registrada (justificaciones de falta a partidos). "
                        + "Desactívala en vez de eliminarla.");

        assertThat(filas("USUARIOS_APP", "ID", 13)).isEqualTo(1);
        assertThat(filas("SOLICITUDES_FAMILIA")).isEqualTo(1);
        assertThat(filas("SOLICITUDES_FAMILIA_JUGADOR")).isEqualTo(1);
        assertThat(filas("NOTIFICACIONES_APP")).isEqualTo(1);
        assertThat(filas("USUARIOS_APP_ROLES")).isEqualTo(1);
        assertThat(filas("JUSTIFICACIONES_FALTA_PARTIDO")).isEqualTo(1);
    }

    @Test
    void haberRevisadoSolicitudesDeOtrosEsActividadPeroLasPropiasNoLoSon() {
        // La cuenta 10 revisó la solicitud de la 11: no se puede eliminar.
        solicitud(11, "PENDIENTE", 0);
        jdbc.update("UPDATE SOLICITUDES_FAMILIA SET REVISADA_POR_USUARIO_APP_ID = 10");

        assertThatThrownBy(() -> service.eliminarInvitacion(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("solicitudes de familia revisadas");
        assertThat(filas("USUARIOS_APP", "ID", 10)).isEqualTo(1);
    }

    @Test
    void unaCuentaActivaSigueDandoElErrorDeSiempre() {
        assertThatThrownBy(() -> service.eliminarInvitacion(14))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya activa");
        assertThat(filas("USUARIOS_APP", "ID", 14)).isEqualTo(1);
    }

    @Test
    void unaCuentaQueNoExisteDaError() {
        assertThatThrownBy(() -> service.eliminarInvitacion(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El usuario no existe");
    }
}
