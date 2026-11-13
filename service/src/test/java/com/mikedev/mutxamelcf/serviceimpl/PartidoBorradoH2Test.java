package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import com.mikedev.mutxamelcf.dao.DescansoEquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.impl.ConvocatoriaDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.ConvocatoriaJugadorDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.EquipacionDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.JustificacionFaltaPartidoDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.PartidoDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.PartidoLiveEventoDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.ResumenTemporadaDaoImpl;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo;
import com.mikedev.mutxamelcf.model.JustificacionFaltaPartido;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.PartidoBorradoResumen;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.InvitadosService;

/**
 * Borrar un partido con todos sus datos, con los DAO reales sobre H2 en modo Oracle y las claves ajenas sin
 * ON DELETE CASCADE que tiene Oracle (ORA-02292): retransmisión, alineación, estadísticas, justificaciones y
 * convocatoria con sus jugadores e invitados.
 */
class PartidoBorradoH2Test {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 21);

    private static final Long ENTRENADOR = 1L;
    private static final Long COORDINADOR = 2L;
    private static final Long EQUIPO = 10L;
    private static final Long OTRO_EQUIPO = 11L;

    /** Futuro, con convocatoria. */
    private static final long FUTURO = 30;
    /** Jugado (2-1), retransmitido, con alineación, estadísticas, convocatoria con un invitado y una justificación. */
    private static final long JUGADO = 31;
    /** Sin ningún dato. */
    private static final long VACIO = 32;
    /** Otro partido del mismo equipo con estadísticas: no se toca al borrar los demás. */
    private static final long OTRO = 33;

    private static final String[] TABLAS_POR_PARTIDO = { "PARTIDO_LIVE_EVENTOS", "PARTIDO_ALINEACION",
            "PARTIDO_ESTADISTICAS_JUGADOR", "JUSTIFICACIONES_FALTA_PARTIDO", "CONVOCATORIAS" };

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private EquipoGestionDao equipoGestionDao;
    private ComunicacionService comunicacionService;
    private InvitadosService invitadosService;
    private PartidoServiceImpl service;
    private JustificacionFaltaPartidoDaoImpl justificacionDao;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
                CREATE TABLE TEMPORADAS (
                    ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE, FECHA_FIN DATE, ACTIVA NUMBER(1))""");
        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50))");
        jdbc.execute("""
                CREATE TABLE JUGADORES (
                    ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), APELLIDOS VARCHAR2(50), DORSAL NUMBER,
                    EQUIPO VARCHAR2(50), EQUIPO_ID NUMBER, FECHA_BAJA DATE)""");
        jdbc.execute("""
                CREATE TABLE PARTIDOS (
                    ID NUMBER PRIMARY KEY, EQUIPO_ID NUMBER NOT NULL, RIVAL VARCHAR2(150), DIA DATE,
                    HORA VARCHAR2(10), CAMPO VARCHAR2(150), RESULTADO VARCHAR2(20), TIPO VARCHAR2(20),
                    USUARIO_ACTUALIZO_ID NUMBER, FECHA_ACTUALIZACION TIMESTAMP, CANCELADO NUMBER(1) DEFAULT 0,
                    GOLES_FAVOR NUMBER, GOLES_CONTRA NUMBER, AVISO_RESULTADO_ENVIADO NUMBER(1) DEFAULT 0,
                    ORIGEN VARCHAR2(10) DEFAULT 'MANUAL', JORNADA NUMBER, ES_LOCAL NUMBER(1) DEFAULT 1)""");
        jdbc.execute("""
                CREATE TABLE CONVOCATORIAS (
                    ID NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    EQUIPO_ID NUMBER NOT NULL,
                    PARTIDO_ID NUMBER NOT NULL,
                    HORA_CONVOCATORIA VARCHAR2(10),
                    LUGAR_CONVOCATORIA VARCHAR2(150),
                    USUARIO_ENTRENADOR_ID NUMBER,
                    FECHA_CREACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP)""");
        jdbc.execute("""
                CREATE TABLE PARTIDO_ESTADISTICAS_JUGADOR (
                    ID NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, PARTIDO_ID NUMBER NOT NULL,
                    JUGADOR_ID NUMBER NOT NULL, GOLES NUMBER DEFAULT 0, ASISTENCIAS NUMBER DEFAULT 0,
                    TARJETAS_AMARILLAS NUMBER DEFAULT 0, TARJETA_ROJA NUMBER(1) DEFAULT 0)""");

        EsquemaH2.crear(jdbc);
        EsquemaH2.crearPlantillas(jdbc);

        // Como en Oracle: ninguna de estas claves ajenas tiene ON DELETE CASCADE.
        for (String tabla : TABLAS_POR_PARTIDO) {
            jdbc.execute("ALTER TABLE " + tabla + " ADD CONSTRAINT FK_T_" + tabla
                    + " FOREIGN KEY (PARTIDO_ID) REFERENCES PARTIDOS(ID)");
        }
        jdbc.execute("ALTER TABLE CONVOCATORIA_JUGADOR ADD CONSTRAINT FK_T_CONV_JUG"
                + " FOREIGN KEY (CONVOCATORIA_ID) REFERENCES CONVOCATORIAS(ID)");

        jdbc.update("INSERT INTO TEMPORADAS VALUES (1, '2026/2027', ?, ?, 1)",
                Date.valueOf(LocalDate.of(2026, 8, 1)), Date.valueOf(LocalDate.of(2027, 6, 30)));
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (?, 'Cadete A')", EQUIPO);
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (?, 'Cadete B')", OTRO_EQUIPO);

        for (long jugador : new long[] { 100, 101, 102 }) {
            jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, DORSAL, EQUIPO, EQUIPO_ID)"
                    + " VALUES (?, 'J', 'J', ?, 'Cadete A', ?)", jugador, jugador - 90, EQUIPO);
        }
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, DORSAL, EQUIPO, EQUIPO_ID)"
                + " VALUES (200, 'I', 'I', 3, 'Cadete B', ?)", OTRO_EQUIPO);

        equipoGestionDao = mock(EquipoGestionDao.class);
        comunicacionService = mock(ComunicacionService.class);
        invitadosService = mock(InvitadosService.class);

        when(equipoGestionDao.puedeGestionarEquipo(ENTRENADOR, EQUIPO)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(COORDINADOR, EQUIPO)).thenReturn(true);
        when(equipoGestionDao.esCoordinadorOAdmin(COORDINADOR)).thenReturn(true);
        when(equipoGestionDao.obtenerNombreJugador(anyLong())).thenAnswer(i -> "Jugador " + i.getArgument(0));
        when(equipoGestionDao.obtenerUsuariosPorJugador(100L)).thenReturn(List.of(50L));
        when(equipoGestionDao.obtenerUsuariosPorJugador(101L)).thenReturn(List.of(51L));
        when(equipoGestionDao.obtenerUsuariosPorJugador(102L)).thenReturn(List.of(52L));
        when(equipoGestionDao.obtenerUsuariosPorJugador(200L)).thenReturn(List.of(60L));
        when(equipoGestionDao.obtenerUsuariosFamiliaresPorJugador(anyLong())).thenReturn(List.of());

        Clock reloj = Clock.fixed(HOY.atTime(12, 0).atZone(MADRID).toInstant(), MADRID);

        PartidoDaoImpl partidoDao = new PartidoDaoImpl(jdbc, reloj);
        ConvocatoriaJugadorDaoImpl convocatoriaJugadorDao = new ConvocatoriaJugadorDaoImpl(jdbc);
        ConvocatoriaDaoImpl convocatoriaDao = new ConvocatoriaDaoImpl(jdbc);
        justificacionDao = new JustificacionFaltaPartidoDaoImpl(jdbc);

        ConvocatoriaServiceImpl convocatoriaService = new ConvocatoriaServiceImpl(
                convocatoriaDao, convocatoriaJugadorDao, equipoGestionDao, partidoDao, comunicacionService,
                justificacionDao, mock(TemporadaDao.class), invitadosService, new EquipacionDaoImpl(jdbc), reloj,
                AvisosTest.TEXTOS, AvisosTest.idiomas());

        service = new PartidoServiceImpl(
                partidoDao, equipoGestionDao, mock(EquipoDao.class), convocatoriaDao, convocatoriaJugadorDao,
                mock(DescansoEquipoDao.class), justificacionDao, mock(TemporadaDao.class), invitadosService,
                new PartidoLiveEventoDaoImpl(jdbc), convocatoriaService, AvisosTest.TEXTOS, reloj);

        // FUTURO: convocatoria de 100 y 101 (la de 101 con la ausencia justificada) e invitado 200 autorizado.
        partido(FUTURO, HOY.plusDays(3), null, null);
        long convocatoriaFuturo = convocatoria(FUTURO);
        convocado(convocatoriaFuturo, 100, false);
        convocado(convocatoriaFuturo, 101, false);
        convocado(convocatoriaFuturo, 200, true);
        justificar(FUTURO, 101);

        // JUGADO: todo.
        partido(JUGADO, HOY.minusDays(3), "2-1", 2);
        long convocatoriaJugado = convocatoria(JUGADO);
        convocado(convocatoriaJugado, 100, false);
        convocado(convocatoriaJugado, 101, false);
        convocado(convocatoriaJugado, 200, true);
        justificar(JUGADO, 102);
        for (String tipo : new String[] { "INICIO", "GOL_FAVOR", "FINAL" }) {
            evento(JUGADO, tipo);
        }
        alineacion(JUGADO, 100, 1, 1);
        alineacion(JUGADO, 101, 0, 2);
        estadistica(JUGADO, 100, 2, 1, 1, 0);
        estadistica(JUGADO, 101, 0, 0, 1, 1);

        partido(VACIO, HOY.plusDays(10), null, null);

        partido(OTRO, HOY.minusDays(10), "1-0", 1);
        estadistica(OTRO, 100, 1, 0, 1, 0);
        evento(OTRO, "INICIO");
    }

    // ---- datos ------------------------------------------------------------------------------

    private void partido(long id, LocalDate dia, String resultado, Integer golesFavor) {
        jdbc.update("INSERT INTO PARTIDOS (ID, EQUIPO_ID, RIVAL, DIA, HORA, RESULTADO, GOLES_FAVOR, GOLES_CONTRA)"
                + " VALUES (?, ?, 'Pego', ?, '11:00', ?, ?, ?)",
                id, EQUIPO, Date.valueOf(dia), resultado, golesFavor, golesFavor == null ? null : 1);
    }

    private long convocatoria(long partidoId) {
        jdbc.update("INSERT INTO CONVOCATORIAS (EQUIPO_ID, PARTIDO_ID, HORA_CONVOCATORIA, LUGAR_CONVOCATORIA,"
                + " USUARIO_ENTRENADOR_ID) VALUES (?, ?, '10:00', 'Vestuarios', ?)", EQUIPO, partidoId, ENTRENADOR);
        return jdbc.queryForObject("SELECT ID FROM CONVOCATORIAS WHERE PARTIDO_ID = ?", Long.class, partidoId);
    }

    private void convocado(long convocatoriaId, long jugadorId, boolean invitado) {
        jdbc.update("INSERT INTO CONVOCATORIA_JUGADOR (CONVOCATORIA_ID, JUGADOR_ID, EQUIPO_ORIGEN_ID, ESTADO_INVITACION)"
                + " VALUES (?, ?, ?, ?)", convocatoriaId, jugadorId, invitado ? OTRO_EQUIPO : null,
                invitado ? "AUTORIZADO" : null);
    }

    private void justificar(long partidoId, long jugadorId) {
        JustificacionFaltaPartido j = new JustificacionFaltaPartido();
        j.setPartidoId(partidoId);
        j.setJugadorId(jugadorId);
        j.setUsuarioAppId(7L);
        j.setMotivo("Examen");
        j.setFechaCreacion(LocalDateTime.of(2026, 10, 20, 10, 0));
        justificacionDao.crear(j);
    }

    private void evento(long partidoId, String tipo) {
        jdbc.update("INSERT INTO PARTIDO_LIVE_EVENTOS (PARTIDO_ID, TIPO, USUARIO_APP_ID, FECHA) VALUES (?, ?, 7, ?)",
                partidoId, tipo, java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 10, 18, 11, 0)));
    }

    private void alineacion(long partidoId, long jugadorId, int titular, int orden) {
        jdbc.update("INSERT INTO PARTIDO_ALINEACION (PARTIDO_ID, JUGADOR_ID, TITULAR, ORDEN) VALUES (?, ?, ?, ?)",
                partidoId, jugadorId, titular, orden);
    }

    private void estadistica(long partidoId, long jugadorId, int goles, int asistencias, int amarillas, int roja) {
        jdbc.update("INSERT INTO PARTIDO_ESTADISTICAS_JUGADOR (PARTIDO_ID, JUGADOR_ID, GOLES, ASISTENCIAS,"
                + " TARJETAS_AMARILLAS, TARJETA_ROJA) VALUES (?, ?, ?, ?, ?, ?)",
                partidoId, jugadorId, goles, asistencias, amarillas, roja);
    }

    private int filas(String tabla, long partidoId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE PARTIDO_ID = ?", Integer.class, partidoId);
    }

    private int total(String tabla) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
    }

    private void sinFilasDelPartido(long partidoId) {
        for (String tabla : TABLAS_POR_PARTIDO) {
            assertThat(filas(tabla, partidoId)).as(tabla).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM PARTIDOS WHERE ID = ?", Integer.class, partidoId)).isZero();
        // Ninguna fila de convocados sin su convocatoria.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CONVOCATORIA_JUGADOR CJ WHERE NOT EXISTS"
                + " (SELECT 1 FROM CONVOCATORIAS C WHERE C.ID = CJ.CONVOCATORIA_ID)", Integer.class)).isZero();
    }

    private void jugadoNoTocado() {
        assertThat(filas("PARTIDO_ESTADISTICAS_JUGADOR", OTRO)).isEqualTo(1);
        assertThat(filas("PARTIDO_LIVE_EVENTOS", OTRO)).isEqualTo(1);
        assertThat(filas("PARTIDO_ESTADISTICAS_JUGADOR", FUTURO)).isZero();
        assertThat(filas("CONVOCATORIAS", FUTURO)).isEqualTo(1);
        assertThat(filas("JUSTIFICACIONES_FALTA_PARTIDO", FUTURO)).isEqualTo(1);
    }

    // ---- borrado en cascada ------------------------------------------------------------------

    @Test
    void borrarUnPartidoRetransmitidoLoBorraTodoSinFilasHuerfanasNiAvisos() {
        service.eliminar(COORDINADOR, JUGADO);

        sinFilasDelPartido(JUGADO);
        jugadoNoTocado();
        // 2 convocatorias (FUTURO y la de JUGADO se fue): solo queda la de FUTURO, con sus 3 filas.
        assertThat(total("CONVOCATORIAS")).isEqualTo(1);
        assertThat(total("CONVOCATORIA_JUGADOR")).isEqualTo(3);
        assertThat(total("PARTIDO_ALINEACION")).isZero();

        // Ya se jugó: ningún aviso a los convocados; el de invitados es el de siempre.
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
        verify(invitadosService).alCancelarPartido(any(Partido.class));
    }

    @Test
    void sinLasClavesAjenasEnCascadaOracleRechazariaElBorradoDeLaFilaPadre() {
        // Comprueba que el esquema del test reproduce el ORA-02292: borrar solo la fila de PARTIDOS falla.
        assertThatThrownBy(() -> jdbc.update("DELETE FROM PARTIDOS WHERE ID = ?", JUGADO))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void elPanelBorraUnPartidoConDatosYDejaElRastroDeQuienLoHizo() {
        service.eliminarComoAdmin(JUGADO, "admin@club");

        sinFilasDelPartido(JUGADO);
        jugadoNoTocado();
    }

    @Test
    void borrarUnPartidoFuturoAvisaDeLaConvocatoriaAnuladaSoloALosConvocadosQueNoHanJustificado() {
        service.eliminarComoAdmin(FUTURO, "admin@club");

        sinFilasDelPartido(FUTURO);

        ArgumentCaptor<Comunicacion> aviso = ArgumentCaptor.forClass(Comunicacion.class);
        // El 100 (convocado): sí. El 101 justificó su ausencia: no. El 200 es invitado: lo avisa InvitadosService.
        verify(comunicacionService).crearPrivada(aviso.capture(), eq(List.of(50L)), anyLong(), any());
        verify(comunicacionService, never()).crearPrivada(any(), eq(List.of(51L)), anyLong(), any());
        verify(comunicacionService, never()).crearPrivada(any(), eq(List.of(60L)), anyLong(), any());

        assertThat(aviso.getValue().getTitulo()).isEqualTo("Convocatoria anulada");
        assertThat(aviso.getValue().getContenido())
                .isEqualTo("Se ha anulado la convocatoria del partido contra el Pego del sábado 24/10.");
        verify(invitadosService).alCancelarPartido(any(Partido.class));
    }

    @Test
    void unPartidoPasadoSinResultadoConConvocatoriaNoAvisaAlBorrarse() {
        jdbc.update("UPDATE PARTIDOS SET DIA = ? WHERE ID = ?", Date.valueOf(HOY.minusDays(1)), FUTURO);

        service.eliminarComoAdmin(FUTURO, "admin@club");

        sinFilasDelPartido(FUTURO);
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
    }

    @Test
    void unPartidoDeHoyConResultadoNoAvisaAlBorrarse() {
        jdbc.update("UPDATE PARTIDOS SET DIA = ?, RESULTADO = '1-1', GOLES_FAVOR = 1, GOLES_CONTRA = 1 WHERE ID = ?",
                Date.valueOf(HOY), FUTURO);

        service.eliminarComoAdmin(FUTURO, "admin@club");

        sinFilasDelPartido(FUTURO);
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
    }

    @Test
    void unPartidoFuturoYaRetransmitidoSeDaPorJugadoYNoAvisa() {
        evento(FUTURO, "INICIO");

        service.eliminarComoAdmin(FUTURO, "admin@club");

        sinFilasDelPartido(FUTURO);
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
    }

    // ---- todo o nada --------------------------------------------------------------------------

    @Test
    void siFallaUnPasoNoSeBorraNada() {
        // Una tabla nueva con clave ajena a PARTIDOS que el borrado no conoce: falla en el último paso (PARTIDOS),
        // cuando ya se han borrado todas las demás filas.
        jdbc.execute("CREATE TABLE TABLA_NUEVA (ID NUMBER PRIMARY KEY, PARTIDO_ID NUMBER NOT NULL,"
                + " CONSTRAINT FK_TABLA_NUEVA FOREIGN KEY (PARTIDO_ID) REFERENCES PARTIDOS(ID))");
        jdbc.update("INSERT INTO TABLA_NUEVA VALUES (1, ?)", JUGADO);

        int convocatorias = total("CONVOCATORIAS");
        int convocados = total("CONVOCATORIA_JUGADOR");
        int eventos = total("PARTIDO_LIVE_EVENTOS");
        int alineacion = total("PARTIDO_ALINEACION");
        int estadisticas = total("PARTIDO_ESTADISTICAS_JUGADOR");
        int justificaciones = total("JUSTIFICACIONES_FALTA_PARTIDO");

        TransactionTemplate transaccion = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> service.eliminar(COORDINADOR, JUGADO)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(total("PARTIDOS")).isEqualTo(4);
        assertThat(total("CONVOCATORIAS")).isEqualTo(convocatorias);
        assertThat(total("CONVOCATORIA_JUGADOR")).isEqualTo(convocados);
        assertThat(total("PARTIDO_LIVE_EVENTOS")).isEqualTo(eventos);
        assertThat(total("PARTIDO_ALINEACION")).isEqualTo(alineacion);
        assertThat(total("PARTIDO_ESTADISTICAS_JUGADOR")).isEqualTo(estadisticas);
        assertThat(total("JUSTIFICACIONES_FALTA_PARTIDO")).isEqualTo(justificaciones);
    }

    // ---- permisos -----------------------------------------------------------------------------

    @Test
    void unEntrenadorBorraUnPartidoSinDatos() {
        service.eliminar(ENTRENADOR, VACIO);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM PARTIDOS WHERE ID = ?", Integer.class, VACIO)).isZero();
        verify(invitadosService).alCancelarPartido(any(Partido.class));
    }

    @Test
    void unEntrenadorNoBorraUnPartidoConConvocatoriaResultadoRetransmisionOEstadisticas() {
        // Convocatoria (FUTURO), resultado y todo lo demás (JUGADO), y cada dato por separado.
        jdbc.update("UPDATE PARTIDOS SET RESULTADO = '3-0' WHERE ID = ?", VACIO);
        comprobarQueUnEntrenadorNoPuedeBorrar(VACIO);
        jdbc.update("UPDATE PARTIDOS SET RESULTADO = NULL WHERE ID = ?", VACIO);

        evento(VACIO, "INICIO");
        comprobarQueUnEntrenadorNoPuedeBorrar(VACIO);
        jdbc.update("DELETE FROM PARTIDO_LIVE_EVENTOS WHERE PARTIDO_ID = ?", VACIO);

        estadistica(VACIO, 100, 0, 0, 0, 0);
        comprobarQueUnEntrenadorNoPuedeBorrar(VACIO);
        jdbc.update("DELETE FROM PARTIDO_ESTADISTICAS_JUGADOR WHERE PARTIDO_ID = ?", VACIO);

        alineacion(VACIO, 100, 1, 1);
        comprobarQueUnEntrenadorNoPuedeBorrar(VACIO);

        comprobarQueUnEntrenadorNoPuedeBorrar(FUTURO);
        comprobarQueUnEntrenadorNoPuedeBorrar(JUGADO);

        // Nada de avisos ni de cancelación de invitados: el rechazo es antes de hacer nada.
        verify(comunicacionService, never()).crearPrivada(any(), any(), anyLong(), any());
        verify(invitadosService, never()).alCancelarPartido(any(Partido.class));
        assertThat(total("PARTIDOS")).isEqualTo(4);
    }

    private void comprobarQueUnEntrenadorNoPuedeBorrar(long partidoId) {
        assertThatThrownBy(() -> service.eliminar(ENTRENADOR, partidoId))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Este partido tiene datos (convocatoria, resultado o retransmisión):"
                        + " pide a coordinación que lo borre");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM PARTIDOS WHERE ID = ?", Integer.class, partidoId))
                .isEqualTo(1);
    }

    @Test
    void unCoordinadorBorraUnPartidoConDatos() {
        service.eliminar(COORDINADOR, JUGADO);
        service.eliminar(COORDINADOR, FUTURO);

        sinFilasDelPartido(JUGADO);
        sinFilasDelPartido(FUTURO);
        assertThat(total("PARTIDOS")).isEqualTo(2);
    }

    @Test
    void quienNoGestionaElEquipoNoBorraNiConsultaNada() {
        assertThatThrownBy(() -> service.eliminar(99L, VACIO)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.resumenBorrado(99L, VACIO)).isInstanceOf(SecurityException.class);

        assertThat(total("PARTIDOS")).isEqualTo(4);
    }

    // ---- resumen -------------------------------------------------------------------------------

    @Test
    void elResumenDiceLoQueSeBorrara() {
        assertThat(service.resumenBorrado(COORDINADOR, JUGADO)).isEqualTo(new PartidoBorradoResumen(
                JUGADO, true, true, true, 3, true, 3, true, 2, 2));
    }

    @Test
    void elResumenDeUnPartidoSinDatosNoTieneNada() {
        assertThat(service.resumenBorrado(ENTRENADOR, VACIO)).isEqualTo(new PartidoBorradoResumen(
                VACIO, false, true, false, 0, false, 0, false, 0, 0));
    }

    @Test
    void elResumenAvisaAlEntrenadorDeQueNoPodraBorrarUnPartidoConDatos() {
        PartidoBorradoResumen resumen = service.resumenBorrado(ENTRENADOR, FUTURO);

        assertThat(resumen.tieneDatos()).isTrue();
        assertThat(resumen.puedeBorrar()).isFalse();
        assertThat(resumen.tieneConvocatoria()).isTrue();
        assertThat(resumen.convocados()).isEqualTo(3);
        assertThat(resumen.tieneResultado()).isFalse();
        assertThat(service.resumenBorradoComoAdmin(FUTURO).puedeBorrar()).isTrue();
    }

    @Test
    void elResumenDeUnPartidoInexistenteEsUnError() {
        assertThatThrownBy(() -> service.resumenBorradoComoAdmin(999L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resumenBorrado(COORDINADOR, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- las estadísticas se suman de los partidos que existen -----------------------------------

    @Test
    void losTotalesDeJugadoresYEquipoSeRecalculanSinElPartidoBorrado() {
        ResumenTemporadaDaoImpl resumen = new ResumenTemporadaDaoImpl(jdbc);
        LocalDate desde = LocalDate.of(2026, 8, 1);
        LocalDate hasta = LocalDate.of(2027, 6, 30);

        // Antes: Pablo (100) suma JUGADO (2 goles, 1 asist., 1 amarilla) y OTRO (1 gol, 1 amarilla).
        assertThat(resumen.estadisticasPartidos(100L, desde, hasta, HOY).goles()).isEqualTo(3);
        assertThat(amarillasDelEquipo(resumen, 100)).isEqualTo(2);
        assertThat(amarillasDelEquipo(resumen, 101)).isEqualTo(1);

        service.eliminar(COORDINADOR, JUGADO);

        assertThat(resumen.estadisticasPartidos(100L, desde, hasta, HOY).goles()).isEqualTo(1);
        assertThat(resumen.estadisticasPartidos(100L, desde, hasta, HOY).asistencias()).isZero();
        assertThat(amarillasDelEquipo(resumen, 100)).isEqualTo(1);
        // Quien solo tenía estadísticas en el partido borrado queda a cero (y la roja también).
        assertThat(amarillasDelEquipo(resumen, 101)).isZero();
        assertThat(resumen.estadisticasPartidos(101L, desde, hasta, HOY).rojas()).isZero();
        assertThat(resumen.estadisticasJugadoresDelEquipo(EQUIPO, desde, hasta, HOY).stream()
                .mapToInt(EstadisticasJugadorEquipo::goles).sum()).isEqualTo(1);
    }

    private int amarillasDelEquipo(ResumenTemporadaDaoImpl resumen, long jugadorId) {
        return resumen.estadisticasJugadoresDelEquipo(
                EQUIPO, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 6, 30), HOY).stream()
                .filter(j -> j.jugadorId() == jugadorId)
                .mapToInt(EstadisticasJugadorEquipo::amarillas)
                .sum();
    }
}
