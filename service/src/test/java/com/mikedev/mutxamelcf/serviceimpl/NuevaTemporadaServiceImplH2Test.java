package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.mikedev.mutxamelcf.dao.CategoriaEdadDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaEntrenamientoDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.dao.impl.CategoriaEdadDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.EquipoDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.JugadorDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.JugadorTemporadaDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.TemporadaDaoImpl;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.ResumenAplicacionDTO;
import com.mikedev.mutxamelcf.model.RevisionTemporadaDTO;
import com.mikedev.mutxamelcf.service.CategoriaPorEdad;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.NuevaTemporadaService;

/**
 * Asistente "Nueva temporada" y mantenimiento de la temporada activa, con un contexto Spring de verdad
 * (proxies @Transactional sobre H2 en modo Oracle) y los DAO reales. Todos los datos están inventados.
 *
 * <p>Temporadas: 1 "2026-27" (activa), 2 "2027-28" (destino válido), 3 "2025-26" (pasada). Categorías por edad de
 * 2026-27: Escuelita 0-5, Prebenjamín 6-7, Benjamín 8-9, Alevín 10-11, Infantil 12-13, Cadete 14-15, Juvenil 16-18.
 * La edad en 2027-28 es la de 2026-27 más uno.
 */
class NuevaTemporadaServiceImplH2Test {

    private static final long ACTIVA = 1L;
    private static final long DESTINO = 2L;
    private static final long PASADA = 3L;
    private static final LocalDate HOY = LocalDate.of(2026, 10, 27);

    // Equipos (deporte F; el grupo es la posición dentro de la categoría)
    private static final long ALEVIN_A = 1L;
    private static final long ALEVIN_B = 2L;
    private static final long INFANTIL_A = 3L;
    private static final long INFANTIL_B = 4L;
    private static final long CADETE_A = 5L;
    private static final long JUVENIL_A = 6L;
    private static final long INFANTIL_C = 7L;
    private static final long INFANTIL_D = 8L;
    private static final long CADETE_D1 = 9L;
    private static final long CADETE_D2 = 10L;
    private static final long PRIMER_EQUIPO = 11L;
    private static final long VETERANOS = 12L;
    private static final long FILIAL = 13L;

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
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneId.of("Europe/Madrid"));
        }

        // Espías para poder hacer fallar una operación a mitad y comprobar que no queda nada a medias.
        @Bean
        JugadorDao jugadorDao(JdbcTemplate jdbc) {
            return spy(new JugadorDaoImpl(jdbc));
        }

        @Bean
        JugadorTemporadaDao jugadorTemporadaDao(JdbcTemplate jdbc) {
            return spy(new JugadorTemporadaDaoImpl(jdbc));
        }

        @Bean
        TemporadaDao temporadaDao(JdbcTemplate jdbc) {
            return new TemporadaDaoImpl(jdbc);
        }

        @Bean
        EquipoDao equipoDao(JdbcTemplate jdbc) {
            return new EquipoDaoImpl(jdbc);
        }

        @Bean
        CategoriaEdadDao categoriaEdadDao(JdbcTemplate jdbc) {
            return new CategoriaEdadDaoImpl(jdbc);
        }

        @Bean
        CategoriaPorEdad categoriaPorEdad(CategoriaEdadDao categoriaEdadDao) {
            return new CategoriaPorEdadImpl(categoriaEdadDao);
        }

        @Bean
        NuevaTemporadaService nuevaTemporadaService(TemporadaDao temporadaDao, JugadorDao jugadorDao,
                JugadorTemporadaDao jugadorTemporadaDao, EquipoDao equipoDao, CategoriaPorEdad categoriaPorEdad,
                Clock clock) {
            return new NuevaTemporadaServiceImpl(temporadaDao, jugadorDao, jugadorTemporadaDao, equipoDao,
                    categoriaPorEdad, clock);
        }

        @Bean
        com.mikedev.mutxamelcf.service.TemporadaService temporadaService(TemporadaDao temporadaDao,
                JugadorTemporadaDao jugadorTemporadaDao) {
            return new TemporadaServiceImpl(temporadaDao, jugadorTemporadaDao);
        }

        @Bean
        JugadorService jugadorService(JugadorDao jugadorDao, JugadorTemporadaDao jugadorTemporadaDao,
                EquipoDao equipoDao, TemporadaDao temporadaDao, Clock clock) {
            return new JugadorServiceImpl(jugadorDao, mock(FamiliarJugadorService.class),
                    mock(UsuarioAppVinculoDao.class), mock(CuotaJugadorService.class),
                    mock(ConvocatoriaJugadorDao.class), mock(EntrenamientoAsistenciaDao.class),
                    mock(PartidoEstadisticaJugadorDao.class), mock(PartidoLiveEventoDao.class),
                    mock(JustificacionFaltaEntrenamientoDao.class), mock(JustificacionFaltaPartidoDao.class),
                    equipoDao, temporadaDao, jugadorTemporadaDao, clock);
        }
    }

    private AnnotationConfigApplicationContext contexto;
    private JdbcTemplate jdbc;
    private NuevaTemporadaService asistente;
    private JugadorService jugadores;
    private JugadorDao jugadorDao;
    private JugadorTemporadaDao jugadorTemporadaDao;

    @BeforeEach
    void setUp() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        jdbc = contexto.getBean(JdbcTemplate.class);
        asistente = contexto.getBean(NuevaTemporadaService.class);
        jugadores = contexto.getBean(JugadorService.class);
        jugadorDao = contexto.getBean(JugadorDao.class);
        jugadorTemporadaDao = contexto.getBean(JugadorTemporadaDao.class);

        jdbc.execute("""
                CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), CATEGORIA VARCHAR2(100),
                    GRUPO VARCHAR2(10), ORDEN VARCHAR2(5), DEPORTE VARCHAR2(10), MODALIDAD VARCHAR2(5))""");
        jdbc.execute("""
                CREATE TABLE TEMPORADAS (ID NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, NOMBRE VARCHAR2(50),
                    FECHA_INICIO DATE, FECHA_FIN DATE, ACTIVA NUMBER(1))""");
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crearPlantillas(jdbc);
        EsquemaH2.crearTransicion(jdbc);

        temporada(ACTIVA, "2026-27", LocalDate.of(2026, 9, 1), 1);
        temporada(DESTINO, "2027-28", LocalDate.of(2027, 9, 1), 0);
        temporada(PASADA, "2025-26", LocalDate.of(2025, 9, 1), 0);

        for (Object[] c : new Object[][] { { "Escuelita", 0, 5, 1 }, { "Prebenjamín", 6, 7, 2 },
                { "Benjamín", 8, 9, 3 }, { "Alevín", 10, 11, 4 }, { "Infantil", 12, 13, 5 }, { "Cadete", 14, 15, 6 },
                { "Juvenil", 16, 18, 7 } }) {
            jdbc.update("INSERT INTO CATEGORIAS_EDAD VALUES (?, ?, ?, ?)", c);
        }

        equipo(ALEVIN_A, "Alevín A", "Alevín", "A");
        equipo(ALEVIN_B, "Alevín B", "Alevín", "B");
        equipo(INFANTIL_A, "Infantil A", "Infantil", "A");
        equipo(INFANTIL_B, "Infantil B", "Infantil", "B");
        equipo(CADETE_A, "Cadete A", "Cadete", "A");
        equipo(JUVENIL_A, "Juvenil A", "Juvenil", "A");
        equipo(INFANTIL_C, "Infantil C", "Infantil", "C");   // no hay Cadete C
        equipo(INFANTIL_D, "Infantil D", "Infantil", "D");   // hay dos Cadete D: ambiguo
        equipo(CADETE_D1, "Cadete D1", "Cadete", "D");
        equipo(CADETE_D2, "Cadete D2", "Cadete", "D");
        // Categorías de adultos: NO están en CATEGORIAS_EDAD
        equipo(PRIMER_EQUIPO, "Primer Equipo", "Primer Equipo", "A");
        equipo(VETERANOS, "Veteranos", "Veteranos", "A");
        equipo(FILIAL, "Filial", "Filial", "A");
    }

    @AfterEach
    void tearDown() {
        contexto.close();
    }

    // ---- datos ----------------------------------------------------------------------------

    private void temporada(long id, String nombre, LocalDate inicio, int activa) {
        jdbc.update("INSERT INTO TEMPORADAS (ID, NOMBRE, FECHA_INICIO, FECHA_FIN, ACTIVA) VALUES (?, ?, ?, ?, ?)", id,
                nombre, Date.valueOf(inicio), Date.valueOf(inicio.plusMonths(9)), activa);
    }

    private void equipo(long id, String nombre, String categoria, String grupo) {
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA, GRUPO, ORDEN, DEPORTE, MODALIDAD)"
                + " VALUES (?, ?, ?, ?, 'X', 'F', 'F11')", id, nombre, categoria, grupo);
    }

    /** Apellidos "Ap01", "Ap02"...: así el orden por apellidos coincide con el id. */
    private void jugador(long id, Integer anio, Long equipoId, Integer dorsal) {
        String nombreEquipo = equipoId == null ? null
                : jdbc.queryForObject("SELECT NOMBRE FROM EQUIPO WHERE ID = ?", String.class, equipoId);
        String categoria = equipoId == null ? null
                : jdbc.queryForObject("SELECT CATEGORIA FROM EQUIPO WHERE ID = ?", String.class, equipoId);
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, CATEGORIA, DEPORTE, DORSAL, EQUIPO, EQUIPO_ID,"
                + " ANIO_NACIMIENTO) VALUES (?, ?, ?, ?, 'F', ?, ?, ?, ?)", id, "Jugador" + id,
                String.format("Ap%02d", id), categoria, dorsal, nombreEquipo, equipoId, anio);
    }

    /** Un jugador por cada regla de la propuesta. */
    private void datosDeLasReglas() {
        jugador(1, 2016, ALEVIN_A, 5);     // 10 años: Alevín hoy y en 27-28 (11) -> se queda
        jugador(2, 2015, ALEVIN_B, 8);     // 11 -> 12: pasa a Infantil -> Infantil B (mismo grupo B)
        jugador(3, null, CADETE_A, 3);     // sin año de nacimiento
        jugador(4, 2009, JUVENIL_A, 4);    // 17 -> 18: Juvenil, se queda
        jugador(5, 2008, JUVENIL_A, 11);   // 18 -> 19: supera la edad máxima
        jugador(6, 2012, ALEVIN_A, 6);     // 14 (Cadete) pero juega en Alevín: juega por abajo
        jugador(7, 2013, INFANTIL_D, 9);   // 13 -> 14 Cadete: dos Cadete D -> ambiguo
        jugador(8, 2013, INFANTIL_C, 2);   // 13 -> 14 Cadete: no hay Cadete C
        jugador(9, 2013, INFANTIL_A, 7);   // 13 -> 14 Cadete -> Cadete A
        jugador(11, 2011, JUVENIL_A, 10);  // 15 (Cadete) juega en Juvenil: por arriba; el año que viene le toca Juvenil
        jugador(12, 2015, null, null);     // activo sin equipo
        jugador(13, null, PRIMER_EQUIPO, 1);   // adulto sin año de nacimiento
        jugador(14, null, VETERANOS, 22);      // adulto sin año de nacimiento
        jugador(15, 1985, PRIMER_EQUIPO, 8);   // adulto con año: tampoco se le toca
        // el 10 está de baja desde antes
        jugador(10, 2010, JUVENIL_A, 1);
        jdbc.update("UPDATE JUGADORES SET FECHA_BAJA = DATE '2026-06-30', EQUIPO_ID = NULL WHERE ID = 10");
    }

    private JugadorTemporada fila(long jugadorId) {
        return jugadorTemporadaDao.obtener(jugadorId, DESTINO);
    }

    private Long equipoDe(long jugadorId) {
        return jdbc.queryForObject("SELECT EQUIPO_ID FROM JUGADORES WHERE ID = ?", Long.class, jugadorId);
    }

    private long temporadaActiva() {
        return jdbc.queryForObject("SELECT ID FROM TEMPORADAS WHERE ACTIVA = 1", Long.class);
    }

    private int filas(long temporada) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM JUGADOR_TEMPORADA WHERE TEMPORADA_ID = ?", Integer.class,
                temporada);
    }

    // ---- destino --------------------------------------------------------------------------------

    @Test
    void soloEsDestinoUnaTemporadaNoActivaYPosteriorALaActiva() {
        assertThat(asistente.temporadasDestino()).extracting(t -> t.getId()).containsExactly(DESTINO);

        assertThatThrownBy(() -> asistente.generarPropuesta(ACTIVA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.generarPropuesta(PASADA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.generarPropuesta(99L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.generarPropuesta(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- reglas de la propuesta -------------------------------------------------------------------

    @Test
    void creaUnaFilaPorCadaJugadorActivoYNingunaParaLasBajas() {
        datosDeLasReglas();

        int creadas = asistente.generarPropuesta(DESTINO);

        assertThat(creadas).isEqualTo(14);
        assertThat(filas(DESTINO)).isEqualTo(14);
        assertThat(fila(10)).isNull();
    }

    @Test
    void losFormativosVanASuCategoriaPorEdadSinEquipoNiDorsalNiEquipoDefinitivo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        // 1: Alevín A -> Alevín; 2: Alevín B -> Infantil; 4: Juvenil A -> Juvenil; 7, 8 y 9: Infantil -> Cadete
        Map<Long, String> categorias = Map.of(1L, "Alevín", 2L, "Infantil", 4L, "Juvenil", 7L, "Cadete", 8L, "Cadete",
                9L, "Cadete");

        categorias.forEach((jugador, categoria) -> {
            JugadorTemporada f = fila(jugador);
            assertThat(f.getCategoria()).as("categoría " + jugador).isEqualTo(categoria);
            assertThat(f.getEquipoId()).as("equipo " + jugador).isNull();
            assertThat(f.getEquipoNombre()).as("nombre de equipo " + jugador).isNull();
            assertThat(f.getDorsal()).as("dorsal " + jugador).isNull();
            assertThat(f.isEquipoDefinitivo()).as("equipo definitivo " + jugador).isFalse();
            assertThat(f.getEstado()).as("estado " + jugador).isEqualTo("CONFIRMADO");
            assertThat(f.getContinuidad()).as("continuidad " + jugador).isEqualTo("PENDIENTE");
            // Sin notas ni marca propia: lo único que lo deja pendiente es que la familia no ha contestado
            assertThat(f.isPendienteRevision()).as("marca " + jugador).isFalse();
            assertThat(f.getNota()).as("nota " + jugador).isNull();
            assertThat(f.requiereRevision()).as("a revisar " + jugador).isTrue();
        });
    }

    @Test
    void sinAnioDeNacimientoConservaSuCategoriaSinEquipoYPendiente() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(3);   // Cadete A, sin año

        assertThat(f.getEquipoId()).isNull();
        assertThat(f.getCategoria()).isEqualTo("Cadete");
        assertThat(f.getDorsal()).isNull();
        assertThat(f.isPendienteRevision()).isTrue();
        assertThat(f.getNota()).isEqualTo("Sin año de nacimiento");
    }

    @Test
    void unJuvenilDeUltimoAnioQuedaSinEquipoNiCategoriaPendienteYSinProponerLaBaja() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(5);   // 19 años en 2027-28: supera la edad máxima (18)

        assertThat(f.getEquipoId()).isNull();
        assertThat(f.getCategoria()).isNull();
        assertThat(f.getDorsal()).isNull();
        assertThat(f.isBaja()).isFalse();
        assertThat(f.isPendienteRevision()).isTrue();
        assertThat(f.getNota()).isEqualTo("Termina Juvenil: ¿Filial, Primer Equipo o baja?");
    }

    @Test
    void filialPrimerEquipoYVeteranosSeQuedanEnSuEquipoConSuDorsalYEquipoDefinitivo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        for (long id : new long[] { 13L, 14L, 15L }) {   // Primer Equipo y Veteranos, con y sin año
            JugadorTemporada f = fila(id);
            assertThat(f.isPendienteRevision()).as("pendiente " + id).isFalse();
            assertThat(f.isBaja()).as("baja " + id).isFalse();
            assertThat(f.getNota()).as("nota " + id).isNull();
            assertThat(f.isEquipoDefinitivo()).as("equipo definitivo " + id).isTrue();
            // Se quedan: no esperan la respuesta de las familias
            assertThat(f.getContinuidad()).as("continuidad " + id).isEqualTo("SIGUE");
            assertThat(f.requiereRevision()).as("a revisar " + id).isFalse();
        }
        assertThat(fila(13).getEquipoId()).isEqualTo(PRIMER_EQUIPO);
        assertThat(fila(13).getDorsal()).isEqualTo(1);
        assertThat(fila(14).getEquipoId()).isEqualTo(VETERANOS);
        assertThat(fila(14).getDorsal()).isEqualTo(22);
        assertThat(fila(15).getEquipoId()).isEqualTo(PRIMER_EQUIPO);   // año 1985: nunca se propone la baja por edad
        assertThat(fila(15).getDorsal()).isEqualTo(8);
    }

    @Test
    void elCoordinadorPuedeMandarAlJuvenilAFilialEnLaRevision() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        asistente.guardarFila(DESTINO, 5L, FILIAL, 11, false, 2008);

        assertThat(fila(5).getEquipoId()).isEqualTo(FILIAL);
        assertThat(fila(5).getCategoria()).isEqualTo("Filial");
        assertThat(fila(5).getDorsal()).isEqualTo(11);
        assertThat(fila(5).isEquipoDefinitivo()).isTrue();
        assertThat(fila(5).isPendienteRevision()).isFalse();
    }

    @Test
    void siJuegaPorAbajoSeProponeSuCategoriaPorEdadYPendiente() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(6);   // le toca Cadete, juega en Alevín A

        assertThat(f.getEquipoId()).isNull();
        assertThat(f.getCategoria()).isEqualTo("Cadete");
        assertThat(f.getDorsal()).isNull();
        assertThat(f.isPendienteRevision()).isTrue();
        assertThat(f.getNota()).isEqualTo("Jugaba fuera de su categoría (Alevín)");
    }

    @Test
    void siJuegaPorArribaYAhoraLeTocaSuCategoriaVaAEllaPeroPendiente() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(11);   // este año Cadete (juega en Juvenil A); el que viene le toca Juvenil

        assertThat(f.getEquipoId()).isNull();
        assertThat(f.getCategoria()).isEqualTo("Juvenil");
        assertThat(f.isPendienteRevision()).isTrue();
        assertThat(f.getNota()).isEqualTo("Jugaba fuera de su categoría (Juvenil)");
    }

    @Test
    void unJugadorActivoSinEquipoVaAsuCategoriaPorEdad() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(12);   // 2015: Infantil en 2027-28

        assertThat(f.getEquipoId()).isNull();
        assertThat(f.getCategoria()).isEqualTo("Infantil");
        assertThat(f.isPendienteRevision()).isFalse();
    }

    @Test
    void generarNoSobrescribeLasFilasExistentesYSoloAnadeLasQueFaltan() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        asistente.guardarFila(DESTINO, 3L, ALEVIN_A, 77, false, 2013);
        jugador(20, 2016, ALEVIN_A, 14);   // creado después de generar

        int creadas = asistente.generarPropuesta(DESTINO);

        assertThat(creadas).isEqualTo(1);
        assertThat(fila(3).getDorsal()).isEqualTo(77);
        assertThat(fila(3).getEquipoId()).isEqualTo(ALEVIN_A);
        assertThat(fila(20)).isNotNull();
    }

    @Test
    void regenerarBorraElBorradorEnteroYLoVuelveACalcular() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        asistente.guardarFila(DESTINO, 3L, ALEVIN_A, 77, false, 2013);

        int creadas = asistente.regenerar(DESTINO);

        assertThat(creadas).isEqualTo(14);
        assertThat(fila(3).getEquipoId()).isNull();
        assertThat(fila(3).getDorsal()).isNull();
        assertThat(fila(3).isPendienteRevision()).isTrue();
    }

    // ---- continuidad ------------------------------------------------------------------------------

    @Test
    void sigueResuelveLaContinuidadYDejaLaFilaSinRevisar() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        asistente.marcarContinuidad(DESTINO, 1L, "SIGUE");

        JugadorTemporada f = fila(1);
        assertThat(f.getContinuidad()).isEqualTo("SIGUE");
        assertThat(f.getFechaContinuidad()).isNotNull();
        assertThat(f.getEstado()).isEqualTo("CONFIRMADO");
        assertThat(f.requiereRevision()).isFalse();
        // Las demás siguen con su respuesta pendiente
        assertThat(fila(2).requiereRevision()).isTrue();
    }

    @Test
    void sigueNoQuitaLaRevisionSiHayOtroMotivo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        asistente.marcarContinuidad(DESTINO, 3L, "SIGUE");   // 3: sin año de nacimiento

        assertThat(fila(3).requiereRevision()).isTrue();
    }

    @Test
    void dudaYSinRespuestaDejanLaFilaPendienteDeRevisar() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        asistente.marcarContinuidad(DESTINO, 1L, "SIGUE");

        asistente.marcarContinuidad(DESTINO, 1L, "DUDA");
        assertThat(fila(1).requiereRevision()).isTrue();

        asistente.marcarContinuidad(DESTINO, 1L, "SIGUE");
        asistente.marcarContinuidad(DESTINO, 1L, "PENDIENTE");
        assertThat(fila(1).requiereRevision()).isTrue();
        assertThat(fila(1).getContinuidad()).isEqualTo("PENDIENTE");
    }

    @Test
    void noSigueProponeLaBajaPeroQuedaPendienteHastaQueElCoordinadorLaConfirma() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        asistente.marcarContinuidad(DESTINO, 1L, "NO_SIGUE");

        JugadorTemporada propuesta = fila(1);
        assertThat(propuesta.getContinuidad()).isEqualTo("NO_SIGUE");
        assertThat(propuesta.getEstado()).isEqualTo("BAJA");
        assertThat(propuesta.isBaja()).isTrue();
        assertThat(propuesta.requiereRevision()).isTrue();
        assertThat(asistente.resumenAplicacion(DESTINO).bloqueos()).anyMatch(b -> b.contains("pendiente"));
        assertThat(asistente.revision(DESTINO).bajas()).extracting(RevisionTemporadaDTO.Fila::jugadorId)
                .containsExactly(1L);

        // El coordinador la confirma guardando la fila con la baja marcada
        asistente.guardarFila(DESTINO, 1L, null, null, true, 2016);

        assertThat(fila(1).getEstado()).isEqualTo("BAJA");
        assertThat(fila(1).requiereRevision()).isFalse();
    }

    @Test
    void elCoordinadorPuedeDesmarcarLaBajaDeUnNoSigueYQuedaConfirmado() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        asistente.marcarContinuidad(DESTINO, 1L, "NO_SIGUE");

        asistente.guardarFila(DESTINO, 1L, null, null, false, 2016);

        assertThat(fila(1).getEstado()).isEqualTo("CONFIRMADO");
        assertThat(fila(1).getContinuidad()).isEqualTo("NO_SIGUE");
    }

    @Test
    void dejarElNoSigueQuitaLaBajaPropuestaYPideRevisarla() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        asistente.marcarContinuidad(DESTINO, 1L, "NO_SIGUE");
        asistente.guardarFila(DESTINO, 1L, null, null, true, 2016);

        asistente.marcarContinuidad(DESTINO, 1L, "SIGUE");

        assertThat(fila(1).getEstado()).isEqualTo("CONFIRMADO");
        assertThat(fila(1).isPendienteRevision()).isTrue();
    }

    @Test
    void laContinuidadValidaElValorLaFilaYElDestino() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        assertThatThrownBy(() -> asistente.marcarContinuidad(DESTINO, 1L, "TAL_VEZ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.marcarContinuidad(DESTINO, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.marcarContinuidad(DESTINO, 99L, "SIGUE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.marcarContinuidad(ACTIVA, 1L, "SIGUE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laRevisionMuestraLaContinuidadYElEstadoDeCadaFila() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        asistente.marcarContinuidad(DESTINO, 1L, "DUDA");

        RevisionTemporadaDTO.Fila f = asistente.revision(DESTINO).pendientes().stream()
                .filter(x -> x.jugadorId() == 1L).findFirst().orElseThrow();

        assertThat(f.continuidad()).isEqualTo("DUDA");
        assertThat(f.estado()).isEqualTo("CONFIRMADO");
        assertThat(f.equipoDefinitivo()).isFalse();
    }

    // ---- altas de prueba ----------------------------------------------------------------------------

    private long altaDePrueba() {
        // Los jugadores de prueba se crean con IDs explícitos: el siguiente generado sigue desde 1000
        jdbc.execute("ALTER TABLE JUGADORES ALTER COLUMN ID RESTART WITH 1000");
        asistente.anadirPrueba(DESTINO, "  Luis ", " Prueba Gil", 2016);
        return jdbc.queryForObject("SELECT ID FROM JUGADORES WHERE NOMBRE = 'Luis'", Long.class);
    }

    @Test
    void unaPruebaEntraEnElBorradorConSuCategoriaPorEdadSinEquipoYSinTocarLaTemporadaActiva() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        long id = altaDePrueba();

        JugadorTemporada f = fila(id);
        assertThat(f.getEstado()).isEqualTo("PRUEBA");
        assertThat(f.getCategoria()).isEqualTo("Alevín");   // 2016: 11 años en 2027-28
        assertThat(f.getEquipoId()).isNull();
        assertThat(f.getDorsal()).isNull();
        assertThat(f.isEquipoDefinitivo()).isFalse();
        assertThat(f.requiereRevision()).isFalse();
        assertThat(jdbc.queryForMap("SELECT NOMBRE, APELLIDOS, ANIO_NACIMIENTO, CATEGORIA FROM JUGADORES WHERE ID = ?", id))
                .containsEntry("NOMBRE", "Luis").containsEntry("APELLIDOS", "Prueba Gil")
                .containsEntry("CATEGORIA", "Alevín");
        // No tiene fila en la temporada activa
        assertThat(jugadorTemporadaDao.obtener(id, ACTIVA)).isNull();
    }

    @Test
    void unaPruebaNoSeColeEnNingunListadoDeLaTemporadaActiva() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        long id = altaDePrueba();

        // Listados de jugadores activos y plantillas
        assertThat(jugadorDao.obtenerActivos()).extracting(j -> j.getId()).doesNotContain(id);
        assertThat(jugadorDao.obtenerTodosPorEquipoId(ALEVIN_A)).extracting(j -> j.getId()).doesNotContain(id);
        assertThat(jugadorDao.obtenerTodosPorCategoria("Alevín")).extracting(j -> j.getId()).doesNotContain(id);
        // Panel de jugadores: ni activos, ni bajas, ni el listado completo
        assertThat(jugadores.obtenerTodos()).extracting(j -> j.getId()).doesNotContain(id);
        assertThat(jugadores.obtenerBajas()).extracting(j -> j.getId()).doesNotContain(id);
        // Ni en el asistente como jugador sin propuesta, ni como baja en el panel
        assertThat(jugadorTemporadaDao.jugadoresActivosSinFila(DESTINO)).extracting(j -> j.getId()).doesNotContain(id);
        RevisionTemporadaDTO.Fila enRevision = asistente.revision(DESTINO).categorias().stream()
                .flatMap(g -> g.filas().stream()).filter(x -> x.jugadorId() == id).findFirst().orElseThrow();
        assertThat(enRevision.jugadorDeBaja()).isFalse();
        // Y a la temporada activa no llega por la sincronización del servicio de jugadores
        assertThat(jugadorTemporadaDao.esDePrueba(id)).isTrue();
        assertThat(filas(ACTIVA)).isZero();
    }

    @Test
    void laPruebaAparecePorCategoriaEnLaRevisionYNoBloquea() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        long id = altaDePrueba();

        RevisionTemporadaDTO revision = asistente.revision(DESTINO);

        assertThat(revision.categorias().stream().filter(g -> g.categoria().equals("Alevín")).findFirst().orElseThrow()
                .filas()).extracting(RevisionTemporadaDTO.Fila::jugadorId).contains(id);
        assertThat(revision.pendientes()).extracting(RevisionTemporadaDTO.Fila::jugadorId).doesNotContain(id);
        assertThat(asistente.resumenAplicacion(DESTINO).bloqueos()).noneMatch(b -> b.contains("sin fila"));
    }

    @Test
    void laPruebaSobreviveAlRegenerarElBorrador() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        long id = altaDePrueba();

        asistente.regenerar(DESTINO);

        assertThat(fila(id)).isNotNull();
        assertThat(fila(id).getEstado()).isEqualTo("PRUEBA");
        assertThat(filas(DESTINO)).isEqualTo(15);   // las 14 de siempre y la prueba, sin duplicarla
    }

    @Test
    void laAltaDePruebaPideNombreApellidosYAnioDeNacimientoConCategoria() {
        assertThatThrownBy(() -> asistente.anadirPrueba(DESTINO, "", "Gil", 2016))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatorios");
        assertThatThrownBy(() -> asistente.anadirPrueba(DESTINO, "Luis", null, 2016))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.anadirPrueba(DESTINO, "Luis", "Gil", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("año de nacimiento");
        assertThatThrownBy(() -> asistente.anadirPrueba(DESTINO, "Luis", "Gil", HOY.getYear() - 2))
                .isInstanceOf(IllegalArgumentException.class);
        // Ninguna categoría formativa cubre 19 años o más
        assertThatThrownBy(() -> asistente.anadirPrueba(DESTINO, "Luis", "Gil", 2000))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ninguna categoría");
        assertThatThrownBy(() -> asistente.anadirPrueba(ACTIVA, "Luis", "Gil", 2016))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM JUGADORES", Integer.class)).isZero();
        assertThat(filas(DESTINO)).isZero();
    }

    @Test
    void siFallaElAltaDeLaFilaDeLaPruebaNoQuedaElJugador() {
        doThrow(new DataIntegrityViolationException("fallo simulado")).when(jugadorTemporadaDao)
                .guardar(org.mockito.ArgumentMatchers.argThat(f -> "PRUEBA".equals(f.getEstado())));

        assertThatThrownBy(() -> asistente.anadirPrueba(DESTINO, "Luis", "Gil", 2016))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM JUGADORES", Integer.class)).isZero();
    }

    @Test
    void alAplicarLaPruebaPasaAJugadorActivoDeSuCategoriaSinEquipo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        long id = altaDePrueba();
        resolverPendientes();

        asistente.aplicar(DESTINO, "2027-28");

        assertThat(jdbc.queryForMap("SELECT CATEGORIA, EQUIPO_ID, FECHA_BAJA FROM JUGADORES WHERE ID = ?", id))
                .containsEntry("CATEGORIA", "Alevín").containsEntry("EQUIPO_ID", null)
                .containsEntry("FECHA_BAJA", null);
        assertThat(jugadorDao.obtenerActivos()).extracting(j -> j.getId()).contains(id);
        assertThat(fila(id).getEstado()).isEqualTo("PRUEBA");
    }

    // ---- revisión -------------------------------------------------------------------------------

    @Test
    void laRevisionAgrupaPorEquipoPorCategoriaYSeparaLasListasDeTrabajo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        jugador(20, 2016, ALEVIN_A, 14);   // sin propuesta

        RevisionTemporadaDTO revision = asistente.revision(DESTINO);

        assertThat(revision.totalFilas()).isEqualTo(14);
        assertThat(revision.destino().getNombre()).isEqualTo("2027-28");
        // Todos los formativos esperan la respuesta de las familias; los adultos no
        assertThat(revision.pendientes()).extracting(RevisionTemporadaDTO.Fila::jugadorId)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 11L, 12L);
        assertThat(revision.sinEquipo()).extracting(RevisionTemporadaDTO.Fila::jugadorId).containsExactly(5L);
        assertThat(revision.bajas()).isEmpty();
        assertThat(revision.sinPropuesta()).extracting(RevisionTemporadaDTO.SinPropuesta::jugadorId)
                .containsExactly(20L);

        // Con equipo: solo los adultos
        assertThat(revision.equipos()).extracting(RevisionTemporadaDTO.GrupoEquipo::equipoNombre)
                .containsExactly("Primer Equipo", "Veteranos");
        assertThat(revision.equipos().get(0).filas()).extracting(RevisionTemporadaDTO.Fila::jugadorId)
                .containsExactlyInAnyOrder(13L, 15L);

        // Sin equipo, por categoría
        assertThat(revision.categorias()).extracting(RevisionTemporadaDTO.GrupoCategoria::categoria)
                .containsExactly("Alevín", "Cadete", "Infantil", "Juvenil");
        RevisionTemporadaDTO.GrupoCategoria cadete = revision.categorias().stream()
                .filter(g -> g.categoria().equals("Cadete")).findFirst().orElseThrow();
        assertThat(cadete.jugadores()).isEqualTo(5);
        assertThat(cadete.filas()).extracting(RevisionTemporadaDTO.Fila::jugadorId)
                .containsExactlyInAnyOrder(3L, 6L, 7L, 8L, 9L);
    }

    @Test
    void avisaSiUnDorsalSeRepiteDentroDeUnEquipo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        asistente.guardarFila(DESTINO, 3L, CADETE_A, 3, false, 2012);
        asistente.guardarFila(DESTINO, 6L, CADETE_A, 3, false, 2012);   // 3 ya lo tiene el jugador 3

        RevisionTemporadaDTO revision = asistente.revision(DESTINO);

        assertThat(revision.avisosDorsal()).containsExactly("Dorsal 3 repetido en Cadete A");
        assertThat(revision.equipos().stream().filter(g -> g.equipoId() == CADETE_A).findFirst().orElseThrow().filas())
                .filteredOn(RevisionTemporadaDTO.Fila::dorsalRepetido)
                .extracting(RevisionTemporadaDTO.Fila::jugadorId).containsExactlyInAnyOrder(3L, 6L);
    }

    @Test
    void guardarUnaFilaLaMarcaComoRevisadaYGuardaElAnioEnElJugador() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        asistente.guardarFila(DESTINO, 3L, CADETE_A, 3, false, 2012);

        assertThat(fila(3).isPendienteRevision()).isFalse();
        assertThat(fila(3).getEquipoId()).isEqualTo(CADETE_A);
        assertThat(fila(3).isEquipoDefinitivo()).isTrue();
        assertThat(jdbc.queryForObject("SELECT ANIO_NACIMIENTO FROM JUGADORES WHERE ID = 3", Integer.class))
                .isEqualTo(2012);
    }

    @Test
    void guardarSinEquipoDejaLaCategoriaPorEdadSinDorsalYRecalculaConElAnioNuevo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        asistente.guardarFila(DESTINO, 3L, null, 9, false, 2012);   // 2012: Cadete en 2027-28 (15 años)

        JugadorTemporada f = fila(3);
        assertThat(f.getEquipoId()).isNull();
        assertThat(f.getCategoria()).isEqualTo("Cadete");
        assertThat(f.getDorsal()).isNull();
        assertThat(f.isEquipoDefinitivo()).isFalse();

        asistente.guardarFila(DESTINO, 3L, null, null, false, 2014);   // 13 años: Infantil
        assertThat(fila(3).getCategoria()).isEqualTo("Infantil");
    }

    @Test
    void guardarUnaFilaValidaLosDatos() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        int anioActual = HOY.getYear();

        // 4 dígitos entre 1940 y (año actual − 3); opcional
        assertThatThrownBy(() -> asistente.guardarFila(DESTINO, 1L, ALEVIN_A, 5, false, anioActual - 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.guardarFila(DESTINO, 1L, ALEVIN_A, 5, false, 1939))
                .isInstanceOf(IllegalArgumentException.class);
        asistente.guardarFila(DESTINO, 1L, ALEVIN_A, 5, false, 1940);
        asistente.guardarFila(DESTINO, 1L, ALEVIN_A, 5, false, null);
        asistente.guardarFila(DESTINO, 1L, ALEVIN_A, 5, false, anioActual - 3);

        assertThatThrownBy(() -> asistente.guardarFila(DESTINO, 1L, 999L, 5, false, 2016))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.guardarFila(DESTINO, 1L, ALEVIN_A, 100, false, 2016))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.guardarFila(DESTINO, 99L, ALEVIN_A, 5, false, 2016))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.guardarFila(ACTIVA, 1L, ALEVIN_A, 5, false, 2016))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unJugadorCreadoDespuesApareceSinPropuestaYSePuedeAnadir() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        jugador(20, 2016, ALEVIN_A, 14);

        assertThat(asistente.revision(DESTINO).sinPropuesta()).hasSize(1);

        asistente.anadirSinPropuesta(DESTINO, 20L);

        assertThat(asistente.revision(DESTINO).sinPropuesta()).isEmpty();
        assertThat(fila(20).getEquipoId()).isNull();
        assertThat(fila(20).getCategoria()).isEqualTo("Alevín");
        assertThatThrownBy(() -> asistente.anadirSinPropuesta(DESTINO, 20L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> asistente.anadirSinPropuesta(DESTINO, 10L))
                .isInstanceOf(IllegalArgumentException.class);   // el 10 está de baja
    }

    // ---- aplicar y activar ------------------------------------------------------------------------

    /**
     * Resuelve todo lo pendiente: la continuidad de cada jugador que sigue, el año de los adultos y las filas que
     * necesitan una decisión (equipo propuesto o el que se indica).
     */
    private void resolverPendientes() {
        for (long id : new long[] { 1L, 2L, 3L, 4L, 6L, 7L, 8L, 9L, 11L, 12L }) {
            asistente.marcarContinuidad(DESTINO, id, "SIGUE");
        }
        asistente.guardarFila(DESTINO, 3L, CADETE_A, 3, false, 2012);
        asistente.guardarFila(DESTINO, 5L, JUVENIL_A, 11, true, 2008);
        asistente.guardarFila(DESTINO, 6L, CADETE_A, 6, false, 2012);
        asistente.guardarFila(DESTINO, 7L, CADETE_D1, 9, false, 2013);
        asistente.guardarFila(DESTINO, 8L, null, null, false, 2013);
        asistente.guardarFila(DESTINO, 11L, JUVENIL_A, 10, false, 2011);
        asistente.guardarFila(DESTINO, 12L, ALEVIN_B, 12, false, 2015);
        // Todos los jugadores activos tienen que tener año de nacimiento
        asistente.guardarFila(DESTINO, 13L, PRIMER_EQUIPO, 1, false, 1990);
        asistente.guardarFila(DESTINO, 14L, VETERANOS, 22, false, 1988);
    }

    @Test
    void conFilasPendientesNoSePuedeAplicarYNadaCambia() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        ResumenAplicacionDTO resumen = asistente.resumenAplicacion(DESTINO);
        assertThat(resumen.puedeAplicar()).isFalse();
        assertThat(resumen.bloqueos()).anyMatch(b -> b.contains("11 fila(s) pendiente(s)"));

        assertThatThrownBy(() -> asistente.aplicar(DESTINO, "2027-28"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("pendiente");

        assertThat(temporadaActiva()).isEqualTo(ACTIVA);
        assertThat(equipoDe(2L)).isEqualTo(ALEVIN_B);
    }

    @Test
    void conLaContinuidadSinResolverNoSePuedeAplicarAunqueLoDemasEsteListo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();
        asistente.marcarContinuidad(DESTINO, 1L, "DUDA");

        ResumenAplicacionDTO resumen = asistente.resumenAplicacion(DESTINO);

        assertThat(resumen.puedeAplicar()).isFalse();
        assertThat(resumen.bloqueos()).containsExactly("1 fila(s) pendiente(s) de revisar (incluye la continuidad sin resolver).");
    }

    @Test
    void cualquierJugadorActivoSinAnioDeNacimientoBloqueaAplicar() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();
        // El 13 es un adulto: la fila no tiene nada pendiente, pero el jugador sigue sin año
        jdbc.update("UPDATE JUGADORES SET ANIO_NACIMIENTO = NULL WHERE ID = 13");

        ResumenAplicacionDTO resumen = asistente.resumenAplicacion(DESTINO);

        assertThat(resumen.puedeAplicar()).isFalse();
        assertThat(resumen.bloqueos()).containsExactly(
                "1 jugador(es) activo(s) sin año de nacimiento: rellénalo en la revisión.");
        assertThatThrownBy(() -> asistente.aplicar(DESTINO, "2027-28")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin año de nacimiento");

        asistente.guardarFila(DESTINO, 13L, PRIMER_EQUIPO, 1, false, 1990);

        assertThat(asistente.resumenAplicacion(DESTINO).puedeAplicar()).isTrue();
    }

    @Test
    void conJugadoresActivosSinFilaNoSePuedeAplicar() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();
        jugador(20, 2016, ALEVIN_A, 14);

        ResumenAplicacionDTO resumen = asistente.resumenAplicacion(DESTINO);

        assertThat(resumen.puedeAplicar()).isFalse();
        assertThat(resumen.bloqueos()).anyMatch(b -> b.contains("sin fila"));
        assertThatThrownBy(() -> asistente.aplicar(DESTINO, "2027-28")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hayQueEscribirElNombreExactoDeLaTemporada() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();

        assertThatThrownBy(() -> asistente.aplicar(DESTINO, "2027/28")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asistente.aplicar(DESTINO, null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(temporadaActiva()).isEqualTo(ACTIVA);
    }

    @Test
    void elResumenCuentaJugadoresPorEquipoYCategoriaCambiosYBajas() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();

        ResumenAplicacionDTO resumen = asistente.resumenAplicacion(DESTINO);

        assertThat(resumen.puedeAplicar()).isTrue();
        assertThat(resumen.temporada()).isEqualTo("2027-28");
        assertThat(resumen.bajas()).isEqualTo(1);                       // jugador 5
        assertThat(resumen.sinEquipo()).isZero();
        Map<String, Integer> porEquipo = resumen.jugadoresPorEquipo();
        assertThat(porEquipo).containsEntry("Cadete A", 2)              // 3, 6
                .containsEntry("Juvenil A", 1)                          // 11
                .containsEntry("Cadete D1", 1)                          // 7
                .containsEntry("Alevín B", 1)                           // 12
                .containsEntry("Primer Equipo", 2)                      // 13, 15
                .containsEntry("Veteranos", 1)                          // 14
                .containsEntry("Alevín (sin equipo)", 1)                // 1
                .containsEntry("Infantil (sin equipo)", 1)              // 2
                .containsEntry("Juvenil (sin equipo)", 1)               // 4
                .containsEntry("Cadete (sin equipo)", 2);               // 8, 9
        // Cambian de equipo (los que tienen equipo y es otro): 6 (Alevín A -> Cadete A), 7 (Infantil D -> Cadete D1),
        // 12 (sin equipo -> Alevín B)
        assertThat(resumen.cambiosDeEquipo()).isEqualTo(3);
    }

    @Test
    void aplicarCopiaLaPlantillaDaDeBajaYActivaLaTemporadaDestino() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();

        asistente.aplicar(DESTINO, "2027-28");

        // El jugador 2 pasa de Alevín B a la categoría Infantil: sin equipo ni dorsal
        assertThat(equipoDe(2L)).isNull();
        assertThat(jdbc.queryForMap("SELECT CATEGORIA, DEPORTE, DORSAL FROM JUGADORES WHERE ID = 2"))
                .containsEntry("CATEGORIA", "Infantil").containsEntry("DEPORTE", "F").containsEntry("DORSAL", null);
        // Los formativos pierden el equipo y el dorsal y se quedan con su categoría por edad
        assertThat(equipoDe(1L)).isNull();
        assertThat(jdbc.queryForMap("SELECT CATEGORIA, DORSAL FROM JUGADORES WHERE ID = 1"))
                .containsEntry("CATEGORIA", "Alevín").containsEntry("DORSAL", null);
        assertThat(equipoDe(9L)).isNull();
        // El coordinador les ha elegido equipo: se copian equipo, categoría, deporte y dorsal
        assertThat(equipoDe(6L)).isEqualTo(CADETE_A);
        assertThat(jdbc.queryForMap("SELECT EQUIPO, CATEGORIA, DEPORTE, DORSAL FROM JUGADORES WHERE ID = 6"))
                .containsEntry("EQUIPO", "Cadete A").containsEntry("CATEGORIA", "Cadete")
                .containsEntry("DEPORTE", "F").containsEntry("DORSAL", java.math.BigDecimal.valueOf(6));
        // Filial, Primer Equipo y Veteranos: mismo equipo y dorsal
        assertThat(equipoDe(15L)).isEqualTo(PRIMER_EQUIPO);
        assertThat(jdbc.queryForObject("SELECT DORSAL FROM JUGADORES WHERE ID = 15", Integer.class)).isEqualTo(8);
        // Baja: FECHA_BAJA = hoy y sin equipo
        assertThat(equipoDe(5L)).isNull();
        assertThat(jdbc.queryForObject("SELECT FECHA_BAJA FROM JUGADORES WHERE ID = 5", Date.class))
                .isEqualTo(Date.valueOf(HOY));
        // Sin equipo pero activo
        assertThat(equipoDe(8L)).isNull();
        assertThat(jdbc.queryForObject("SELECT FECHA_BAJA FROM JUGADORES WHERE ID = 8", Date.class)).isNull();
        // El que ya estaba de baja sigue igual
        assertThat(jdbc.queryForObject("SELECT FECHA_BAJA FROM JUGADORES WHERE ID = 10", Date.class))
                .isEqualTo(Date.valueOf(LocalDate.of(2026, 6, 30)));
        // La temporada destino pasa a ser la única activa
        assertThat(temporadaActiva()).isEqualTo(DESTINO);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TEMPORADAS WHERE ACTIVA = 1", Integer.class)).isEqualTo(1);
        // El borrador queda como historial del destino
        assertThat(filas(DESTINO)).isEqualTo(14);
    }

    @Test
    void siAlgoFallaAMitadDeAplicarNoCambiaNada() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();
        List<Map<String, Object>> antes = jdbc.queryForList("SELECT * FROM JUGADORES ORDER BY ID");
        // Falla al asignar al último jugador con equipo (orden por apellidos = por id): antes ya se han tocado otros
        doThrow(new DataIntegrityViolationException("fallo simulado"))
                .when(jugadorDao).asignarPlantilla(eq(15L), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> asistente.aplicar(DESTINO, "2027-28"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForList("SELECT * FROM JUGADORES ORDER BY ID")).isEqualTo(antes);
        assertThat(temporadaActiva()).isEqualTo(ACTIVA);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TEMPORADAS WHERE ACTIVA = 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void siFallaLaActivacionTampocoQuedanLosJugadoresCambiados() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();
        List<Map<String, Object>> antes = jdbc.queryForList("SELECT * FROM JUGADORES ORDER BY ID");
        // Una restricción que impide activar la temporada destino
        jdbc.execute("ALTER TABLE TEMPORADAS ADD CONSTRAINT CK_NO_ACTIVAR CHECK (ID <> " + DESTINO + " OR ACTIVA = 0)");

        assertThatThrownBy(() -> asistente.aplicar(DESTINO, "2027-28")).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForList("SELECT * FROM JUGADORES ORDER BY ID")).isEqualTo(antes);
        assertThat(temporadaActiva()).isEqualTo(ACTIVA);
    }

    @Test
    void unJugadorDadoDeBajaEnElPanelMientrasSePreparaSeQuedaDeBajaAlAplicar() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();
        jugadores.darDeBaja(1L);   // se da de baja desde el panel con el borrador ya hecho

        asistente.aplicar(DESTINO, "2027-28");

        assertThat(equipoDe(1L)).isNull();
        assertThat(jdbc.queryForObject("SELECT FECHA_BAJA FROM JUGADORES WHERE ID = 1", Date.class)).isNotNull();
        assertThat(temporadaActiva()).isEqualTo(DESTINO);
    }

    // ---- borrar temporadas con plantillas -------------------------------------------------------------

    private int temporadas(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM TEMPORADAS WHERE ID = ?", Integer.class, id);
    }

    @Test
    void unaTemporadaSinPlantillasSeBorraComoSiempre() {
        contexto.getBean(com.mikedev.mutxamelcf.service.TemporadaService.class).eliminar(PASADA, false);

        assertThat(temporadas(PASADA)).isZero();
    }

    @Test
    void laActivaYLaQueYaEstuvoActivaConPlantillasNoSePuedenBorrar() {
        datosDeLasReglas();
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, DORSAL) VALUES (1, ?, 5)", PASADA);
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, DORSAL) VALUES (1, ?, 5)", ACTIVA);
        var temporadaService = contexto.getBean(com.mikedev.mutxamelcf.service.TemporadaService.class);

        assertThatThrownBy(() -> temporadaService.eliminar(ACTIVA, true)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Esta temporada tiene historial de plantillas y no se puede borrar");
        assertThatThrownBy(() -> temporadaService.eliminar(PASADA, true)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Esta temporada tiene historial de plantillas y no se puede borrar");

        assertThat(temporadas(ACTIVA)).isEqualTo(1);
        assertThat(temporadas(PASADA)).isEqualTo(1);
        assertThat(filas(PASADA)).isEqualTo(1);
    }

    @Test
    void unaFuturaConBorradorPideConfirmacionYLuegoBorraElBorradorYLaTemporada() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        var temporadaService = contexto.getBean(com.mikedev.mutxamelcf.service.TemporadaService.class);

        assertThatThrownBy(() -> temporadaService.eliminar(DESTINO, false))
                .isInstanceOf(com.mikedev.mutxamelcf.model.BorradorPlantillasException.class)
                .hasMessage("Se borrará también el borrador de plantillas de 14 jugadores");
        assertThat(temporadas(DESTINO)).isEqualTo(1);
        assertThat(filas(DESTINO)).isEqualTo(14);

        temporadaService.eliminar(DESTINO, true);

        assertThat(temporadas(DESTINO)).isZero();
        assertThat(filas(DESTINO)).isZero();
    }

    @Test
    void siFallaElBorradoDeLaTemporadaElBorradorSeConserva() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        // Una tabla con FK a la temporada: el DELETE de la temporada viola la integridad
        jdbc.execute("CREATE TABLE DATOS_DE_TEMPORADA (ID NUMBER PRIMARY KEY, TEMPORADA_ID NUMBER,"
                + " CONSTRAINT FK_DT_TEMPORADA FOREIGN KEY (TEMPORADA_ID) REFERENCES TEMPORADAS(ID))");
        jdbc.update("INSERT INTO DATOS_DE_TEMPORADA VALUES (1, ?)", DESTINO);
        var temporadaService = contexto.getBean(com.mikedev.mutxamelcf.service.TemporadaService.class);

        assertThatThrownBy(() -> temporadaService.eliminar(DESTINO, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("datos relacionados");

        // Mismo transacción: no se ha borrado el borrador a medias
        assertThat(temporadas(DESTINO)).isEqualTo(1);
        assertThat(filas(DESTINO)).isEqualTo(14);
    }

    // ---- mantener la temporada activa desde el panel ------------------------------------------------

    private JugadorDTO dto(Long id, String nombre, long equipoId, Integer dorsal) {
        JugadorDTO d = new JugadorDTO();
        d.setId(id);
        d.setNombre(nombre);
        d.setApellidos("Inventado");
        d.setEquipoId(equipoId);
        d.setEquipo(jdbc.queryForObject("SELECT NOMBRE FROM EQUIPO WHERE ID = ?", String.class, equipoId));
        d.setCategoria(jdbc.queryForObject("SELECT CATEGORIA FROM EQUIPO WHERE ID = ?", String.class, equipoId));
        d.setDeporte("F");
        d.setDorsal(dorsal);
        return d;
    }

    private long idDe(String nombre) {
        return jdbc.queryForObject("SELECT ID FROM JUGADORES WHERE NOMBRE = ?", Long.class, nombre);
    }

    private Map<String, Object> filaActiva(long jugadorId) {
        return jdbc.queryForMap("SELECT EQUIPO_ID, EQUIPO_NOMBRE, CATEGORIA, DORSAL, ESTADO FROM JUGADOR_TEMPORADA"
                + " WHERE JUGADOR_ID = ? AND TEMPORADA_ID = ?", jugadorId, ACTIVA);
    }

    @Test
    void altaDeJugadorCreaSuFilaDeLaTemporadaActiva() {
        assertThat(jugadores.guardarJugador(dto(null, "Nuevo", CADETE_A, 9))).isTrue();

        long id = idDe("Nuevo");
        assertThat(filaActiva(id)).containsEntry("EQUIPO_NOMBRE", "Cadete A").containsEntry("CATEGORIA", "Cadete");
        assertThat(((Number) filaActiva(id).get("EQUIPO_ID")).longValue()).isEqualTo(CADETE_A);
        assertThat(((Number) filaActiva(id).get("DORSAL")).intValue()).isEqualTo(9);
        assertThat(filaActiva(id)).containsEntry("ESTADO", "CONFIRMADO");
    }

    @Test
    void cambiarEquipoYDorsalActualizaLaMismaFila() {
        jugadores.guardarJugador(dto(null, "Cambia", CADETE_A, 9));
        long id = idDe("Cambia");

        jugadores.guardarJugador(dto(id, "Cambia", JUVENIL_A, 12));

        assertThat(filas(ACTIVA)).isEqualTo(1);
        assertThat(filaActiva(id)).containsEntry("EQUIPO_NOMBRE", "Juvenil A").containsEntry("CATEGORIA", "Juvenil");
        assertThat(((Number) filaActiva(id).get("DORSAL")).intValue()).isEqualTo(12);
        assertThat(equipoDe(id)).isEqualTo(JUVENIL_A);
    }

    @Test
    void darDeBajaYReactivarSeReflejanEnLaTemporadaActiva() {
        jugadores.guardarJugador(dto(null, "Baja", CADETE_A, 9));
        long id = idDe("Baja");

        jugadores.darDeBaja(id);

        assertThat(equipoDe(id)).isNull();
        assertThat(jdbc.queryForObject("SELECT FECHA_BAJA FROM JUGADORES WHERE ID = ?", Date.class, id))
                .isEqualTo(Date.valueOf(HOY));
        assertThat(filaActiva(id)).containsEntry("ESTADO", "BAJA");
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ?", String.class, id))
                .isEqualTo("BAJA");
        // La fila conserva el equipo en el que estaba
        assertThat(filaActiva(id)).containsEntry("EQUIPO_NOMBRE", "Cadete A");

        jugadores.reactivar(id, JUVENIL_A);

        assertThat(equipoDe(id)).isEqualTo(JUVENIL_A);
        assertThat(jdbc.queryForObject("SELECT FECHA_BAJA FROM JUGADORES WHERE ID = ?", Date.class, id)).isNull();
        assertThat(filaActiva(id)).containsEntry("ESTADO", "CONFIRMADO");
        assertThat(jdbc.queryForObject("SELECT ESTADO FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ?", String.class, id))
                .isEqualTo("CONFIRMADO");
        assertThat(filaActiva(id)).containsEntry("EQUIPO_NOMBRE", "Juvenil A");
        assertThat(filas(ACTIVA)).isEqualTo(1);
    }

    @Test
    void siFallaLaFilaDeTemporadaNoSeGuardaElJugador() {
        doThrow(new DataIntegrityViolationException("fallo simulado")).when(jugadorTemporadaDao)
                .guardar(any(JugadorTemporada.class));

        assertThatThrownBy(() -> jugadores.guardarJugador(dto(null, "Fallo", CADETE_A, 9)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Misma transacción: tampoco queda el jugador
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM JUGADORES WHERE NOMBRE = 'Fallo'", Integer.class)).isZero();
    }

    @Test
    void siFallaLaFilaDeTemporadaNoSeDaDeBaja() {
        jugadores.guardarJugador(dto(null, "Sigue", CADETE_A, 9));
        long id = idDe("Sigue");
        doThrow(new DataIntegrityViolationException("fallo simulado")).when(jugadorTemporadaDao)
                .guardar(any(JugadorTemporada.class));

        assertThatThrownBy(() -> jugadores.darDeBaja(id)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(equipoDe(id)).isEqualTo(CADETE_A);
        assertThat(jdbc.queryForObject("SELECT FECHA_BAJA FROM JUGADORES WHERE ID = ?", Date.class, id)).isNull();
    }

    @Test
    void elHistorialDevuelveUnaFilaPorTemporadaLaMasRecientePrimero() {
        jugadores.guardarJugador(dto(null, "Historia", CADETE_A, 9));
        long id = idDe("Historia");
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, EQUIPO_NOMBRE, CATEGORIA, DORSAL)"
                + " VALUES (?, ?, 'Infantil A', 'Infantil', 4)", id, PASADA);

        assertThat(jugadores.obtenerHistorial(id)).extracting(h -> h.temporada() + ":" + h.equipo() + ":" + h.baja())
                .containsExactly("2026-27:Cadete A:false", "2025-26:Infantil A:false");
    }

}
