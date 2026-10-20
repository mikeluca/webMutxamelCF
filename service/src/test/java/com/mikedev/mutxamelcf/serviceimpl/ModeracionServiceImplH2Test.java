package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.DispositivoAppDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.impl.ReporteComunicacionDaoImpl;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ReporteModeracionResponse;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.FcmPushService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/** Cola de moderación con el DAO real sobre H2 (REPORTES_COMUNICACION del esquema compartido). */
class ModeracionServiceImplH2Test {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 12, 0);

    private static final Long ADMIN = 1L;
    private static final Long OTRO_ADMIN = 2L;
    private static final Long COORDINADOR = 3L;
    private static final Long REPORTADOR = 10L;
    private static final Long OTRO_REPORTADOR = 11L;
    private static final Long AUTOR = 20L;

    private JdbcTemplate jdbc;
    private ComunicacionService comunicacionService;
    private com.mikedev.mutxamelcf.dao.ComunicacionDao comunicacionDao;
    private UsuarioAppService usuarioAppService;
    private EquipoGestionDao equipoGestionDao;
    private DispositivoAppDao dispositivoAppDao;
    private FcmPushService fcmPushService;
    private ModeracionServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        EsquemaH2.crear(jdbc);

        comunicacionService = mock(ComunicacionService.class);
        comunicacionDao = mock(com.mikedev.mutxamelcf.dao.ComunicacionDao.class);
        // Por defecto los mensajes existen; un test concreto prueba el caso contrario.
        when(comunicacionDao.obtenerPorId(anyLong())).thenReturn(new Comunicacion());
        usuarioAppService = mock(UsuarioAppService.class);
        equipoGestionDao = mock(EquipoGestionDao.class);
        dispositivoAppDao = mock(DispositivoAppDao.class);
        fcmPushService = mock(FcmPushService.class);

        when(usuarioAppService.tieneRol(ADMIN.intValue(), "ADMIN_APP")).thenReturn(true);
        when(usuarioAppService.tieneRol(OTRO_ADMIN.intValue(), "ADMIN_APP")).thenReturn(true);
        when(equipoGestionDao.obtenerAdministradoresApp()).thenReturn(List.of(ADMIN, OTRO_ADMIN));

        for (long id : new long[] { ADMIN, OTRO_ADMIN, COORDINADOR, REPORTADOR, OTRO_REPORTADOR, AUTOR }) {
            when(usuarioAppService.obtenerNombreMostrable((int) id)).thenReturn("Usuario " + id);

            UsuarioApp usuario = new UsuarioApp();
            usuario.setId((int) id);
            usuario.setActivo(true);
            when(usuarioAppService.obtenerPorId((int) id)).thenReturn(usuario);
        }

        service = new ModeracionServiceImpl(
                new ReporteComunicacionDaoImpl(jdbc), comunicacionDao, comunicacionService, usuarioAppService, equipoGestionDao,
                dispositivoAppDao, fcmPushService, Clock.fixed(AHORA.atZone(MADRID).toInstant(), MADRID));
    }

    private static Comunicacion mensaje(long id, Long autor) {
        Comunicacion c = new Comunicacion();
        c.setId(id);
        c.setTitulo("Título del mensaje");
        c.setContenido("Texto muy ofensivo");
        c.setUsuarioAutorId(autor);
        c.setFechaCreacion(LocalDateTime.of(2026, 10, 20, 9, 0));
        return c;
    }

    private Long reportar(long comunicacion, Long reportador) {
        return service.registrarReporte(mensaje(comunicacion, AUTOR), reportador, "Insultos");
    }

    private String estado(Long reporteId) {
        return jdbc.queryForObject("SELECT ESTADO FROM REPORTES_COMUNICACION WHERE ID = ?", String.class, reporteId);
    }

    // ---- registrar ------------------------------------------------------------------------------

    @Test
    void registrarGuardaLaFilaConLaCopiaDelMensaje() {
        Long id = reportar(40, REPORTADOR);

        assertThat(id).isNotNull();

        var fila = jdbc.queryForMap("SELECT * FROM REPORTES_COMUNICACION WHERE ID = ?", id);

        assertThat(fila.get("ESTADO")).isEqualTo("PENDIENTE");
        assertThat(((Number) fila.get("COMUNICACION_ID")).longValue()).isEqualTo(40L);
        assertThat(((Number) fila.get("AUTOR_ID")).longValue()).isEqualTo(AUTOR);
        assertThat(((Number) fila.get("REPORTADOR_ID")).longValue()).isEqualTo(REPORTADOR);
        assertThat(fila.get("MOTIVO")).isEqualTo("Insultos");
        assertThat(fila.get("TITULO_COPIA")).isEqualTo("Título del mensaje");
        assertThat(jdbc.queryForObject(
                "SELECT CONTENIDO_COPIA FROM REPORTES_COMUNICACION WHERE ID = ?", String.class, id))
                .isEqualTo("Texto muy ofensivo");
        assertThat(((java.sql.Timestamp) fila.get("FECHA_MENSAJE")).toLocalDateTime())
                .isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 0));
        assertThat(((java.sql.Timestamp) fila.get("FECHA_REPORTE")).toLocalDateTime()).isEqualTo(AHORA);
    }

    @Test
    void elMismoUsuarioNoDuplicaElReporteNiVuelveAAvisar() {
        assertThat(reportar(40, REPORTADOR)).isNotNull();

        assertThat(reportar(40, REPORTADOR)).isNull();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM REPORTES_COMUNICACION", Integer.class)).isEqualTo(1);
        // Un solo aviso por administrador (2), no cuatro.
        verify(fcmPushService, org.mockito.Mockito.times(2))
                .enviarNotificacionAUsuario(anyLong(), anyString(), anyString(), anyString(), anyLong(), any());

        // Otro usuario sí puede reportar el mismo mensaje.
        assertThat(reportar(40, OTRO_REPORTADOR)).isNotNull();
    }

    @Test
    void avisaConPushATodosLosAdminsSinElContenidoDelMensaje() {
        Long id = reportar(40, REPORTADOR);

        ArgumentCaptor<String> titulo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> datos = ArgumentCaptor.forClass(Map.class);

        verify(fcmPushService).enviarNotificacionAUsuario(
                eq(ADMIN), eq("REPORTE"), titulo.capture(), texto.capture(), eq(id), datos.capture());
        verify(fcmPushService).enviarNotificacionAUsuario(
                eq(OTRO_ADMIN), eq("REPORTE"), anyString(), anyString(), eq(id), any());

        assertThat(titulo.getValue()).isEqualTo("Nuevo mensaje reportado");
        assertThat(texto.getValue()).doesNotContain("ofensivo").doesNotContain("Título del mensaje");
        assertThat(datos.getValue()).containsEntry("reporteId", String.valueOf(id));
    }

    @Test
    void siElReportadorEsAdminNoSeAvisaASiMismo() {
        reportar(41, ADMIN);

        verify(fcmPushService, never()).enviarNotificacionAUsuario(
                eq(ADMIN), anyString(), anyString(), anyString(), anyLong(), any());
        verify(fcmPushService).enviarNotificacionAUsuario(
                eq(OTRO_ADMIN), anyString(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void siFallaUnPushElReporteQuedaGuardadoYSeAvisaALosDemas() {
        doThrow(new RuntimeException("fcm caído")).when(fcmPushService)
                .enviarNotificacionAUsuario(eq(ADMIN), anyString(), anyString(), anyString(), anyLong(), any());

        Long id = reportar(40, REPORTADOR);

        assertThat(id).isNotNull();
        verify(fcmPushService).enviarNotificacionAUsuario(
                eq(OTRO_ADMIN), anyString(), anyString(), anyString(), anyLong(), any());
    }

    // ---- listar ---------------------------------------------------------------------------------

    @Test
    void listaPendientesYResueltosConOtrosReportesDelAutor() {
        Long uno = reportar(40, REPORTADOR);
        Long dos = reportar(41, REPORTADOR);
        service.descartar(ADMIN, dos, "Era una broma");

        List<ReporteModeracionResponse> pendientes = service.listar(ADMIN, null);

        assertThat(pendientes).hasSize(1);

        ReporteModeracionResponse r = pendientes.get(0);

        assertThat(r.id()).isEqualTo(uno);
        assertThat(r.estado()).isEqualTo("PENDIENTE");
        assertThat(r.contenidoCopia()).isEqualTo("Texto muy ofensivo");
        assertThat(r.reportador().nombre()).isEqualTo("Usuario 10");
        assertThat(r.autor().nombre()).isEqualTo("Usuario 20");
        assertThat(r.autor().activo()).isTrue();
        assertThat(r.otrosReportesDelAutor()).isEqualTo(1);
        assertThat(r.resueltoPor()).isNull();

        List<ReporteModeracionResponse> resueltos = service.listar(ADMIN, "RESUELTOS");

        assertThat(resueltos).hasSize(1);
        assertThat(resueltos.get(0).estado()).isEqualTo("DESCARTADO");
        assertThat(resueltos.get(0).resueltoPor().id()).isEqualTo(ADMIN);
        assertThat(resueltos.get(0).notaResolucion()).isEqualTo("Era una broma");
        assertThat(resueltos.get(0).fechaResolucion()).isEqualTo(AHORA);

        assertThat(service.contarPendientes(ADMIN)).isEqualTo(1);
        assertThatThrownBy(() -> service.listar(ADMIN, "OTRO")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losResueltosSonComoMaximoLosCincuentaUltimos() {
        for (long i = 0; i < 55; i++) {
            service.descartar(ADMIN, reportar(100 + i, REPORTADOR), null);
        }

        assertThat(service.listar(ADMIN, "RESUELTOS")).hasSize(50);
    }

    // ---- acciones -------------------------------------------------------------------------------

    @Test
    void descartarPoneElEstadoYGuardaQuienCuandoYLaNota() {
        Long id = reportar(40, REPORTADOR);

        service.descartar(ADMIN, id, "  Sin motivo  ");

        var fila = jdbc.queryForMap("SELECT * FROM REPORTES_COMUNICACION WHERE ID = ?", id);

        assertThat(fila.get("ESTADO")).isEqualTo("DESCARTADO");
        assertThat(((Number) fila.get("RESUELTO_POR_ID")).longValue()).isEqualTo(ADMIN);
        assertThat(fila.get("NOTA_RESOLUCION")).isEqualTo("Sin motivo");
        assertThat(fila.get("FECHA_RESOLUCION")).isNotNull();
        verify(comunicacionService, never()).eliminar(anyLong(), anyLong());
    }

    @Test
    void borrarMensajeLoBorraYResuelveTambienLosDemasReportesPendientesDelMismoMensaje() {
        Long uno = reportar(40, REPORTADOR);
        Long dos = reportar(40, OTRO_REPORTADOR);
        Long ajeno = reportar(41, REPORTADOR);

        service.borrarMensaje(ADMIN, uno, null);

        verify(comunicacionService).eliminar(40L, ADMIN);
        assertThat(estado(uno)).isEqualTo("MENSAJE_BORRADO");
        assertThat(estado(dos)).isEqualTo("MENSAJE_BORRADO");
        assertThat(estado(ajeno)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject(
                "SELECT RESUELTO_POR_ID FROM REPORTES_COMUNICACION WHERE ID = ?", Long.class, dos)).isEqualTo(ADMIN);
    }

    @Test
    void borrarMensajeSiYaNoExisteIgualResuelveElReporte() {
        Long id = reportar(40, REPORTADOR);
        when(comunicacionDao.obtenerPorId(40L)).thenReturn(null);

        service.borrarMensaje(ADMIN, id, null);

        // No se llama a eliminar(): su @Transactional dejaría la transacción en rollback-only.
        verify(comunicacionService, never()).eliminar(anyLong(), anyLong());
        assertThat(estado(id)).isEqualTo("MENSAJE_BORRADO");
    }

    @Test
    void otrosReportesDelAutorCuentaMensajesDistintosNoReportes() {
        Long uno = reportar(40, REPORTADOR);
        reportar(40, OTRO_REPORTADOR);   // mismo mensaje, otro reportador
        reportar(41, REPORTADOR);        // otro mensaje del autor
        service.registrarReporte(mensaje(42, OTRO_ADMIN), REPORTADOR, "x"); // de otro autor

        ReporteModeracionResponse r = service.listar(ADMIN, null).stream()
                .filter(x -> x.id().equals(uno)).findFirst().orElseThrow();

        assertThat(r.otrosReportesDelAutor()).isEqualTo(1);
    }

    @Test
    void cadaReporteIndicaSiElAutorEsAdmin() {
        reportar(40, REPORTADOR);
        when(usuarioAppService.tieneRol(AUTOR.intValue(), "ADMIN_APP")).thenReturn(true);

        assertThat(service.listar(ADMIN, null).get(0).autor().esAdmin()).isTrue();
    }

    @Test
    void desactivarAutorLoDesactivaDaDeBajaSusDispositivosYGuardaElEstado() {
        Long id = reportar(40, REPORTADOR);

        service.desactivarAutor(ADMIN, id, "Reincidente", false);

        verify(usuarioAppService).desactivarUsuarioAdmin(AUTOR.intValue());
        verify(dispositivoAppDao).eliminarTodosDeUsuario(AUTOR);
        verify(comunicacionService, never()).eliminar(anyLong(), anyLong());

        var fila = jdbc.queryForMap("SELECT * FROM REPORTES_COMUNICACION WHERE ID = ?", id);

        assertThat(fila.get("ESTADO")).isEqualTo("AUTOR_DESACTIVADO");
        assertThat(((Number) fila.get("RESUELTO_POR_ID")).longValue()).isEqualTo(ADMIN);
        assertThat(fila.get("NOTA_RESOLUCION")).isEqualTo("Reincidente");
    }

    @Test
    void desactivarAutorConBorrarMensajeBorraElMensajeYResuelveSusOtrosReportes() {
        Long uno = reportar(40, REPORTADOR);
        Long dos = reportar(40, OTRO_REPORTADOR);

        service.desactivarAutor(ADMIN, uno, null, true);

        verify(comunicacionService).eliminar(40L, ADMIN);
        assertThat(estado(uno)).isEqualTo("AUTOR_DESACTIVADO");
        assertThat(estado(dos)).isEqualTo("AUTOR_DESACTIVADO");
    }

    @Test
    void noSePuedeDesactivarAUnAdminNiAUnoMismo() {
        // El autor del mensaje es otro administrador.
        when(usuarioAppService.tieneRol(AUTOR.intValue(), "ADMIN_APP")).thenReturn(true);
        Long contraAdmin = reportar(40, REPORTADOR);

        assertThatThrownBy(() -> service.desactivarAutor(ADMIN, contraAdmin, null, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("administrador");

        // El administrador que modera es el autor.
        Long propio = service.registrarReporte(mensaje(50, ADMIN), REPORTADOR, "x");

        assertThatThrownBy(() -> service.desactivarAutor(ADMIN, propio, null, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("propia");

        verify(usuarioAppService, never()).desactivarUsuarioAdmin(org.mockito.ArgumentMatchers.anyInt());
        verify(dispositivoAppDao, never()).eliminarTodosDeUsuario(anyLong());
        assertThat(estado(contraAdmin)).isEqualTo("PENDIENTE");
        assertThat(estado(propio)).isEqualTo("PENDIENTE");
    }

    @Test
    void soloUnReportePendienteSePuedeResolver() {
        Long id = reportar(40, REPORTADOR);
        service.descartar(ADMIN, id, null);

        assertThatThrownBy(() -> service.descartar(ADMIN, id, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.borrarMensaje(ADMIN, id, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.descartar(ADMIN, 999L, null)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void soloUnAdminPuedeConsultarYResolver_CoordinadorYEntrenadorNo() {
        Long id = reportar(40, REPORTADOR);

        // COORDINADOR (tieneRol ADMIN_APP = false por defecto en el mock).
        assertThatThrownBy(() -> service.listar(COORDINADOR, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.contarPendientes(COORDINADOR)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.descartar(COORDINADOR, id, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.borrarMensaje(COORDINADOR, id, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.desactivarAutor(COORDINADOR, id, null, true))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.listar(null, null)).isInstanceOf(SecurityException.class);

        assertThat(estado(id)).isEqualTo("PENDIENTE");
    }

    @Test
    void unaNotaDemasiadoLargaSeRechaza() {
        Long id = reportar(40, REPORTADOR);

        assertThatThrownBy(() -> service.descartar(ADMIN, id, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
