package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.mikedev.mutxamelcf.dao.ComunicacionDao;
import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.dao.impl.ComunicacionDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.ReporteComunicacionDaoImpl;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.ModeracionService;
import com.mikedev.mutxamelcf.service.NotificacionAppService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * Integración con un contexto Spring de verdad (proxies @Transactional y gestor de
 * transacciones sobre H2), sin mocks en la cadena de transacciones: la moderación,
 * el servicio real de comunicaciones y los DAO reales.
 *
 * <p>Lo que se comprueba: borrar un mensaje que ya no existe NO deja la transacción
 * en rollback-only (la excepción de ComunicacionService.eliminar, que es
 * @Transactional, acababa en UnexpectedRollbackException, un 500, al hacer commit).
 */
class ModeracionTransaccionalTest {

    private static final Long ADMIN = 1L;
    private static final Long AUTOR = 20L;
    private static final Long REPORTADOR = 10L;

    @Configuration
    @EnableTransactionManagement
    static class Config {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        UsuarioAppService usuarioAppService() {
            UsuarioAppService servicio = mock(UsuarioAppService.class);

            when(servicio.tieneRol(ADMIN.intValue(), "ADMIN_APP")).thenReturn(true);
            when(servicio.obtenerNombreMostrable(anyInt())).thenReturn("Usuario");

            UsuarioApp autor = new UsuarioApp();
            autor.setId(AUTOR.intValue());
            autor.setActivo(true);
            when(servicio.obtenerPorId(AUTOR.intValue())).thenReturn(autor);

            return servicio;
        }

        @Bean
        ComunicacionDao comunicacionDao(JdbcTemplate jdbcTemplate) {
            return new ComunicacionDaoImpl(jdbcTemplate, mock(RolAppDao.class));
        }

        @Bean
        ComunicacionService comunicacionService(
                ComunicacionDao comunicacionDao, UsuarioAppService usuarioAppService,
                ApplicationEventPublisher publicador) {

            // El servicio REAL, con su @Transactional.
            return new ComunicacionServiceImpl(
                    comunicacionDao, usuarioAppService, mock(NotificacionAppService.class),
                    mock(UsuarioAppVinculoDao.class), publicador);
        }

        @Bean
        ModeracionService moderacionService(
                JdbcTemplate jdbcTemplate, ComunicacionDao comunicacionDao,
                ComunicacionService comunicacionService, UsuarioAppService usuarioAppService) {

            EquipoGestionDao equipoGestionDao = mock(EquipoGestionDao.class);

            return new ModeracionServiceImpl(
                    new ReporteComunicacionDaoImpl(jdbcTemplate), comunicacionDao, comunicacionService,
                    usuarioAppService, equipoGestionDao, mock(DispositivoAppDao.class), mock(FcmPushService.class),
                    Clock.fixed(LocalDateTime.of(2026, 10, 21, 12, 0).atZone(ZoneId.of("Europe/Madrid")).toInstant(),
                            ZoneId.of("Europe/Madrid")));
        }
    }

    private AnnotationConfigApplicationContext contexto;
    private JdbcTemplate jdbc;
    private ModeracionService moderacion;

    @BeforeEach
    void setUp() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        jdbc = contexto.getBean(JdbcTemplate.class);
        moderacion = contexto.getBean(ModeracionService.class);

        EsquemaH2.crear(jdbc);

        jdbc.execute("""
                CREATE TABLE COMUNICACIONES (
                    ID NUMBER PRIMARY KEY, TITULO VARCHAR2(300), CONTENIDO CLOB, USUARIO_AUTOR_ID NUMBER,
                    FECHA_CREACION TIMESTAMP, FECHA_PUBLICACION TIMESTAMP, ACTIVA NUMBER(1) DEFAULT 1, TIPO VARCHAR2(20))""");
        jdbc.execute("CREATE TABLE COMUNICACION_EQUIPO (COMUNICACION_ID NUMBER)");
        jdbc.execute("CREATE TABLE COMUNICACION_CATEGORIA (COMUNICACION_ID NUMBER)");
        jdbc.execute("CREATE TABLE COMUNICACION_USUARIO (COMUNICACION_ID NUMBER)");
    }

    @AfterEach
    void tearDown() {
        contexto.close();
    }

    private void mensaje(long id) {
        jdbc.update("INSERT INTO COMUNICACIONES (ID, TITULO, CONTENIDO, USUARIO_AUTOR_ID, FECHA_CREACION, TIPO)"
                + " VALUES (?, 'Título', 'Texto', ?, CURRENT_TIMESTAMP, 'GRUPAL')", id, AUTOR);
    }

    private Long reportar(long comunicacionId) {
        Comunicacion c = new Comunicacion();
        c.setId(comunicacionId);
        c.setTitulo("Título");
        c.setContenido("Texto");
        c.setUsuarioAutorId(AUTOR);
        c.setFechaCreacion(LocalDateTime.of(2026, 10, 20, 9, 0));

        return moderacion.registrarReporte(c, REPORTADOR, "Insultos");
    }

    private String estado(Long reporteId) {
        return jdbc.queryForObject("SELECT ESTADO FROM REPORTES_COMUNICACION WHERE ID = ?", String.class, reporteId);
    }

    private int mensajes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM COMUNICACIONES", Integer.class);
    }

    @Test
    void borrarUnMensajeQueYaNoExisteResuelveElReporteSinUnexpectedRollback() {
        // El mensaje 40 no existe (ya lo borró su autor).
        Long id = reportar(40);

        moderacion.borrarMensaje(ADMIN, id, "Ya estaba borrado");

        // Commit correcto: el cambio es visible en la base de datos.
        assertThat(estado(id)).isEqualTo("MENSAJE_BORRADO");
    }

    @Test
    void borrarUnMensajeQueExisteLoBorraYResuelveElReporte() {
        mensaje(41);
        Long id = reportar(41);

        moderacion.borrarMensaje(ADMIN, id, null);

        assertThat(mensajes()).isZero();
        assertThat(estado(id)).isEqualTo("MENSAJE_BORRADO");
    }

    @Test
    void desactivarAutorConBorrarMensajeYElMensajeInexistenteTambienHaceCommit() {
        Long id = reportar(42);

        moderacion.desactivarAutor(ADMIN, id, "Reincidente", true);

        assertThat(estado(id)).isEqualTo("AUTOR_DESACTIVADO");
        assertThat(jdbc.queryForObject(
                "SELECT NOTA_RESOLUCION FROM REPORTES_COMUNICACION WHERE ID = ?", String.class, id))
                .isEqualTo("Reincidente");
    }

    @Test
    void desactivarAutorConBorrarMensajeBorraElMensajeQueExiste() {
        mensaje(43);
        Long id = reportar(43);

        moderacion.desactivarAutor(ADMIN, id, null, true);

        assertThat(mensajes()).isZero();
        assertThat(estado(id)).isEqualTo("AUTOR_DESACTIVADO");
    }

    @Test
    void siOtroModeradorYaResolvioElReporteSeRechazaYNoSeBorraNada() {
        mensaje(44);
        Long id = reportar(44);

        moderacion.descartar(ADMIN, id, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> moderacion.borrarMensaje(ADMIN, id, null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(mensajes()).isEqualTo(1);
        assertThat(estado(id)).isEqualTo("DESCARTADO");
    }

    @Test
    void unaAccionQueFallaDeshaceTodoElCambioDeLaTransaccion() {
        mensaje(45);
        Long id = reportar(45);

        // El autor es ADMIN_APP: se rechaza la desactivación y no debe quedar nada a medias.
        when(contexto.getBean(UsuarioAppService.class).tieneRol(AUTOR.intValue(), "ADMIN_APP")).thenReturn(true);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> moderacion.desactivarAutor(ADMIN, id, null, true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(mensajes()).isEqualTo(1);
        assertThat(estado(id)).isEqualTo("PENDIENTE");
    }
}
