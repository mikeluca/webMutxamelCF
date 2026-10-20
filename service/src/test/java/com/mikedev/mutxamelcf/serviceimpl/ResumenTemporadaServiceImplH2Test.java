package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.dao.impl.ResumenTemporadaDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.TemporadaDaoImpl;
import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse;

/**
 * Resumen de temporada de la ficha del jugador con los DAO reales sobre H2 en
 * modo Oracle (agregados SQL) y el servicio real.
 */
class ResumenTemporadaServiceImplH2Test {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Hoy: miércoles 21/10/2026. Temporada 2026-09-01 a 2027-06-30. */
    private static final LocalDate HOY = LocalDate.of(2026, 10, 21);
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2027, 6, 30);

    private static final Long USUARIO = 7L;
    private static final Long JUGADOR = 100L;
    private static final Long EQUIPO = 5L;

    private JdbcTemplate jdbc;
    private UsuarioAppVinculoDao vinculoDao;
    private EquipoGestionDao equipoGestionDao;
    private ResumenTemporadaServiceImpl service;

    private long siguienteId = 1;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

        jdbc.execute("""
                CREATE TABLE TEMPORADAS (
                    ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE, FECHA_FIN DATE, ACTIVA NUMBER(1))""");
        jdbc.execute("CREATE TABLE SESIONES_ENTRENAMIENTO (ID NUMBER PRIMARY KEY, HORA VARCHAR2(20))");
        jdbc.execute("""
                CREATE TABLE ENTRENAMIENTO_ASISTENCIA (
                    ENTRENAMIENTO_ID NUMBER, JUGADOR_ID NUMBER, ESTADO VARCHAR2(30))""");
        jdbc.execute("""
                CREATE TABLE PARTIDOS (
                    ID NUMBER PRIMARY KEY, DIA DATE, CANCELADO NUMBER(1) DEFAULT 0,
                    RESULTADO VARCHAR2(20), GOLES_FAVOR NUMBER)""");
        jdbc.execute("CREATE TABLE CONVOCATORIAS (ID NUMBER PRIMARY KEY, PARTIDO_ID NUMBER)");
        EsquemaH2.crear(jdbc);
        jdbc.execute("""
                CREATE TABLE PARTIDO_ESTADISTICAS_JUGADOR (
                    PARTIDO_ID NUMBER, JUGADOR_ID NUMBER, GOLES NUMBER, ASISTENCIAS NUMBER,
                    TARJETAS_AMARILLAS NUMBER, TARJETA_ROJA NUMBER(1))""");

        jdbc.update("INSERT INTO TEMPORADAS VALUES (1, '2026/2027', ?, ?, 1)", Date.valueOf(INICIO), Date.valueOf(FIN));

        vinculoDao = mock(UsuarioAppVinculoDao.class);
        equipoGestionDao = mock(EquipoGestionDao.class);
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(true);
        when(equipoGestionDao.obtenerEquipoIdDeJugador(JUGADOR)).thenReturn(EQUIPO);

        service = new ResumenTemporadaServiceImpl(
                new ResumenTemporadaDaoImpl(jdbc),
                new TemporadaDaoImpl(jdbc),
                vinculoDao,
                equipoGestionDao,
                Clock.fixed(HOY.atTime(10, 0).atZone(MADRID).toInstant(), MADRID));
    }

    private void entrenamiento(LocalDate fecha, String estado) {
        long id = siguienteId++;
        jdbc.update("INSERT INTO ENTRENAMIENTOS (ID, EQUIPO_ID, FECHA, USUARIO_ENTRENADOR_ID) VALUES (?, 5, ?, 1)", id, Date.valueOf(fecha));
        jdbc.update("INSERT INTO ENTRENAMIENTO_ASISTENCIA VALUES (?, ?, ?)", id, JUGADOR, estado);
    }

    private long partido(LocalDate dia, boolean cancelado, String resultado) {
        long id = siguienteId++;
        jdbc.update("INSERT INTO PARTIDOS (ID, DIA, CANCELADO, RESULTADO) VALUES (?, ?, ?, ?)",
                id, Date.valueOf(dia), cancelado ? 1 : 0, resultado);
        return id;
    }

    private void convocar(long partidoId) {
        long convocatoria = siguienteId++;
        jdbc.update("INSERT INTO CONVOCATORIAS VALUES (?, ?)", convocatoria, partidoId);
        jdbc.update("INSERT INTO CONVOCATORIA_JUGADOR (CONVOCATORIA_ID, JUGADOR_ID) VALUES (?, ?)",
                convocatoria, JUGADOR);
    }

    private void justificar(long partidoId, long jugadorId) {
        jdbc.update("INSERT INTO JUSTIFICACIONES_FALTA_PARTIDO"
                + " (PARTIDO_ID, JUGADOR_ID, USUARIO_APP_ID, MOTIVO, FECHA_CREACION) VALUES (?, ?, 7, ?, ?)",
                partidoId, jugadorId, "Motivo", java.sql.Timestamp.valueOf(HOY.atStartOfDay()));
    }

    private void estadistica(long partidoId, int goles, int asistencias, int amarillas, int roja) {
        jdbc.update("INSERT INTO PARTIDO_ESTADISTICAS_JUGADOR VALUES (?, ?, ?, ?, ?, ?)",
                partidoId, JUGADOR, goles, asistencias, amarillas, roja);
    }

    // ---- entrenamientos ---------------------------------------------------------------

    @Test
    void elPorcentajeCuentaPresenteRetrasoYMalComportamientoComoAsistidos() {
        LocalDate d = INICIO.plusDays(3);
        entrenamiento(d, "PRESENTE");
        entrenamiento(d.plusDays(1), "PRESENTE");
        entrenamiento(d.plusDays(2), "PRESENTE");
        entrenamiento(d.plusDays(3), "RETRASO");
        entrenamiento(d.plusDays(4), "MAL_COMPORTAMIENTO");
        entrenamiento(d.plusDays(5), "FALTA");
        entrenamiento(d.plusDays(6), "FALTA");
        entrenamiento(d.plusDays(7), "FALTA_JUSTIFICADA");

        ResumenTemporadaResponse.EntrenamientosResumen e = service.obtenerResumen(USUARIO, JUGADOR).entrenamientos();

        assertThat(e.total()).isEqualTo(8);
        assertThat(e.asistidos()).isEqualTo(5);
        assertThat(e.faltas()).isEqualTo(2);
        assertThat(e.justificadas()).isEqualTo(1);
        // 5 / 8 = 62,5 % -> 63
        assertThat(e.porcentaje()).isEqualTo(63);
    }

    @Test
    void el90PorCientoDelEjemplo() {
        for (int i = 0; i < 18; i++) {
            entrenamiento(INICIO.plusDays(i), "PRESENTE");
        }
        entrenamiento(INICIO.plusDays(18), "FALTA");
        entrenamiento(INICIO.plusDays(19), "FALTA_JUSTIFICADA");

        ResumenTemporadaResponse.EntrenamientosResumen e = service.obtenerResumen(USUARIO, JUGADOR).entrenamientos();

        assertThat(e.total()).isEqualTo(20);
        assertThat(e.asistidos()).isEqualTo(18);
        assertThat(e.porcentaje()).isEqualTo(90);
    }

    @Test
    void sinEntrenamientosElPorcentajeEsNulo() {
        ResumenTemporadaResponse.EntrenamientosResumen e = service.obtenerResumen(USUARIO, JUGADOR).entrenamientos();

        assertThat(e.total()).isZero();
        assertThat(e.porcentaje()).isNull();
    }

    @Test
    void soloCuentanLosEntrenamientosDentroDeLaTemporadaYHastaAyer() {
        entrenamiento(INICIO.minusDays(1), "FALTA");        // antes de la temporada
        entrenamiento(INICIO, "PRESENTE");                  // primer día: cuenta
        entrenamiento(HOY.minusDays(1), "PRESENTE");        // ayer: cuenta
        entrenamiento(HOY, "PRESENTE");                     // hoy sin hora conocida: todavía no
        entrenamiento(HOY.plusDays(1), "PRESENTE");         // futuro (PRESENTE por defecto): no cuenta
        entrenamiento(FIN.plusDays(1), "PRESENTE");         // después de la temporada

        ResumenTemporadaResponse.EntrenamientosResumen e = service.obtenerResumen(USUARIO, JUGADOR).entrenamientos();

        assertThat(e.total()).isEqualTo(2);
        assertThat(e.faltas()).isZero();
        assertThat(e.porcentaje()).isEqualTo(100);
    }

    /** Entrenamiento con sesión (la hora puede ser null o cualquier texto). */
    private void entrenamientoConHora(LocalDate fecha, String estado, String hora) {
        long sesion = siguienteId++;
        long id = siguienteId++;
        jdbc.update("INSERT INTO SESIONES_ENTRENAMIENTO (ID, HORA) VALUES (?, ?)", sesion, hora);
        jdbc.update("INSERT INTO ENTRENAMIENTOS (ID, EQUIPO_ID, FECHA, SESION_ENTRENAMIENTO_ID, USUARIO_ENTRENADOR_ID) VALUES (?, 5, ?, ?, 1)",
                id, Date.valueOf(fecha), sesion);
        jdbc.update("INSERT INTO ENTRENAMIENTO_ASISTENCIA VALUES (?, ?, ?)", id, JUGADOR, estado);
    }

    @Test
    void elEntrenamientoDeHoyCuentaSoloSiSuHoraYaHaPasado() {
        // Son las 10:00 en Madrid.
        entrenamientoConHora(HOY, "PRESENTE", "09:00");     // ya ha pasado: cuenta
        entrenamientoConHora(HOY, "FALTA", "10:00");        // es justo ahora: cuenta
        entrenamientoConHora(HOY, "PRESENTE", "18:30");     // todavía no
        entrenamientoConHora(HOY, "PRESENTE", "9:30");      // formato H:mm: cuenta

        ResumenTemporadaResponse.EntrenamientosResumen e = service.obtenerResumen(USUARIO, JUGADOR).entrenamientos();

        assertThat(e.total()).isEqualTo(3);
        assertThat(e.asistidos()).isEqualTo(2);
        assertThat(e.faltas()).isEqualTo(1);
    }

    @Test
    void siNoSeSabeLaHoraDeHoySeCuentaHastaAyer() {
        entrenamientoConHora(HOY, "PRESENTE", null);
        entrenamientoConHora(HOY, "PRESENTE", "por la tarde");
        entrenamientoConHora(HOY.minusDays(1), "FALTA", null);   // ayer cuenta sea cual sea la hora

        ResumenTemporadaResponse.EntrenamientosResumen e = service.obtenerResumen(USUARIO, JUGADOR).entrenamientos();

        assertThat(e.total()).isEqualTo(1);
        assertThat(e.faltas()).isEqualTo(1);
    }

    @Test
    void elEntrenamientoDeHoyNoCuentaSiLaTemporadaYaHaTerminado() {
        jdbc.update("UPDATE TEMPORADAS SET FECHA_FIN = ?", Date.valueOf(HOY.minusDays(5)));
        entrenamientoConHora(HOY, "PRESENTE", "09:00");
        entrenamiento(HOY.minusDays(4), "PRESENTE");        // después del fin: no cuenta

        assertThat(service.obtenerResumen(USUARIO, JUGADOR).entrenamientos().total()).isZero();
    }
    @Test
    void laAsistenciaDeOtroJugadorNoCuenta() {
        long id = siguienteId++;
        jdbc.update("INSERT INTO ENTRENAMIENTOS (ID, EQUIPO_ID, FECHA, USUARIO_ENTRENADOR_ID) VALUES (?, 5, ?, 1)", id, Date.valueOf(INICIO.plusDays(2)));
        jdbc.update("INSERT INTO ENTRENAMIENTO_ASISTENCIA VALUES (?, 999, 'PRESENTE')", id);

        assertThat(service.obtenerResumen(USUARIO, JUGADOR).entrenamientos().total()).isZero();
    }

    // ---- partidos ----------------------------------------------------------------------

    @Test
    void sumaGolesAsistenciasYTarjetasDeLosPartidosDeLaTemporada() {
        long uno = partido(INICIO.plusDays(10), false, "2-1");
        long dos = partido(INICIO.plusDays(17), false, "0-0");
        long fuera = partido(INICIO.minusDays(20), false, "5-0");   // temporada anterior
        convocar(uno);
        convocar(dos);
        convocar(fuera);
        estadistica(uno, 2, 1, 1, 0);
        estadistica(dos, 1, 0, 1, 1);
        estadistica(fuera, 9, 9, 9, 1);

        ResumenTemporadaResponse.PartidosResumen p = service.obtenerResumen(USUARIO, JUGADOR).partidos();

        assertThat(p.convocado()).isEqualTo(2);
        assertThat(p.goles()).isEqualTo(3);
        assertThat(p.asistencias()).isEqualTo(1);
        assertThat(p.amarillas()).isEqualTo(2);
        assertThat(p.rojas()).isEqualTo(1);
    }

    @Test
    void losPartidosCanceladosSeExcluyen() {
        long jugado = partido(INICIO.plusDays(10), false, "1-0");
        long cancelado = partido(INICIO.plusDays(17), true, null);
        convocar(jugado);
        convocar(cancelado);
        estadistica(jugado, 1, 0, 0, 0);
        estadistica(cancelado, 4, 4, 4, 1);

        ResumenTemporadaResponse resumen = service.obtenerResumen(USUARIO, JUGADOR);

        assertThat(resumen.partidos().convocado()).isEqualTo(1);
        assertThat(resumen.partidos().goles()).isEqualTo(1);
        assertThat(resumen.partidos().rojas()).isZero();
    }

    @Test
    void soloSeCuentanConvocadoEnPartidosYaJugados() {
        long pasado = partido(HOY.minusDays(3), false, "1-0");
        long hoyConResultado = partido(HOY, false, "2-2");
        long hoySinResultado = partido(HOY, false, null);
        long futuro = partido(HOY.plusDays(3), false, null);
        convocar(pasado);
        convocar(hoyConResultado);
        convocar(hoySinResultado);
        convocar(futuro);

        assertThat(service.obtenerResumen(USUARIO, JUGADOR).partidos().convocado()).isEqualTo(2);
    }

    // ---- ausencias justificadas ----------------------------------------------------------

    @Test
    void cuentaLosPartidosYaJugadosDeLaTemporadaConAusenciaJustificadaDeEsteJugador() {
        justificar(partido(INICIO.plusDays(5), false, "1-0"), JUGADOR);
        justificar(partido(HOY.minusDays(2), false, "2-2"), JUGADOR);
        justificar(partido(HOY, false, null), JUGADOR);                       // hoy: cuenta, como "convocado"
        justificar(partido(HOY.plusDays(3), false, null), JUGADOR);          // futuro: todavía no
        justificar(partido(HOY.minusDays(1), true, null), JUGADOR);          // cancelado: no
        justificar(partido(FIN.plusDays(10), false, null), JUGADOR);         // fuera de temporada
        justificar(partido(INICIO.plusDays(6), false, "2-0"), 999L);         // otro jugador

        assertThat(service.obtenerResumen(USUARIO, JUGADOR).partidos().ausenciasJustificadas()).isEqualTo(3);
    }

    @Test
    void sinAusenciasElContadorEsCero() {
        assertThat(service.obtenerResumen(USUARIO, JUGADOR).partidos().ausenciasJustificadas()).isZero();
    }

    // ---- temporada y seguridad -----------------------------------------------------------

    @Test
    void devuelveLosDatosDeLaTemporada() {
        ResumenTemporadaResponse.TemporadaResumen t = service.obtenerResumen(USUARIO, JUGADOR).temporada();

        assertThat(t.nombre()).isEqualTo("2026/2027");
        assertThat(t.inicio()).isEqualTo(INICIO);
        assertThat(t.fin()).isEqualTo(FIN);
    }

    @Test
    void sinTemporadaActivaElResumenEstaVacioYLaTemporadaEsNula() {
        jdbc.update("UPDATE TEMPORADAS SET ACTIVA = 0");
        entrenamiento(INICIO.plusDays(1), "PRESENTE");

        ResumenTemporadaResponse resumen = service.obtenerResumen(USUARIO, JUGADOR);

        assertThat(resumen.temporada()).isNull();
        assertThat(resumen.entrenamientos().total()).isZero();
        assertThat(resumen.entrenamientos().porcentaje()).isNull();
        assertThat(resumen.partidos().goles()).isZero();
        assertThat(resumen.partidos().ausenciasJustificadas()).isZero();
    }

    @Test
    void sinVinculoNiGestionDelEquipoDevuelveSecurityException() {
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(false);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO, EQUIPO)).thenReturn(false);

        assertThatThrownBy(() -> service.obtenerResumen(USUARIO, JUGADOR))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void elStaffQueGestionaElEquipoPuedeVerlo() {
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(false);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO, EQUIPO)).thenReturn(true);

        assertThat(service.obtenerResumen(USUARIO, JUGADOR).temporada()).isNotNull();
    }

    @Test
    void unJugadorSinEquipoNoSePuedeConsultarSinVinculo() {
        when(vinculoDao.tieneVinculoConJugador(USUARIO.intValue(), JUGADOR)).thenReturn(false);
        when(equipoGestionDao.obtenerEquipoIdDeJugador(JUGADOR)).thenReturn(null);

        assertThatThrownBy(() -> service.obtenerResumen(USUARIO, JUGADOR))
                .isInstanceOf(SecurityException.class);
    }

}
