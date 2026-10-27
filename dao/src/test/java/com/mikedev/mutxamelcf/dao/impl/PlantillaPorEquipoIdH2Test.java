package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.RolAppDao;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorTemporada;

/**
 * Los jugadores se enlazan con su equipo por JUGADORES.EQUIPO_ID (no por nombre) y los dados de baja
 * (FECHA_BAJA) quedan fuera de la plantilla actual. Todo con H2 en modo Oracle y datos inventados.
 *
 * <p>Datos: equipo 1 "Cadete A" (Cadete), equipo 2 "Infantil A" (Infantil). Jugadores:
 * 10 Pablo (Cadete A), 11 Luis (Cadete A), 12 Eva (Infantil A), 13 Baja (de baja, sin equipo, con la copia de texto
 * "Cadete A" que NO debe tenerse en cuenta). Usuarios de app: 100 (jugador 10), 101 (jugador 13), 102 (familiar de 11),
 * 103 (entrenador de Cadete A), 104 (coordinador).
 */
class PlantillaPorEquipoIdH2Test {

    private JdbcTemplate jdbc;
    private EquipoGestionDaoImpl gestion;
    private ComunicacionDaoImpl comunicacion;
    private JugadorDaoImpl jugadorDao;
    private JugadorTemporadaDaoImpl jugadorTemporadaDao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

        jdbc.execute("""
                CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), CATEGORIA VARCHAR2(100),
                    DEPORTE VARCHAR2(10), GRUPO VARCHAR2(10), ORDEN VARCHAR2(5), MODALIDAD VARCHAR2(5))""");
        jdbc.execute("""
                CREATE TABLE TEMPORADAS (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE,
                    FECHA_FIN DATE, ACTIVA NUMBER(1))""");
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crearPlantillas(jdbc);
        jdbc.execute("CREATE TABLE USUARIOS_APP (ID NUMBER PRIMARY KEY, ACTIVO NUMBER(1))");
        jdbc.execute("CREATE TABLE ROLES_APP (ID NUMBER PRIMARY KEY, CODIGO VARCHAR2(30))");
        jdbc.execute("CREATE TABLE USUARIOS_APP_ROLES (USUARIO_APP_ID NUMBER, ROL_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_JUGADORES (USUARIO_APP_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE FAMILIARES (ID NUMBER PRIMARY KEY)");
        jdbc.execute("CREATE TABLE FAMILIARES_JUGADOR (FAMILIAR_ID NUMBER, JUGADOR_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_FAMILIARES (USUARIO_APP_ID NUMBER, FAMILIAR_ID NUMBER)");
        jdbc.execute("CREATE TABLE USUARIOS_APP_CUERPO_TECNICO (USUARIO_APP_ID NUMBER, CUERPO_TECNICO_ID NUMBER)");

        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA, DEPORTE, GRUPO, ORDEN) VALUES (1, 'Cadete A', 'Cadete', 'F', 'A', 'F')");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA, DEPORTE, GRUPO, ORDEN) VALUES (2, 'Infantil A', 'Infantil', 'F', 'A', 'E')");

        jugador(10, "Pablo", "Cadete A", 1L, false);
        jugador(11, "Luis", "Cadete A", 1L, false);
        jugador(12, "Eva", "Infantil A", 2L, false);
        jugador(13, "Baja", "Cadete A", null, true);

        for (int rol = 1; rol <= 5; rol++) {
            jdbc.update("INSERT INTO ROLES_APP VALUES (?, ?)", rol,
                    new String[] { "", "JUGADOR", "FAMILIAR", "ENTRENADOR", "COORDINADOR", "ADMIN_APP" }[rol]);
        }
        for (int usuario = 100; usuario <= 104; usuario++) {
            jdbc.update("INSERT INTO USUARIOS_APP VALUES (?, 1)", usuario);
        }
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (100, 1)");
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (101, 1)");
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (102, 2)");
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (103, 3)");
        jdbc.update("INSERT INTO USUARIOS_APP_ROLES VALUES (104, 4)");
        jdbc.update("INSERT INTO USUARIOS_APP_JUGADORES VALUES (100, 10)");
        jdbc.update("INSERT INTO USUARIOS_APP_JUGADORES VALUES (101, 13)");
        jdbc.update("INSERT INTO FAMILIARES VALUES (50)");
        jdbc.update("INSERT INTO FAMILIARES_JUGADOR VALUES (50, 11)");
        jdbc.update("INSERT INTO USUARIOS_APP_FAMILIARES VALUES (102, 50)");
        jdbc.update("INSERT INTO CUERPO_TECNICO (ID, NOMBRE, APELLIDOS, CATEGORIA, EQUIPO) VALUES (1, 'Ana', 'Ruiz', 'Cadete', 'Cadete A')");
        jdbc.update("INSERT INTO CUERPO_TECNICO_EQUIPO (CUERPO_TECNICO_ID, EQUIPO_ID) VALUES (1, 1)");
        jdbc.update("INSERT INTO USUARIOS_APP_CUERPO_TECNICO VALUES (103, 1)");

        gestion = new EquipoGestionDaoImpl(jdbc);
        comunicacion = new ComunicacionDaoImpl(jdbc, mock(RolAppDao.class));
        jugadorDao = new JugadorDaoImpl(jdbc);
        jugadorTemporadaDao = new JugadorTemporadaDaoImpl(jdbc);
    }

    /** {@code copia} es el texto de JUGADORES.EQUIPO: solo una copia, no se usa para enlazar. */
    private void jugador(long id, String nombre, String copia, Long equipoId, boolean baja) {
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, CATEGORIA, DEPORTE, DORSAL, EQUIPO, EQUIPO_ID, FECHA_BAJA)"
                + " VALUES (?, ?, 'Prueba', 'Cat', 'F', ?, ?, ?, " + (baja ? "DATE '2026-06-30'" : "NULL") + ")",
                id, nombre, id - 9, copia, equipoId);
    }

    private void renombrarCadeteA() {
        // Se renombra el equipo: la copia de texto de los jugadores queda desfasada a propósito.
        jdbc.update("UPDATE EQUIPO SET NOMBRE = 'Cadete Estrella' WHERE ID = 1");
    }

    // ---- EquipoGestionDao -----------------------------------------------------------------------

    @Test
    void losJugadoresDelEquipoSalenPorEquipoIdYSinLasBajas() {
        assertThat(gestion.obtenerJugadoresPorEquipo(1L)).containsExactly(10L, 11L);
        assertThat(gestion.obtenerJugadoresPorEquipo(2L)).containsExactly(12L);
    }

    @Test
    void renombrarUnEquipoNoRompeLaListaDeJugadores() {
        renombrarCadeteA();

        assertThat(gestion.obtenerJugadoresPorEquipo(1L)).containsExactly(10L, 11L);
        assertThat(gestion.perteneceJugadorAEquipo(10L, 1L)).isTrue();
        assertThat(gestion.obtenerEquipoIdDeJugador(10L)).isEqualTo(1L);
    }

    @Test
    void perteneceYEquipoDeJugadorNoCuentanLaBaja() {
        assertThat(gestion.perteneceJugadorAEquipo(13L, 1L)).isFalse();
        assertThat(gestion.obtenerEquipoIdDeJugador(13L)).isNull();
        assertThat(gestion.perteneceJugadorAEquipo(10L, 2L)).isFalse();
    }

    // ---- Comunicaciones ------------------------------------------------------------------------

    @Test
    void losEquiposDeUnUsuarioJugadorOFamiliarSalenPorEquipoId() {
        assertThat(comunicacion.obtenerEquiposDeJugador(100L)).containsExactly(1L);
        assertThat(comunicacion.obtenerEquiposDeFamiliar(102L)).containsExactly(1L);
    }

    @Test
    void unJugadorDeBajaNoTieneEquipoNiRecibeAvisosDelEquipo() {
        assertThat(comunicacion.obtenerEquiposDeJugador(101L)).isEmpty();
        assertThat(comunicacion.obtenerUsuariosDelEquipo(1L)).doesNotContain(101L);
    }

    @Test
    void losUsuariosDelEquipoYDeLaCategoriaSiguenAlEquipoAunqueSeRenombre() {
        renombrarCadeteA();

        // 100 = jugador, 102 = familiar de un jugador; 103 = cuerpo técnico (enlazado por nombre en su propia tabla)
        assertThat(comunicacion.obtenerUsuariosDelEquipo(1L)).contains(100L, 102L).doesNotContain(101L);
        assertThat(comunicacion.obtenerUsuariosDeCategoria("Cadete")).contains(100L, 102L, 103L).doesNotContain(101L);
        assertThat(comunicacion.obtenerUsuariosDeCategoria("Infantil")).doesNotContain(100L, 102L);
    }

    @Test
    void elEntrenadorSoloPuedeEscribirALosUsuariosDeJugadoresActivosDeSuEquipo() {
        // 103 entrena a Cadete A: puede escribir al jugador (100) y a la familia (102), pero no al jugador de baja (101).
        assertThat(comunicacion.obtenerDestinatariosDirectosPermitidos(103L)).contains(100L, 102L, 104L)
                .doesNotContain(101L);
    }

    @Test
    void elJugadorPuedeEscribirALosEntrenadoresDeSuEquipo() {
        assertThat(comunicacion.obtenerDestinatariosDirectosPermitidos(100L)).contains(103L, 104L);
    }

    @Test
    void elFamiliarPuedeEscribirALosEntrenadoresDeSusJugadores() {
        assertThat(comunicacion.obtenerDestinatariosDirectosPermitidos(102L)).contains(103L, 104L);
    }

    // ---- Vínculos de la app --------------------------------------------------------------------

    @Test
    void losJugadoresSinCuentaNoIncluyenBajasYMuestranElEquipoActual() {
        UsuarioAppVinculoDaoImpl vinculos = new UsuarioAppVinculoDaoImpl(jdbc);
        renombrarCadeteA();

        assertThat(vinculos.obtenerJugadoresSinCuenta()).extracting(p -> p.getNombreCompleto() + ":" + p.getEquipo())
                .containsExactlyInAnyOrder("Luis Prueba:Cadete Estrella", "Eva Prueba:Infantil A");
    }

    // ---- JugadorDao ----------------------------------------------------------------------------

    @Test
    void losListadosDistinguenActivosYBajasYElEquipoSaleDeLaTablaEquipo() {
        renombrarCadeteA();

        assertThat(jugadorDao.obtenerActivos()).extracting(Jugador::getId).containsExactlyInAnyOrder(10L, 11L, 12L);
        assertThat(jugadorDao.obtenerBajas()).extracting(Jugador::getId).containsExactly(13L);
        assertThat(jugadorDao.obtenerTodos()).hasSize(4);
        assertThat(jugadorDao.obtenerPorId(10L).getEquipo()).isEqualTo("Cadete Estrella");
        assertThat(jugadorDao.obtenerPorId(13L).getEquipo()).isNull();
        assertThat(jugadorDao.obtenerPorId(13L).estaDeBaja()).isTrue();
        assertThat(jugadorDao.obtenerTodosPorEquipoId(1L)).extracting(Jugador::getId).containsExactly(10L, 11L);
        assertThat(jugadorDao.obtenerTodosPorNombreEquipo("Cadete Estrella")).hasSize(2);
        assertThat(jugadorDao.obtenerTodosPorNombreEquipo("Cadete A")).isEmpty();
        assertThat(jugadorDao.obtenerTodosPorCategoria("Cadete")).extracting(Jugador::getId)
                .containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void darDeBajaPoneLaFechaYQuitaElEquipo() {
        assertThat(jugadorDao.darDeBaja(10L, LocalDate.of(2026, 10, 27))).isTrue();

        Jugador baja = jugadorDao.obtenerPorId(10L);
        assertThat(baja.getFechaBaja()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(baja.getEquipoId()).isNull();
        assertThat(gestion.obtenerJugadoresPorEquipo(1L)).containsExactly(11L);
    }

    @Test
    void reactivarQuitaLaBajaYAsignaElEquipoConSusCopias() {
        assertThat(jugadorDao.reactivar(13L, 2L, "Infantil A", "Infantil", "F")).isTrue();

        Jugador reactivado = jugadorDao.obtenerPorId(13L);
        assertThat(reactivado.estaDeBaja()).isFalse();
        assertThat(reactivado.getEquipoId()).isEqualTo(2L);
        assertThat(reactivado.getCategoria()).isEqualTo("Infantil");
        assertThat(gestion.obtenerJugadoresPorEquipo(2L)).containsExactlyInAnyOrder(12L, 13L);
    }

    @Test
    void elAnioDeNacimientoSeGuardaSoloComoAnio() {
        jugadorDao.actualizarAnioNacimiento(10L, 2011);

        assertThat(jugadorDao.obtenerPorId(10L).getAnioNacimiento()).isEqualTo(2011);
        assertThat(jdbc.queryForObject("SELECT ANIO_NACIMIENTO FROM JUGADORES WHERE ID = 10", Integer.class))
                .isEqualTo(2011);
    }

    @Test
    void unAltaDevuelveElIdGenerado() {
        Jugador nuevo = new Jugador();
        nuevo.setNombre("Nuevo");
        nuevo.setApellidos("Prueba");
        nuevo.setEquipoId(2L);
        nuevo.setEquipo("Infantil A");

        assertThat(jugadorDao.guardarJugador(nuevo)).isTrue();

        assertThat(nuevo.getId()).isNotNull();
        assertThat(jugadorDao.obtenerPorId(nuevo.getId()).getEquipoId()).isEqualTo(2L);
    }

    @Test
    void editarUnJugadorDeBajaNoLeBorraLasCopiasDeSuUltimoEquipo() {
        Jugador baja = jugadorDao.obtenerPorId(13L);
        baja.setEquipo(null);
        baja.setCategoria(null);
        baja.setDeporte(null);
        baja.setEquipoId(null);
        baja.setPosicion("DEL");

        assertThat(jugadorDao.guardarJugador(baja)).isTrue();

        assertThat(jdbc.queryForObject("SELECT EQUIPO FROM JUGADORES WHERE ID = 13", String.class))
                .isEqualTo("Cadete A");
        assertThat(jdbc.queryForObject("SELECT CATEGORIA FROM JUGADORES WHERE ID = 13", String.class)).isEqualTo("Cat");
        assertThat(jugadorDao.obtenerPorId(13L).estaDeBaja()).isTrue();
    }

    // ---- JugadorTemporadaDao --------------------------------------------------------------------

    @Test
    void laFilaDeTemporadaSeCreaSeActualizaYSeListaConLosDatosDelJugador() {
        jdbc.update("INSERT INTO TEMPORADAS VALUES (1, '2026-27', DATE '2026-09-01', DATE '2027-06-30', 1)");
        JugadorTemporada fila = new JugadorTemporada();
        fila.setJugadorId(10L);
        fila.setTemporadaId(1L);
        fila.setEquipoId(1L);
        fila.setEquipoNombre("Cadete A");
        fila.setCategoria("Cadete");
        fila.setDorsal(7);

        jugadorTemporadaDao.guardar(fila);
        fila.setDorsal(8);
        fila.setBaja(true);
        jugadorTemporadaDao.guardar(fila);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM JUGADOR_TEMPORADA", Integer.class)).isEqualTo(1);
        JugadorTemporada leida = jugadorTemporadaDao.obtener(10L, 1L);
        assertThat(leida.getDorsal()).isEqualTo(8);
        assertThat(leida.isBaja()).isTrue();
        assertThat(leida.getJugadorNombre()).isEqualTo("Pablo");
        assertThat(leida.getEquipoActualNombre()).isEqualTo("Cadete A");
        assertThat(leida.getTemporadaNombre()).isEqualTo("2026-27");
        assertThat(jugadorTemporadaDao.listarPorJugador(10L)).hasSize(1);
        assertThat(jugadorTemporadaDao.existenParaEquipo(1L)).isTrue();
        assertThat(jugadorTemporadaDao.existenParaEquipo(2L)).isFalse();
    }

    @Test
    void losJugadoresActivosSinFilaSonLosQueNoTienenFilaEnEsaTemporada() {
        jdbc.update("INSERT INTO TEMPORADAS VALUES (1, '2026-27', DATE '2026-09-01', DATE '2027-06-30', 1)");
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID) VALUES (10, 1)");

        List<Jugador> sinFila = jugadorTemporadaDao.jugadoresActivosSinFila(1L);

        // Ni 10 (ya tiene fila) ni 13 (de baja)
        assertThat(sinFila).extracting(Jugador::getId).containsExactlyInAnyOrder(11L, 12L);
    }

    @Test
    void eliminarUnJugadorBorraPrimeroSuFilaDeTemporada() {
        jdbc.update("INSERT INTO TEMPORADAS VALUES (1, '2026-27', DATE '2026-09-01', DATE '2027-06-30', 1)");
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID) VALUES (10, 1)");

        jugadorDao.eliminar(10L);

        assertThat(jugadorDao.obtenerPorId(10L)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM JUGADOR_TEMPORADA", Integer.class)).isZero();
    }
}
