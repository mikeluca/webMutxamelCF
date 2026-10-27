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
    void laCategoriaPorEdadCoincideConLaDelEquipoSeQuedaEnElMismoEquipoYDorsal() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(1);

        assertThat(f.getEquipoId()).isEqualTo(ALEVIN_A);
        assertThat(f.getEquipoNombre()).isEqualTo("Alevín A");
        assertThat(f.getCategoria()).isEqualTo("Alevín");
        assertThat(f.getDorsal()).isEqualTo(5);
        assertThat(f.isPendienteRevision()).isFalse();
        assertThat(f.isBaja()).isFalse();
        assertThat(f.getNota()).isNull();
    }

    @Test
    void siCambiaDeCategoriaVaAlEquipoEquivalenteDelMismoGrupoSinDorsal() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada paso = fila(2);   // Alevín B -> Infantil B
        assertThat(paso.getEquipoId()).isEqualTo(INFANTIL_B);
        assertThat(paso.getCategoria()).isEqualTo("Infantil");
        assertThat(paso.getDorsal()).isNull();
        assertThat(paso.isPendienteRevision()).isFalse();

        JugadorTemporada otro = fila(9);   // Infantil A -> Cadete A
        assertThat(otro.getEquipoId()).isEqualTo(CADETE_A);
        assertThat(otro.getDorsal()).isNull();
        assertThat(otro.isPendienteRevision()).isFalse();
    }

    @Test
    void sinEquipoEquivalenteNiUnoSoloQuedaSinEquipoYPendiente() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada ninguno = fila(8);   // Infantil C -> Cadete C no existe
        assertThat(ninguno.getEquipoId()).isNull();
        assertThat(ninguno.isPendienteRevision()).isTrue();
        assertThat(ninguno.getNota()).isEqualTo("Sin equipo equivalente en Cadete");
        assertThat(ninguno.getDorsal()).isNull();

        JugadorTemporada ambiguo = fila(7);   // Infantil D -> dos Cadete D
        assertThat(ambiguo.getEquipoId()).isNull();
        assertThat(ambiguo.isPendienteRevision()).isTrue();
        assertThat(ambiguo.getNota()).isEqualTo("Sin equipo equivalente en Cadete");
    }

    @Test
    void sinAnioDeNacimientoSeQuedaIgualPeroPendiente() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(3);

        assertThat(f.getEquipoId()).isEqualTo(CADETE_A);
        assertThat(f.getDorsal()).isEqualTo(3);
        assertThat(f.isPendienteRevision()).isTrue();
        assertThat(f.getNota()).isEqualTo("Sin año de nacimiento");
    }

    @Test
    void unJuvenilDeUltimoAnioQuedaSinEquipoPendienteYSinProponerLaBaja() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(5);   // 19 años en 2027-28: supera la edad máxima (18)

        assertThat(f.getEquipoId()).isNull();
        assertThat(f.getDorsal()).isNull();
        assertThat(f.isBaja()).isFalse();
        assertThat(f.isPendienteRevision()).isTrue();
        assertThat(f.getNota()).isEqualTo("Termina Juvenil: ¿Filial, Primer Equipo o baja?");
    }

    @Test
    void losJugadoresDeCategoriasSinReglaDeEdadSeQuedanSinRevision() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        for (long id : new long[] { 13L, 14L, 15L }) {   // Primer Equipo y Veteranos, con y sin año
            JugadorTemporada f = fila(id);
            assertThat(f.isPendienteRevision()).as("pendiente " + id).isFalse();
            assertThat(f.isBaja()).as("baja " + id).isFalse();
            assertThat(f.getNota()).as("nota " + id).isNull();
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
        assertThat(fila(5).isPendienteRevision()).isFalse();
    }

    @Test
    void siJuegaPorAbajoSeProponeSegunSuEdadYPendiente() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(6);   // le toca Cadete, juega en Alevín A -> Cadete A

        assertThat(f.getEquipoId()).isEqualTo(CADETE_A);
        assertThat(f.getDorsal()).isNull();
        assertThat(f.isPendienteRevision()).isTrue();
        assertThat(f.getNota()).isEqualTo("Jugaba fuera de su categoría (Alevín)");
    }

    @Test
    void siJuegaPorArribaYAhoraLeTocaSuCategoriaSeQuedaPeroPendiente() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(11);   // este año Cadete (juega en Juvenil A); el que viene le toca Juvenil

        assertThat(f.getEquipoId()).isEqualTo(JUVENIL_A);
        assertThat(f.getDorsal()).isEqualTo(10);
        assertThat(f.isPendienteRevision()).isTrue();
        assertThat(f.getNota()).isEqualTo("Jugaba fuera de su categoría (Juvenil)");
    }

    @Test
    void unJugadorActivoSinEquipoQuedaPendiente() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        JugadorTemporada f = fila(12);

        assertThat(f.getEquipoId()).isNull();
        assertThat(f.isPendienteRevision()).isTrue();
        assertThat(f.getNota()).isEqualTo("Sin equipo actual");
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
        assertThat(fila(3).getDorsal()).isEqualTo(3);
        assertThat(fila(3).isPendienteRevision()).isTrue();
    }

    // ---- revisión -------------------------------------------------------------------------------

    @Test
    void laRevisionAgrupaPorEquipoYSeparaLasListasDeTrabajo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        jugador(20, 2016, ALEVIN_A, 14);   // sin propuesta

        RevisionTemporadaDTO revision = asistente.revision(DESTINO);

        assertThat(revision.totalFilas()).isEqualTo(14);
        assertThat(revision.destino().getNombre()).isEqualTo("2027-28");
        assertThat(revision.pendientes()).extracting(RevisionTemporadaDTO.Fila::jugadorId)
                .containsExactlyInAnyOrder(3L, 5L, 6L, 7L, 8L, 11L, 12L);
        assertThat(revision.sinEquipo()).extracting(RevisionTemporadaDTO.Fila::jugadorId)
                .containsExactlyInAnyOrder(5L, 7L, 8L, 12L);
        assertThat(revision.bajas()).isEmpty();   // ya no se proponen bajas por edad
        assertThat(revision.sinPropuesta()).extracting(RevisionTemporadaDTO.SinPropuesta::jugadorId)
                .containsExactly(20L);

        RevisionTemporadaDTO.GrupoEquipo cadeteA = revision.equipos().stream()
                .filter(g -> g.equipoId() == CADETE_A).findFirst().orElseThrow();
        // 3 (sin año), 6 (juega por abajo) y 9 (Infantil A -> Cadete A); no cuenta la baja propuesta
        assertThat(cadeteA.jugadores()).isEqualTo(3);
        assertThat(cadeteA.filas()).extracting(RevisionTemporadaDTO.Fila::jugadorId)
                .containsExactlyInAnyOrder(3L, 6L, 9L);
    }

    @Test
    void avisaSiUnDorsalSeRepiteDentroDeUnEquipo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
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
        assertThat(jdbc.queryForObject("SELECT ANIO_NACIMIENTO FROM JUGADORES WHERE ID = 3", Integer.class))
                .isEqualTo(2012);
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
        assertThat(fila(20).getEquipoId()).isEqualTo(ALEVIN_A);
        assertThatThrownBy(() -> asistente.anadirSinPropuesta(DESTINO, 20L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> asistente.anadirSinPropuesta(DESTINO, 10L))
                .isInstanceOf(IllegalArgumentException.class);   // el 10 está de baja
    }

    // ---- aplicar y activar ------------------------------------------------------------------------

    /** Resuelve las filas pendientes dejándolas como están (equipo propuesto o el que se indica). */
    private void resolverPendientes() {
        asistente.guardarFila(DESTINO, 3L, CADETE_A, 3, false, 2012);
        asistente.guardarFila(DESTINO, 5L, JUVENIL_A, 11, true, 2008);
        asistente.guardarFila(DESTINO, 6L, CADETE_A, 6, false, 2012);
        asistente.guardarFila(DESTINO, 7L, CADETE_D1, 9, false, 2013);
        asistente.guardarFila(DESTINO, 8L, null, null, false, 2013);
        asistente.guardarFila(DESTINO, 11L, JUVENIL_A, 10, false, 2011);
        asistente.guardarFila(DESTINO, 12L, ALEVIN_B, 12, false, 2015);
    }

    @Test
    void conFilasPendientesNoSePuedeAplicarYNadaCambia() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);

        ResumenAplicacionDTO resumen = asistente.resumenAplicacion(DESTINO);
        assertThat(resumen.puedeAplicar()).isFalse();
        assertThat(resumen.bloqueos()).anyMatch(b -> b.contains("7 fila(s) pendiente(s)"));

        assertThatThrownBy(() -> asistente.aplicar(DESTINO, "2027-28"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("pendiente");

        assertThat(temporadaActiva()).isEqualTo(ACTIVA);
        assertThat(equipoDe(2L)).isEqualTo(ALEVIN_B);
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
    void elResumenCuentaJugadoresPorEquipoCambiosBajasYSinEquipo() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();

        ResumenAplicacionDTO resumen = asistente.resumenAplicacion(DESTINO);

        assertThat(resumen.puedeAplicar()).isTrue();
        assertThat(resumen.temporada()).isEqualTo("2027-28");
        assertThat(resumen.bajas()).isEqualTo(1);                       // jugador 5
        assertThat(resumen.sinEquipo()).isEqualTo(1);                   // jugador 8
        Map<String, Integer> porEquipo = resumen.jugadoresPorEquipo();
        assertThat(porEquipo).containsEntry("Alevín A", 1)              // 1
                .containsEntry("Infantil B", 1)                         // 2
                .containsEntry("Cadete A", 3)                           // 3, 6, 9
                .containsEntry("Juvenil A", 2)                          // 4, 11
                .containsEntry("Cadete D1", 1)                          // 7
                .containsEntry("Alevín B", 1);                          // 12
        // Cambian de equipo: 2 (Alevín B -> Infantil B), 6 (Alevín A -> Cadete A), 7 (Infantil D -> Cadete D1),
        // 9 (Infantil A -> Cadete A), 12 (sin equipo -> Alevín B)
        assertThat(resumen.cambiosDeEquipo()).isEqualTo(5);
    }

    @Test
    void aplicarCopiaLaPlantillaDaDeBajaYActivaLaTemporadaDestino() {
        datosDeLasReglas();
        asistente.generarPropuesta(DESTINO);
        resolverPendientes();

        asistente.aplicar(DESTINO, "2027-28");

        // El jugador 2 cambia de Alevín B a Infantil B sin dorsal; se copian equipo, categoría, deporte y dorsal
        assertThat(equipoDe(2L)).isEqualTo(INFANTIL_B);
        assertThat(jdbc.queryForMap("SELECT EQUIPO, CATEGORIA, DEPORTE, DORSAL FROM JUGADORES WHERE ID = 2"))
                .containsEntry("EQUIPO", "Infantil B").containsEntry("CATEGORIA", "Infantil")
                .containsEntry("DEPORTE", "F").containsEntry("DORSAL", null);
        // Se queda: mismo equipo y dorsal
        assertThat(jdbc.queryForMap("SELECT EQUIPO_ID, DORSAL FROM JUGADORES WHERE ID = 1"))
                .containsEntry("DORSAL", java.math.BigDecimal.valueOf(5));
        assertThat(equipoDe(1L)).isEqualTo(ALEVIN_A);
        assertThat(equipoDe(9L)).isEqualTo(CADETE_A);
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
        // Falla al asignar al último jugador (orden por apellidos = por id): antes ya se han tocado otros
        doThrow(new DataIntegrityViolationException("fallo simulado"))
                .when(jugadorDao).asignarPlantilla(eq(12L), any(), any(), any(), any(), any());

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
        return jdbc.queryForMap("SELECT EQUIPO_ID, EQUIPO_NOMBRE, CATEGORIA, DORSAL, BAJA FROM JUGADOR_TEMPORADA"
                + " WHERE JUGADOR_ID = ? AND TEMPORADA_ID = ?", jugadorId, ACTIVA);
    }

    @Test
    void altaDeJugadorCreaSuFilaDeLaTemporadaActiva() {
        assertThat(jugadores.guardarJugador(dto(null, "Nuevo", CADETE_A, 9))).isTrue();

        long id = idDe("Nuevo");
        assertThat(filaActiva(id)).containsEntry("EQUIPO_NOMBRE", "Cadete A").containsEntry("CATEGORIA", "Cadete");
        assertThat(((Number) filaActiva(id).get("EQUIPO_ID")).longValue()).isEqualTo(CADETE_A);
        assertThat(((Number) filaActiva(id).get("DORSAL")).intValue()).isEqualTo(9);
        assertThat(((Number) filaActiva(id).get("BAJA")).intValue()).isZero();
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
        assertThat(((Number) filaActiva(id).get("BAJA")).intValue()).isEqualTo(1);
        // La fila conserva el equipo en el que estaba
        assertThat(filaActiva(id)).containsEntry("EQUIPO_NOMBRE", "Cadete A");

        jugadores.reactivar(id, JUVENIL_A);

        assertThat(equipoDe(id)).isEqualTo(JUVENIL_A);
        assertThat(jdbc.queryForObject("SELECT FECHA_BAJA FROM JUGADORES WHERE ID = ?", Date.class, id)).isNull();
        assertThat(((Number) filaActiva(id).get("BAJA")).intValue()).isZero();
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
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, EQUIPO_NOMBRE, CATEGORIA, DORSAL, BAJA)"
                + " VALUES (?, ?, 'Infantil A', 'Infantil', 4, 0)", id, PASADA);

        assertThat(jugadores.obtenerHistorial(id)).extracting(h -> h.temporada() + ":" + h.equipo() + ":" + h.baja())
                .containsExactly("2026-27:Cadete A:false", "2025-26:Infantil A:false");
    }

}
