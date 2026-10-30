package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.impl.ResumenTemporadaDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.TemporadaDaoImpl;
import com.mikedev.mutxamelcf.model.EstadisticasEquipoResponse;
import com.mikedev.mutxamelcf.model.TarjetasEquipoResponse;
import com.mikedev.mutxamelcf.model.TarjetasJugadorResponse;

/**
 * Tarjetas y estadísticas de un equipo con los DAO reales sobre H2 (modo Oracle):
 * varias temporadas (solo cuenta la activa), partidos cancelados, otros equipos y
 * entrenamientos de hoy, de ayer y futuros.
 */
class EstadisticasEquipoServiceImplH2Test {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Hoy: miércoles 21/10/2026 a las 10:00. Temporada activa 2026-09-01 a 2027-06-30. */
    private static final LocalDate HOY = LocalDate.of(2026, 10, 21);
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2027, 6, 30);

    private static final Long USUARIO = 7L;
    private static final Long EQUIPO = 5L;
    private static final Long OTRO_EQUIPO = 6L;

    private static final long PABLO = 100;
    private static final long LUIS = 101;
    private static final long ANA = 102;
    private static final long MARTA = 103;

    private JdbcTemplate jdbc;
    private EquipoGestionDao equipoGestionDao;
    private EstadisticasEquipoServiceImpl service;

    private long siguienteId = 1000;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

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
                    ID NUMBER PRIMARY KEY, EQUIPO_ID NUMBER, DIA DATE, CANCELADO NUMBER(1) DEFAULT 0,
                    RESULTADO VARCHAR2(20), GOLES_FAVOR NUMBER, GOLES_CONTRA NUMBER)""");
        jdbc.execute("CREATE TABLE CONVOCATORIAS (ID NUMBER PRIMARY KEY, PARTIDO_ID NUMBER)");
        jdbc.execute("""
                CREATE TABLE PARTIDO_ESTADISTICAS_JUGADOR (
                    PARTIDO_ID NUMBER, JUGADOR_ID NUMBER, GOLES NUMBER DEFAULT 0, ASISTENCIAS NUMBER DEFAULT 0,
                    TARJETAS_AMARILLAS NUMBER DEFAULT 0, TARJETA_ROJA NUMBER(1) DEFAULT 0)""");
        jdbc.execute("CREATE TABLE SESIONES_ENTRENAMIENTO (ID NUMBER PRIMARY KEY, HORA VARCHAR2(20))");
        jdbc.execute("""
                CREATE TABLE ENTRENAMIENTO_ASISTENCIA (
                    ENTRENAMIENTO_ID NUMBER, JUGADOR_ID NUMBER, ESTADO VARCHAR2(30))""");

        EsquemaH2.crear(jdbc);
        EsquemaH2.crearPlantillas(jdbc);

        jdbc.update("INSERT INTO TEMPORADAS VALUES (1, '2025/2026', ?, ?, 0)",
                Date.valueOf(LocalDate.of(2025, 9, 1)), Date.valueOf(LocalDate.of(2026, 6, 30)));
        jdbc.update("INSERT INTO TEMPORADAS VALUES (2, '2026/2027', ?, ?, 1)", Date.valueOf(INICIO), Date.valueOf(FIN));

        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (?, 'Cadete A')", EQUIPO);
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (?, 'Cadete B')", OTRO_EQUIPO);

        jugador(PABLO, "Pablo", "Garcia", 9, "Cadete A");
        jugador(LUIS, "Luis", "Lopez", 1, "Cadete A");
        jugador(ANA, "Ana", "Ruiz", null, "Cadete A");
        jugador(MARTA, "Marta", "Otro", 7, "Cadete B");

        equipoGestionDao = mock(EquipoGestionDao.class);
        when(equipoGestionDao.existeEquipo(EQUIPO)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO, EQUIPO)).thenReturn(true);
        when(equipoGestionDao.equipoAdmiteEstadisticas(EQUIPO)).thenReturn(true);
        when(equipoGestionDao.existeEquipo(OTRO_EQUIPO)).thenReturn(true);
        when(equipoGestionDao.puedeGestionarEquipo(USUARIO, OTRO_EQUIPO)).thenReturn(true);
        when(equipoGestionDao.equipoAdmiteEstadisticas(OTRO_EQUIPO)).thenReturn(true);

        service = new EstadisticasEquipoServiceImpl(
                new ResumenTemporadaDaoImpl(jdbc),
                new TemporadaDaoImpl(jdbc),
                equipoGestionDao,
                new ReglaSancionAmarillas(5, 1),
                Clock.fixed(HOY.atTime(10, 0).atZone(MADRID).toInstant(), MADRID));
    }

    // ---- datos ----------------------------------------------------------------------------

    private void jugador(long id, String nombre, String apellidos, Integer dorsal, String equipo) {
        // El enlace con el equipo es EQUIPO_ID; la columna EQUIPO es solo una copia
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, DORSAL, EQUIPO, EQUIPO_ID)"
                + " VALUES (?, ?, ?, ?, ?, (SELECT ID FROM EQUIPO WHERE NOMBRE = ?))",
                id, nombre, apellidos, dorsal, equipo, equipo);
    }

    private long partido(long equipoId, LocalDate dia, boolean cancelado, Integer golesFavor, Integer golesContra) {
        long id = siguienteId++;
        jdbc.update("INSERT INTO PARTIDOS (ID, EQUIPO_ID, DIA, CANCELADO, GOLES_FAVOR, GOLES_CONTRA, RESULTADO)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, equipoId, Date.valueOf(dia), cancelado ? 1 : 0, golesFavor, golesContra,
                golesFavor == null ? null : golesFavor + "-" + golesContra);
        return id;
    }

    private void estadistica(long partido, long jugador, int goles, int asistencias, int amarillas, int roja) {
        jdbc.update("INSERT INTO PARTIDO_ESTADISTICAS_JUGADOR VALUES (?, ?, ?, ?, ?, ?)",
                partido, jugador, goles, asistencias, amarillas, roja);
    }

    private void convocar(long partido, long jugador) {
        long convocatoria = siguienteId++;
        jdbc.update("INSERT INTO CONVOCATORIAS VALUES (?, ?)", convocatoria, partido);
        jdbc.update("INSERT INTO CONVOCATORIA_JUGADOR (CONVOCATORIA_ID, JUGADOR_ID) VALUES (?, ?)",
                convocatoria, jugador);
    }

    /** Entrenamiento del equipo con la asistencia de los jugadores dados (jugador -> estado), con sesión y hora. */
    private long entrenamiento(LocalDate fecha, String hora, boolean revisada, Object... jugadorYEstado) {
        long id = siguienteId++;
        Long sesion = null;

        if (hora != null) {
            sesion = siguienteId++;
            jdbc.update("INSERT INTO SESIONES_ENTRENAMIENTO VALUES (?, ?)", sesion, hora);
        }

        jdbc.update("INSERT INTO ENTRENAMIENTOS (ID, EQUIPO_ID, FECHA, USUARIO_ENTRENADOR_ID, SESION_ENTRENAMIENTO_ID,"
                + " LISTA_REVISADA) VALUES (?, ?, ?, 1, ?, ?)",
                id, EQUIPO, Date.valueOf(fecha), sesion, revisada ? 1 : 0);

        for (int i = 0; i < jugadorYEstado.length; i += 2) {
            jdbc.update("INSERT INTO ENTRENAMIENTO_ASISTENCIA VALUES (?, ?, ?)",
                    id, jugadorYEstado[i], jugadorYEstado[i + 1]);
        }

        return id;
    }

    private List<TarjetasJugadorResponse> tarjetas() {
        return service.obtenerTarjetas(USUARIO, EQUIPO).jugadores();
    }

    private TarjetasJugadorResponse tarjetasDe(long jugador) {
        return tarjetas().stream().filter(t -> t.jugadorId() == jugador).findFirst().orElseThrow();
    }

    // ---- tarjetas ------------------------------------------------------------------------------

    @Test
    void elEstadoDeLasTarjetasDependeDeLasAmarillasAcumuladas() {
        long partido = partido(EQUIPO, INICIO.plusDays(10), false, 1, 0);

        // 0, 4, 5 y 9 amarillas en el mismo equipo (jugadores distintos).
        jugador(104, "Cero", "A", 2, "Cadete A");
        jugador(105, "Cuatro", "B", 3, "Cadete A");
        jugador(106, "Cinco", "C", 4, "Cadete A");
        jugador(107, "Nueve", "D", 5, "Cadete A");
        jugador(108, "Diez", "E", 6, "Cadete A");

        estadistica(partido, 105, 0, 0, 4, 0);
        estadistica(partido, 106, 0, 0, 5, 0);
        estadistica(partido, 107, 0, 0, 9, 0);
        estadistica(partido, 108, 0, 0, 10, 0);

        assertThat(tarjetasDe(104).estado()).isEqualTo("OK");
        assertThat(tarjetasDe(104).amarillas()).isZero();
        assertThat(tarjetasDe(105).estado()).isEqualTo("A_UNA");
        assertThat(tarjetasDe(106).estado()).isEqualTo("SANCION_POSIBLE");
        assertThat(tarjetasDe(107).estado()).isEqualTo("A_UNA");
        assertThat(tarjetasDe(108).estado()).isEqualTo("SANCION_POSIBLE");
    }

    @Test
    void lasTarjetasSumanVariosPartidosYSeOrdenanPorAmarillasDescendente() {
        long uno = partido(EQUIPO, INICIO.plusDays(5), false, 1, 0);
        long dos = partido(EQUIPO, INICIO.plusDays(12), false, 0, 2);
        long tres = partido(EQUIPO, INICIO.plusDays(19), false, 2, 2);

        estadistica(uno, PABLO, 0, 0, 1, 0);
        estadistica(dos, PABLO, 0, 0, 2, 1);
        estadistica(tres, PABLO, 0, 0, 1, 0);
        estadistica(uno, LUIS, 0, 0, 2, 0);

        List<TarjetasJugadorResponse> lista = tarjetas();

        assertThat(lista).extracting(TarjetasJugadorResponse::nombre)
                .containsExactly("Pablo Garcia", "Luis Lopez", "Ana Ruiz");
        assertThat(lista.get(0).amarillas()).isEqualTo(4);
        assertThat(lista.get(0).rojas()).isEqualTo(1);
        assertThat(lista.get(0).dorsal()).isEqualTo(9);
        assertThat(lista.get(0).estado()).isEqualTo("A_UNA");
        assertThat(lista.get(2).dorsal()).isNull();
    }

    @Test
    void soloCuentaLaTemporadaActivaLosPartidosNoCanceladosYLosJugadoresDelEquipo() {
        long temporadaAnterior = partido(EQUIPO, LocalDate.of(2026, 3, 1), false, 1, 0);
        long cancelado = partido(EQUIPO, INICIO.plusDays(8), true, null, null);
        long valido = partido(EQUIPO, INICIO.plusDays(9), false, 1, 1);

        estadistica(temporadaAnterior, PABLO, 5, 5, 4, 1);
        estadistica(cancelado, PABLO, 5, 5, 4, 1);
        estadistica(valido, PABLO, 1, 0, 1, 0);
        estadistica(valido, MARTA, 3, 3, 3, 3);

        List<TarjetasJugadorResponse> lista = tarjetas();

        assertThat(tarjetasDe(PABLO).amarillas()).isEqualTo(1);
        assertThat(tarjetasDe(PABLO).rojas()).isZero();
        // Marta es de otro equipo.
        assertThat(lista).extracting(TarjetasJugadorResponse::jugadorId).doesNotContain(MARTA);
    }

    @Test
    void sinTemporadaActivaTodosEstanAOk() {
        jdbc.update("UPDATE TEMPORADAS SET ACTIVA = 0");
        estadistica(partido(EQUIPO, INICIO.plusDays(9), false, 1, 0), PABLO, 1, 1, 4, 1);

        assertThat(tarjetas()).hasSize(3).allSatisfy(t -> {
            assertThat(t.amarillas()).isZero();
            assertThat(t.estado()).isEqualTo("OK");
        });
    }

    @Test
    void lasTarjetasSoloLasVeElStaffDelEquipo() {
        when(equipoGestionDao.puedeGestionarEquipo(8L, EQUIPO)).thenReturn(false);

        assertThatThrownBy(() -> service.obtenerTarjetas(8L, EQUIPO)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.obtenerEstadisticas(8L, EQUIPO)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.obtenerTarjetas(null, EQUIPO)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.obtenerTarjetas(USUARIO, 99L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.obtenerTarjetas(USUARIO, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unEquipoSinEstadisticasDeJugadorDevuelveTarjetasVaciasYAdmiteEstadisticasFalse() {
        when(equipoGestionDao.equipoAdmiteEstadisticas(EQUIPO)).thenReturn(false);
        estadistica(partido(EQUIPO, INICIO.plusDays(9), false, 1, 0), PABLO, 1, 1, 4, 1);

        TarjetasEquipoResponse respuesta = service.obtenerTarjetas(USUARIO, EQUIPO);

        assertThat(respuesta.admiteEstadisticas()).isFalse();
        assertThat(respuesta.jugadores()).isEmpty();
    }

    @Test
    void unEquipoF11DevuelveTarjetasConAdmiteEstadisticasTrue() {
        estadistica(partido(EQUIPO, INICIO.plusDays(9), false, 1, 0), PABLO, 1, 1, 4, 1);

        TarjetasEquipoResponse respuesta = service.obtenerTarjetas(USUARIO, EQUIPO);

        assertThat(respuesta.admiteEstadisticas()).isTrue();
        assertThat(respuesta.jugadores()).hasSize(3);
    }

    @Test
    void sinEstadisticasDeJugadorNoHayGoleadoresNiAsistentesPeroSiPartidosYEntrenamientos() {
        when(equipoGestionDao.equipoAdmiteEstadisticas(EQUIPO)).thenReturn(false);
        long jugado = partido(EQUIPO, INICIO.plusDays(5), false, 3, 1);
        estadistica(jugado, PABLO, 3, 1, 0, 0);
        entrenamiento(HOY.minusDays(1), "18:00", true, PABLO, "PRESENTE", LUIS, "FALTA");

        EstadisticasEquipoResponse respuesta = service.obtenerEstadisticas(USUARIO, EQUIPO);

        assertThat(respuesta.admiteEstadisticas()).isFalse();
        assertThat(respuesta.goleadores()).isEmpty();
        assertThat(respuesta.asistentes()).isEmpty();
        assertThat(respuesta.partidos().jugados()).isEqualTo(1);
        assertThat(respuesta.partidos().golesFavor()).isEqualTo(3);
        assertThat(respuesta.entrenamientos().sesiones()).isEqualTo(1);
    }

    // ---- goleadores y asistentes ---------------------------------------------------------------

    @Test
    void goleadoresYAsistentesSonTop10ConDesempateYSoloLaTemporadaActiva() {
        long jugado = partido(EQUIPO, INICIO.plusDays(5), false, 3, 1);
        long anterior = partido(EQUIPO, LocalDate.of(2026, 2, 1), false, 9, 0);
        long cancelado = partido(EQUIPO, INICIO.plusDays(6), true, null, null);

        estadistica(jugado, PABLO, 2, 1, 0, 0);
        estadistica(jugado, LUIS, 2, 3, 0, 0);
        estadistica(jugado, ANA, 1, 3, 0, 0);
        estadistica(anterior, ANA, 9, 9, 0, 0);       // otra temporada: no cuenta
        estadistica(cancelado, PABLO, 9, 9, 0, 0);    // cancelado: no cuenta

        convocar(jugado, PABLO);
        convocar(jugado, LUIS);

        EstadisticasEquipoResponse r = service.obtenerEstadisticas(USUARIO, EQUIPO);

        // Pablo y Luis empatan a 2 goles: gana el de más asistencias.
        assertThat(r.goleadores()).extracting(EstadisticasEquipoResponse.Goleador::nombre)
                .containsExactly("Luis Lopez", "Pablo Garcia", "Ana Ruiz");
        assertThat(r.goleadores().get(0).goles()).isEqualTo(2);
        assertThat(r.goleadores().get(0).asistencias()).isEqualTo(3);
        assertThat(r.goleadores().get(0).partidos()).isEqualTo(1);
        assertThat(r.goleadores().get(2).partidos()).isZero();

        // Luis y Ana empatan a 3 asistencias: gana el de más goles.
        assertThat(r.asistentes()).extracting(EstadisticasEquipoResponse.Asistente::nombre)
                .containsExactly("Luis Lopez", "Ana Ruiz", "Pablo Garcia");
    }

    @Test
    void lasListasTienenComoMaximo10Jugadores() {
        long jugado = partido(EQUIPO, INICIO.plusDays(5), false, 1, 0);

        for (int i = 0; i < 12; i++) {
            long id = 200 + i;
            jugador(id, "Jugador" + i, "X", i + 10, "Cadete A");
            estadistica(jugado, id, i + 1, i + 1, 0, 0);
        }

        EstadisticasEquipoResponse r = service.obtenerEstadisticas(USUARIO, EQUIPO);

        assertThat(r.goleadores()).hasSize(10);
        assertThat(r.asistentes()).hasSize(10);
        assertThat(r.goleadores().get(0).goles()).isEqualTo(12);
        assertThat(r.goleadores().get(9).goles()).isEqualTo(3);
    }

    @Test
    void losPartidosJugadosSoloCuentanSiYaSeHanJugado() {
        long pasado = partido(EQUIPO, HOY.minusDays(3), false, 1, 0);
        long futuro = partido(EQUIPO, HOY.plusDays(3), false, null, null);

        estadistica(pasado, PABLO, 1, 0, 0, 0);
        convocar(pasado, PABLO);
        convocar(futuro, PABLO);

        EstadisticasEquipoResponse r = service.obtenerEstadisticas(USUARIO, EQUIPO);

        assertThat(r.goleadores().get(0).partidos()).isEqualTo(1);
    }

    // ---- partidos ------------------------------------------------------------------------------

    @Test
    void losPartidosSumanResultadosYGolesDeLaTemporadaActiva() {
        partido(EQUIPO, INICIO.plusDays(5), false, 3, 1);          // ganado
        partido(EQUIPO, INICIO.plusDays(12), false, 2, 2);         // empatado
        partido(EQUIPO, INICIO.plusDays(19), false, 0, 4);         // perdido
        partido(EQUIPO, INICIO.plusDays(26), false, 1, 0);         // ganado
        partido(EQUIPO, INICIO.plusDays(33), true, 5, 0);          // cancelado
        partido(EQUIPO, HOY.plusDays(4), false, null, null);       // sin resultado
        partido(EQUIPO, LocalDate.of(2026, 3, 1), false, 7, 0);    // temporada anterior
        partido(OTRO_EQUIPO, INICIO.plusDays(5), false, 9, 0);     // otro equipo

        EstadisticasEquipoResponse.Partidos p = service.obtenerEstadisticas(USUARIO, EQUIPO).partidos();

        assertThat(p.jugados()).isEqualTo(4);
        assertThat(p.ganados()).isEqualTo(2);
        assertThat(p.empatados()).isEqualTo(1);
        assertThat(p.perdidos()).isEqualTo(1);
        assertThat(p.golesFavor()).isEqualTo(6);
        assertThat(p.golesContra()).isEqualTo(7);
    }

    @Test
    void sinTemporadaActivaLasEstadisticasVanVacias() {
        jdbc.update("UPDATE TEMPORADAS SET ACTIVA = 0");
        partido(EQUIPO, INICIO.plusDays(5), false, 3, 1);
        entrenamiento(INICIO.plusDays(3), "18:00", true, PABLO, "PRESENTE");

        EstadisticasEquipoResponse r = service.obtenerEstadisticas(USUARIO, EQUIPO);

        assertThat(r.goleadores()).isEmpty();
        assertThat(r.asistentes()).isEmpty();
        assertThat(r.partidos().jugados()).isZero();
        assertThat(r.entrenamientos().sesiones()).isZero();
        assertThat(r.entrenamientos().asistenciaMedia()).isNull();
        assertThat(r.entrenamientos().porJugador()).isEmpty();
    }

    // ---- entrenamientos ------------------------------------------------------------------------

    @Test
    void laAsistenciaPorJugadorVaDeMenorAMayorYLaMediaEsLaDelEquipo() {
        // Pablo asiste a 4 de 4; Luis a 2 de 4 (retraso cuenta, falta no); Ana a 1 de 4.
        entrenamiento(INICIO.plusDays(3), "18:00", true,
                PABLO, "PRESENTE", LUIS, "PRESENTE", ANA, "FALTA");
        entrenamiento(INICIO.plusDays(5), "18:00", true,
                PABLO, "RETRASO", LUIS, "FALTA", ANA, "FALTA_JUSTIFICADA");
        entrenamiento(INICIO.plusDays(7), "18:00", true,
                PABLO, "MAL_COMPORTAMIENTO", LUIS, "RETRASO", ANA, "PRESENTE");
        entrenamiento(INICIO.plusDays(9), "18:00", false,
                PABLO, "PRESENTE", LUIS, "FALTA", ANA, "FALTA");

        EstadisticasEquipoResponse.Entrenamientos e = service.obtenerEstadisticas(USUARIO, EQUIPO).entrenamientos();

        assertThat(e.sesiones()).isEqualTo(4);
        assertThat(e.sesionesRevisadas()).isEqualTo(3);
        assertThat(e.sesionesSinRevisar()).isEqualTo(1);

        // El 4.º entrenamiento no tiene la lista revisada: no cuenta en los porcentajes
        // (sus filas son las de por defecto, no asistencia real).
        assertThat(e.porJugador()).extracting(
                EstadisticasEquipoResponse.AsistenciaJugador::nombre,
                EstadisticasEquipoResponse.AsistenciaJugador::asistidos,
                EstadisticasEquipoResponse.AsistenciaJugador::total,
                EstadisticasEquipoResponse.AsistenciaJugador::porcentaje)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Ana Ruiz", 1, 3, 33),
                        org.assertj.core.groups.Tuple.tuple("Luis Lopez", 2, 3, 67),
                        org.assertj.core.groups.Tuple.tuple("Pablo Garcia", 3, 3, 100));

        // (3 + 2 + 1) / 9 = 66,7 % -> 67
        assertThat(e.asistenciaMedia()).isEqualTo(67);
    }

    @Test
    void sinNingunaListaRevisadaNoHayPorcentajesPeroSiSesionesSinRevisar() {
        entrenamiento(INICIO.plusDays(3), "18:00", false, PABLO, "PRESENTE");
        entrenamiento(INICIO.plusDays(5), "18:00", false, PABLO, "PRESENTE");

        EstadisticasEquipoResponse.Entrenamientos e = service.obtenerEstadisticas(USUARIO, EQUIPO).entrenamientos();

        assertThat(e.sesiones()).isEqualTo(2);
        assertThat(e.sesionesSinRevisar()).isEqualTo(2);
        assertThat(e.asistenciaMedia()).isNull();
        assertThat(e.porJugador()).allSatisfy(j -> assertThat(j.porcentaje()).isNull());
    }

    @Test
    void unJugadorConvocadoEnDosEquiposSoloSumaLosPartidosDelEquipoConsultado() {
        // Pablo juega con el Cadete A (equipo 5) y es convocado también con el Cadete B (6).
        long propio = partido(EQUIPO, INICIO.plusDays(5), false, 1, 0);
        long ajeno = partido(OTRO_EQUIPO, INICIO.plusDays(6), false, 2, 0);

        estadistica(propio, PABLO, 1, 0, 2, 0);
        estadistica(ajeno, PABLO, 5, 3, 4, 1);

        convocar(propio, PABLO);
        convocar(ajeno, PABLO);

        // Tarjetas: solo las del partido de este equipo (la sanción va por competición).
        assertThat(tarjetasDe(PABLO).amarillas()).isEqualTo(2);
        assertThat(tarjetasDe(PABLO).rojas()).isZero();
        assertThat(tarjetasDe(PABLO).estado()).isEqualTo("OK");

        EstadisticasEquipoResponse r = service.obtenerEstadisticas(USUARIO, EQUIPO);

        EstadisticasEquipoResponse.Goleador pablo = r.goleadores().get(0);

        assertThat(pablo.goles()).isEqualTo(1);
        // Y los partidos jugados (subconsulta de convocatorias) también son solo los de este equipo.
        assertThat(pablo.partidos()).isEqualTo(1);
    }

    @Test
    void soloCuentanLosEntrenamientosPasadosYDeLaTemporadaActiva() {
        entrenamiento(LocalDate.of(2026, 3, 1), "18:00", true, PABLO, "FALTA");          // temporada anterior
        entrenamiento(INICIO.plusDays(3), "18:00", true, PABLO, "PRESENTE");             // cuenta
        entrenamiento(HOY.minusDays(1), "18:00", true, PABLO, "PRESENTE");               // ayer: cuenta
        entrenamiento(HOY.plusDays(1), "18:00", true, PABLO, "PRESENTE");                // futuro (PRESENTE por defecto)
        entrenamiento(HOY.plusDays(5), "18:00", false, PABLO, "PRESENTE");              // futuro

        EstadisticasEquipoResponse.Entrenamientos e = service.obtenerEstadisticas(USUARIO, EQUIPO).entrenamientos();

        assertThat(e.sesiones()).isEqualTo(2);
        assertThat(e.porJugador().stream().filter(j -> j.jugadorId() == PABLO).findFirst().orElseThrow().total())
                .isEqualTo(2);
    }

    @Test
    void elEntrenamientoDeHoyCuentaSoloSiSuHoraYaHaPasado() {
        // Son las 10:00.
        entrenamiento(HOY, "09:00", true, PABLO, "FALTA", LUIS, "PRESENTE");   // ya ha pasado
        entrenamiento(HOY, "18:00", false, PABLO, "PRESENTE", LUIS, "PRESENTE"); // todavía no
        entrenamiento(HOY, null, false, PABLO, "PRESENTE", LUIS, "PRESENTE");  // sin hora: hasta que acabe el día

        EstadisticasEquipoResponse.Entrenamientos e = service.obtenerEstadisticas(USUARIO, EQUIPO).entrenamientos();

        assertThat(e.sesiones()).isEqualTo(1);
        assertThat(e.sesionesRevisadas()).isEqualTo(1);
        assertThat(e.porJugador().stream().filter(j -> j.jugadorId() == PABLO).findFirst().orElseThrow()
                .porcentaje()).isZero();
        assertThat(e.porJugador().stream().filter(j -> j.jugadorId() == LUIS).findFirst().orElseThrow()
                .porcentaje()).isEqualTo(100);
        assertThat(e.asistenciaMedia()).isEqualTo(50);
    }

    @Test
    void sinEntrenamientosLaAsistenciaMediaYLosPorcentajesSonNulos() {
        EstadisticasEquipoResponse.Entrenamientos e = service.obtenerEstadisticas(USUARIO, EQUIPO).entrenamientos();

        assertThat(e.sesiones()).isZero();
        assertThat(e.asistenciaMedia()).isNull();
        assertThat(e.porJugador()).hasSize(3).allSatisfy(j -> {
            assertThat(j.total()).isZero();
            assertThat(j.porcentaje()).isNull();
        });
    }

    // ---- plantilla por temporada ---------------------------------------------------------------

    private void plantilla(long jugador, long temporada, Long equipo, Integer dorsal, boolean baja) {
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, EQUIPO_ID, DORSAL, ESTADO)"
                + " VALUES (?, ?, ?, ?, ?)", jugador, temporada, equipo, dorsal,
                baja ? "BAJA" : "CONFIRMADO");
    }

    /** En 2025/2026: Marta (hoy en el B) jugaba en el A con el 11; Pablo estaba en el B y Luis de baja. */
    private void plantillaDe2025() {
        plantilla(MARTA, 1, EQUIPO, 11, false);
        plantilla(PABLO, 1, OTRO_EQUIPO, 9, false);
        plantilla(LUIS, 1, EQUIPO, 1, true);
        plantilla(MARTA, 2, OTRO_EQUIPO, 7, false);
    }

    @Test
    void laTemporadaPasadaSacaLaPlantillaDeEntoncesConSuDorsalYSusGoles() {
        plantillaDe2025();
        long antiguo = partido(EQUIPO, LocalDate.of(2026, 3, 1), false, 2, 0);
        estadistica(antiguo, MARTA, 2, 1, 0, 0);
        estadistica(antiguo, PABLO, 4, 0, 0, 0);

        List<com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo> lista = new ResumenTemporadaDaoImpl(jdbc)
                .estadisticasJugadoresDelEquipo(EQUIPO, LocalDate.of(2025, 9, 1), LocalDate.of(2026, 6, 30), HOY);

        // Ana no tiene fila en 2025/2026: se mantiene lo de JUGADORES (Cadete A).
        assertThat(lista).extracting(com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo::jugadorId)
                .containsExactlyInAnyOrder(MARTA, ANA);
        com.mikedev.mutxamelcf.model.EstadisticasJugadorEquipo marta = lista.stream()
                .filter(j -> j.jugadorId() == MARTA).findFirst().orElseThrow();
        assertThat(marta.dorsal()).isEqualTo(11);
        assertThat(marta.goles()).isEqualTo(2);
        assertThat(marta.asistencias()).isEqualTo(1);
    }

    @Test
    void laTemporadaPasadaSacaLaAsistenciaDeLaPlantillaDeEntonces() {
        plantillaDe2025();
        long id = siguienteId++;
        jdbc.update("INSERT INTO ENTRENAMIENTOS (ID, EQUIPO_ID, FECHA, USUARIO_ENTRENADOR_ID, LISTA_REVISADA)"
                + " VALUES (?, ?, ?, 1, 1)", id, EQUIPO, Date.valueOf(LocalDate.of(2026, 3, 2)));
        jdbc.update("INSERT INTO ENTRENAMIENTO_ASISTENCIA VALUES (?, ?, 'PRESENTE')", id, MARTA);
        jdbc.update("INSERT INTO ENTRENAMIENTO_ASISTENCIA VALUES (?, ?, 'FALTA')", id, ANA);

        List<com.mikedev.mutxamelcf.model.AsistenciaJugadorEquipo> lista = new ResumenTemporadaDaoImpl(jdbc)
                .asistenciaEntrenamientosDelEquipo(EQUIPO, LocalDate.of(2025, 9, 1), LocalDate.of(2026, 6, 30));

        assertThat(lista).extracting(com.mikedev.mutxamelcf.model.AsistenciaJugadorEquipo::jugadorId)
                .containsExactlyInAnyOrder(MARTA, ANA);
        com.mikedev.mutxamelcf.model.AsistenciaJugadorEquipo marta = lista.stream()
                .filter(j -> j.jugadorId() == MARTA).findFirst().orElseThrow();
        assertThat(marta.dorsal()).isEqualTo(11);
        assertThat(marta.total()).isEqualTo(1);
        assertThat(marta.asistidos()).isEqualTo(1);
    }

    @Test
    void enLaTemporadaActivaUnJugadorConFilaDeBajaODeOtroEquipoNoSaleEnLasTarjetas() {
        // JUGADORES dice que Pablo y Luis son del Cadete A, pero su fila de 2026/2027 dice otra cosa.
        plantilla(PABLO, 2, EQUIPO, 10, true);
        plantilla(LUIS, 2, OTRO_EQUIPO, 1, false);
        plantilla(MARTA, 2, EQUIPO, 7, false);

        assertThat(tarjetas()).extracting(TarjetasJugadorResponse::jugadorId)
                .containsExactlyInAnyOrder(ANA, MARTA);
        assertThat(tarjetasDe(MARTA).dorsal()).isEqualTo(7);
    }

    @Test
    void losEntrenamientosDeOtroEquipoNoCuentan() {
        long otro = siguienteId++;
        jdbc.update("INSERT INTO ENTRENAMIENTOS (ID, EQUIPO_ID, FECHA, USUARIO_ENTRENADOR_ID) VALUES (?, ?, ?, 1)",
                otro, OTRO_EQUIPO, Date.valueOf(INICIO.plusDays(3)));
        jdbc.update("INSERT INTO ENTRENAMIENTO_ASISTENCIA VALUES (?, ?, 'PRESENTE')", otro, PABLO);

        assertThat(service.obtenerEstadisticas(USUARIO, EQUIPO).entrenamientos().sesiones()).isZero();
    }

    // ---- jugadores de otros equipos invitados (docs/convocatoria-invitados.md, §3) ----------------

    /** El jugador juega el partido como invitado de otro equipo con ese estado (su equipo habitual es OTRO_EQUIPO). */
    private void convocarComoInvitado(long partido, long jugador, String estado) {
        long convocatoria = siguienteId++;
        jdbc.update("INSERT INTO CONVOCATORIAS VALUES (?, ?)", convocatoria, partido);
        jdbc.update("INSERT INTO CONVOCATORIA_JUGADOR (CONVOCATORIA_ID, JUGADOR_ID, EQUIPO_ORIGEN_ID,"
                + " ESTADO_INVITACION) VALUES (?, ?, ?, ?)", convocatoria, jugador, OTRO_EQUIPO, estado);
    }

    @Test
    void unInvitadoAutorizadoSaleEnLasEstadisticasDelEquipoConElQueJugoMarcadoComoInvitado() {
        // Marta es del Cadete B (6) y juega de invitada con el Cadete A (5).
        long conElA = partido(EQUIPO, INICIO.plusDays(5), false, 3, 0);
        estadistica(conElA, PABLO, 1, 0, 0, 0);
        estadistica(conElA, MARTA, 2, 1, 1, 0);
        convocar(conElA, PABLO);
        convocarComoInvitado(conElA, MARTA, "AUTORIZADO");

        EstadisticasEquipoResponse r = service.obtenerEstadisticas(USUARIO, EQUIPO);

        EstadisticasEquipoResponse.Goleador marta = r.goleadores().stream()
                .filter(g -> g.jugadorId() == MARTA).findFirst().orElseThrow();

        assertThat(marta.invitado()).isTrue();
        assertThat(marta.goles()).isEqualTo(2);
        assertThat(marta.asistencias()).isEqualTo(1);
        assertThat(marta.partidos()).isEqualTo(1);
        assertThat(r.goleadores().stream().filter(g -> g.jugadorId() == PABLO).findFirst().orElseThrow().invitado())
                .isFalse();
        assertThat(r.asistentes()).extracting(EstadisticasEquipoResponse.Asistente::jugadorId).containsExactly(MARTA);
        assertThat(r.asistentes().get(0).invitado()).isTrue();

        assertThat(tarjetasDe(MARTA).invitado()).isTrue();
        assertThat(tarjetasDe(MARTA).amarillas()).isEqualTo(1);
        assertThat(tarjetasDe(PABLO).invitado()).isFalse();
    }

    @Test
    void lasEstadisticasDelInvitadoNoSeSumanAlEquipoDeOrigen() {
        long conElA = partido(EQUIPO, INICIO.plusDays(5), false, 3, 0);
        long conElB = partido(OTRO_EQUIPO, INICIO.plusDays(6), false, 1, 1);

        estadistica(conElA, MARTA, 4, 2, 3, 1);
        estadistica(conElB, MARTA, 1, 0, 1, 0);
        convocarComoInvitado(conElA, MARTA, "AUTORIZADO");
        convocar(conElB, MARTA);

        // En las estadisticas de su propio equipo (el B) solo cuenta lo que hizo con el B.
        EstadisticasEquipoResponse delB = service.obtenerEstadisticas(USUARIO, OTRO_EQUIPO);

        assertThat(delB.goleadores()).extracting(EstadisticasEquipoResponse.Goleador::jugadorId).containsExactly(MARTA);
        assertThat(delB.goleadores().get(0).goles()).isEqualTo(1);
        assertThat(delB.goleadores().get(0).partidos()).isEqualTo(1);
        assertThat(delB.goleadores().get(0).invitado()).isFalse();

        TarjetasJugadorResponse tarjetasDelB = service.obtenerTarjetas(USUARIO, OTRO_EQUIPO).jugadores().stream()
                .filter(t -> t.jugadorId() == MARTA).findFirst().orElseThrow();

        assertThat(tarjetasDelB.amarillas()).isEqualTo(1);
        assertThat(tarjetasDelB.rojas()).isZero();
        assertThat(tarjetasDelB.invitado()).isFalse();
    }

    @Test
    void lasTarjetasDelInvitadoSeCuentanPorSeparadoEnCadaCompeticion() {
        // 4 amarillas con su equipo (a una de la sancion) y 2 de invitada con el A: no suman 6.
        long conElA = partido(EQUIPO, INICIO.plusDays(5), false, 3, 0);
        long conElB = partido(OTRO_EQUIPO, INICIO.plusDays(6), false, 1, 1);

        estadistica(conElB, MARTA, 0, 0, 4, 0);
        estadistica(conElA, MARTA, 0, 0, 2, 0);
        convocar(conElB, MARTA);
        convocarComoInvitado(conElA, MARTA, "AUTORIZADO");

        assertThat(tarjetasDe(MARTA).amarillas()).isEqualTo(2);
        assertThat(tarjetasDe(MARTA).estado()).isEqualTo("OK");

        TarjetasJugadorResponse enElB = service.obtenerTarjetas(USUARIO, OTRO_EQUIPO).jugadores().stream()
                .filter(t -> t.jugadorId() == MARTA).findFirst().orElseThrow();

        assertThat(enElB.amarillas()).isEqualTo(4);
        assertThat(enElB.estado()).isEqualTo("A_UNA");
    }

    @Test
    void unInvitadoQueNoEstaAutorizadoNoSaleEnLasEstadisticasDelEquipo() {
        long conElA = partido(EQUIPO, INICIO.plusDays(5), false, 3, 0);
        estadistica(conElA, MARTA, 2, 1, 1, 0);
        convocarComoInvitado(conElA, MARTA, "PENDIENTE_ENTRENADOR");

        assertThat(service.obtenerEstadisticas(USUARIO, EQUIPO).goleadores()).isEmpty();
        assertThat(tarjetas()).extracting(TarjetasJugadorResponse::jugadorId).doesNotContain(MARTA);
    }

    @Test
    void unPartidoCanceladoNoCuentaParaElInvitado() {
        long cancelado = partido(EQUIPO, INICIO.plusDays(5), true, null, null);
        estadistica(cancelado, MARTA, 2, 1, 1, 0);
        convocarComoInvitado(cancelado, MARTA, "AUTORIZADO");

        assertThat(tarjetas()).extracting(TarjetasJugadorResponse::jugadorId).doesNotContain(MARTA);
    }
}
