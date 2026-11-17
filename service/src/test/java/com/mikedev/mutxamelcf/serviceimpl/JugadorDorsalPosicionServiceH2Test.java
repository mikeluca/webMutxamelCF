package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.dao.impl.JugadorDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.JugadorTemporadaDaoImpl;
import com.mikedev.mutxamelcf.model.DorsalRepetidoException;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorEquipoAppDTO;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;

/** Dorsal y posición desde la app y la regla del dorsal del panel, con los DAO reales sobre H2 (modo Oracle). */
class JugadorDorsalPosicionServiceH2Test {

    private static final Long ENTRENADOR_CADETE = 10L;
    private static final Long ENTRENADOR_INFANTIL = 11L;
    private static final Long COORDINADOR = 12L;
    private static final Long ADMIN = 13L;
    private static final Long SIN_ROL = 14L;

    private JdbcTemplate jdbc;
    private JugadorDorsalPosicionServiceImpl service;
    private JugadorServiceImpl jugadorService;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crearPlantillas(jdbc);
        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100), CATEGORIA VARCHAR2(100),"
                + " DEPORTE VARCHAR2(10), ORDEN VARCHAR2(5))");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA) VALUES (1, 'Cadete A', 'Cadete')");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE, CATEGORIA) VALUES (2, 'Infantil A', 'Infantil')");

        jdbc.execute("CREATE TABLE TEMPORADAS (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(50), FECHA_INICIO DATE,"
                + " FECHA_FIN DATE, ACTIVA NUMBER(1))");
        jdbc.update("INSERT INTO TEMPORADAS VALUES (5, '2026-27', DATE '2026-09-01', DATE '2027-06-30', 1)");

        TemporadaDao temporadaDao = mock(TemporadaDao.class);
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(
                new Temporada(5L, "2026-27", Date.valueOf("2026-09-01"), Date.valueOf("2027-06-30"), 1));

        EquipoGestionDao gestion = mock(EquipoGestionDao.class);
        when(gestion.puedeGestionarEquipo(anyLong(), anyLong())).thenReturn(false);
        when(gestion.puedeGestionarEquipo(ENTRENADOR_CADETE, 1L)).thenReturn(true);
        when(gestion.puedeGestionarEquipo(ENTRENADOR_INFANTIL, 2L)).thenReturn(true);
        // Coordinación y administración gestionan cualquier equipo
        for (Long id : new Long[] { COORDINADOR, ADMIN }) {
            when(gestion.puedeGestionarEquipo(id, 1L)).thenReturn(true);
            when(gestion.puedeGestionarEquipo(id, 2L)).thenReturn(true);
            when(gestion.esCoordinadorOAdmin(id)).thenReturn(true);
        }

        JugadorDaoImpl jugadorDao = new JugadorDaoImpl(jdbc);
        JugadorTemporadaDaoImpl jugadorTemporadaDao = new JugadorTemporadaDaoImpl(jdbc);

        jugadorService = new JugadorServiceImpl(jugadorDao, mock(FamiliarJugadorService.class),
                mock(UsuarioAppVinculoDao.class), mock(CuotaJugadorService.class), mock(ConvocatoriaJugadorDao.class),
                mock(EntrenamientoAsistenciaDao.class), null, null, null, null, mock(EquipoDao.class), temporadaDao,
                jugadorTemporadaDao, java.time.Clock.systemDefaultZone());

        service = new JugadorDorsalPosicionServiceImpl(jugadorDao, gestion, temporadaDao, jugadorTemporadaDao,
                jugadorService);
    }

    /** Un jugador activo (o de baja) en el equipo; con su fila de la temporada activa. */
    private long jugador(String nombre, String apellidos, long equipoId, Integer dorsal, String posicion,
            boolean baja) {

        jdbc.update("INSERT INTO JUGADORES (NOMBRE, APELLIDOS, CATEGORIA, DEPORTE, DORSAL, POSICION, EQUIPO, EQUIPO_ID,"
                + " ANIO_NACIMIENTO, FECHA_BAJA, FOTO) VALUES (?, ?, 'Cadete', 'F', ?, ?, 'x', ?, 2011, ?, ?)",
                nombre, apellidos, dorsal, posicion, baja ? null : equipoId,
                baja ? Date.valueOf("2026-10-01") : null, new byte[] { 1, 2, 3 });

        long id = jdbc.queryForObject("SELECT MAX(ID) FROM JUGADORES", Long.class);

        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, EQUIPO_ID, DORSAL, ESTADO) VALUES"
                + " (?, 5, ?, ?, 'CONFIRMADO')", id, equipoId, dorsal);

        return id;
    }

    private Integer dorsal(long id) {
        return jdbc.queryForObject("SELECT DORSAL FROM JUGADORES WHERE ID = ?", Integer.class, id);
    }

    private String posicion(long id) {
        return jdbc.queryForObject("SELECT POSICION FROM JUGADORES WHERE ID = ?", String.class, id);
    }

    // ---- permisos ----------------------------------------------------------------------------

    @Test
    void elEntrenadorEditaSoloLosJugadoresDeSuEquipo() {

        long suyo = jugador("Pablo", "Garcia Lopez", 1, null, null, false);
        long deOtro = jugador("Luis", "Perez", 2, null, null, false);

        JugadorEquipoAppDTO dto = service.actualizar(ENTRENADOR_CADETE, suyo, 7, "DEFENSA");

        assertThat(dto.getId()).isEqualTo(suyo);
        assertThat(dto.getDorsal()).isEqualTo(7);
        assertThat(dto.getPosicion()).isEqualTo("Defensa");
        assertThat(dto.getPosicionCodigo()).isEqualTo("DEFENSA");
        assertThat(dto.isEditable()).isTrue();

        assertThatThrownBy(() -> service.actualizar(ENTRENADOR_CADETE, deOtro, 7, "DEFENSA"))
                .isInstanceOf(SecurityException.class)
                .hasMessageNotContaining("Luis");
        assertThat(dorsal(deOtro)).isNull();
        assertThat(posicion(deOtro)).isNull();
    }

    @Test
    void coordinadorYAdminEditanCualquierEquipo() {

        long jugador = jugador("Pablo", "Garcia", 2, null, null, false);

        service.actualizar(COORDINADOR, jugador, 4, "PORTERO");
        assertThat(dorsal(jugador)).isEqualTo(4);

        service.actualizar(ADMIN, jugador, 5, "EXTREMO");
        assertThat(dorsal(jugador)).isEqualTo(5);
        assertThat(posicion(jugador)).isEqualTo("Extremo");
    }

    @Test
    void sinPermisoNiSesionNoSePuedeEditar() {

        long jugador = jugador("Pablo", "Garcia", 1, null, null, false);

        assertThatThrownBy(() -> service.actualizar(SIN_ROL, jugador, 7, null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.actualizar(null, jugador, 7, null)).isInstanceOf(SecurityException.class);
        assertThat(dorsal(jugador)).isNull();
    }

    @Test
    void unJugadorSinEquipoNoLoEditaUnEntrenadorYAlCoordinadorSeLoDice() {

        jdbc.update("INSERT INTO JUGADORES (NOMBRE, APELLIDOS, CATEGORIA, DEPORTE) VALUES ('Sin', 'Equipo', 'Cadete', 'F')");
        long id = jdbc.queryForObject("SELECT MAX(ID) FROM JUGADORES", Long.class);

        assertThatThrownBy(() -> service.actualizar(ENTRENADOR_CADETE, id, 7, null))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.actualizar(COORDINADOR, id, 7, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jugadorInexistenteODeBajaEsNoEncontrado() {

        long baja = jugador("Pablo", "Garcia", 1, null, null, true);

        assertThatThrownBy(() -> service.actualizar(COORDINADOR, 9999L, 7, null))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.actualizar(COORDINADOR, baja, 7, null))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.actualizar(COORDINADOR, null, 7, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ---- dorsal ------------------------------------------------------------------------------

    @Test
    void elDorsalDebeEstarEntre1Y99() {

        long jugador = jugador("Pablo", "Garcia", 1, 3, null, false);

        for (int fuera : new int[] { 0, -1, 100, 1000 }) {
            assertThatThrownBy(() -> service.actualizar(COORDINADOR, jugador, fuera, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("entre 1 y 99");
        }

        service.actualizar(COORDINADOR, jugador, 1, null);
        assertThat(dorsal(jugador)).isEqualTo(1);
        service.actualizar(COORDINADOR, jugador, 99, null);
        assertThat(dorsal(jugador)).isEqualTo(99);
    }

    @Test
    void unDorsalRepetidoEnElEquipoEsUn409ConElNombreDelOtro() {

        jugador("Pablo", "Garcia Lopez", 1, 7, null, false);
        long otro = jugador("Luis", "Perez", 1, 9, null, false);

        assertThatThrownBy(() -> service.actualizar(ENTRENADOR_CADETE, otro, 7, null))
                .isInstanceOf(DorsalRepetidoException.class)
                .hasMessage("El dorsal 7 ya lo tiene Pablo Garcia");
        assertThat(dorsal(otro)).isEqualTo(9);
    }

    @Test
    void elMismoJugadorPuedeConservarSuDorsal() {

        long jugador = jugador("Pablo", "Garcia", 1, 7, null, false);

        service.actualizar(ENTRENADOR_CADETE, jugador, 7, "DELANTERO");

        assertThat(dorsal(jugador)).isEqualTo(7);
        assertThat(posicion(jugador)).isEqualTo("Delantero");
    }

    @Test
    void unDorsalDeUnJugadorDeBajaODeOtroEquipoSePuedeUsar() {

        jugador("Baja", "Antiguo", 1, 7, null, true);
        jugador("Otro", "Equipo", 2, 8, null, false);
        long nuevo = jugador("Pablo", "Garcia", 1, null, null, false);

        service.actualizar(ENTRENADOR_CADETE, nuevo, 7, null);
        assertThat(dorsal(nuevo)).isEqualTo(7);

        service.actualizar(ENTRENADOR_CADETE, nuevo, 8, null);
        assertThat(dorsal(nuevo)).isEqualTo(8);
    }

    @Test
    void sePuedeQuitarElDorsalYLaPosicion() {

        long jugador = jugador("Pablo", "Garcia", 1, 7, "Defensa", false);

        JugadorEquipoAppDTO dto = service.actualizar(ENTRENADOR_CADETE, jugador, null, null);

        assertThat(dto.getDorsal()).isNull();
        assertThat(dto.getPosicion()).isNull();
        assertThat(dto.getPosicionCodigo()).isNull();
        assertThat(dorsal(jugador)).isNull();
        assertThat(posicion(jugador)).isNull();
    }

    // ---- posición ----------------------------------------------------------------------------

    @Test
    void laPosicionSeGuardaEnCastellanoYSoloSiEsDeLaLista() {

        long jugador = jugador("Pablo", "Garcia", 1, null, null, false);

        String[][] pares = { { "PORTERO", "Portero" }, { "DEFENSA", "Defensa" }, { "CENTROCAMPISTA", "Centrocampista" },
                { "EXTREMO", "Extremo" }, { "DELANTERO", "Delantero" } };

        for (String[] par : pares) {
            service.actualizar(COORDINADOR, jugador, null, par[0]);
            assertThat(posicion(jugador)).isEqualTo(par[1]);
        }

        assertThatThrownBy(() -> service.actualizar(COORDINADOR, jugador, null, "LATERAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PORTERO");
        assertThatThrownBy(() -> service.actualizar(COORDINADOR, jugador, null, ""))
                .isInstanceOf(IllegalArgumentException.class);

        // Lo último que valió se conserva
        assertThat(posicion(jugador)).isEqualTo("Delantero");
    }

    // ---- lo que no se toca y la temporada ----------------------------------------------------

    @Test
    void soloSeTocanDorsalYPosicion() {

        long jugador = jugador("Pablo", "Garcia", 1, null, null, false);

        service.actualizar(ENTRENADOR_CADETE, jugador, 7, "DEFENSA");

        assertThat(jdbc.queryForObject("SELECT NOMBRE || '|' || APELLIDOS || '|' || CATEGORIA || '|' || EQUIPO_ID"
                + " || '|' || ANIO_NACIMIENTO FROM JUGADORES WHERE ID = ?", String.class, jugador))
                .isEqualTo("Pablo|Garcia|Cadete|1|2011");
        assertThat(jdbc.queryForObject("SELECT LENGTH(FOTO) FROM JUGADORES WHERE ID = ?", Integer.class, jugador))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT FECHA_BAJA FROM JUGADORES WHERE ID = ?", Date.class, jugador)).isNull();
    }

    @Test
    void actualizaElDorsalDeLaFilaDeLaTemporadaActiva() {

        long jugador = jugador("Pablo", "Garcia", 1, 3, null, false);

        service.actualizar(ENTRENADOR_CADETE, jugador, 7, "DEFENSA");

        assertThat(jdbc.queryForObject("SELECT DORSAL FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ? AND TEMPORADA_ID = 5",
                Integer.class, jugador)).isEqualTo(7);

        service.actualizar(ENTRENADOR_CADETE, jugador, null, null);

        assertThat(jdbc.queryForObject("SELECT DORSAL FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ? AND TEMPORADA_ID = 5",
                Integer.class, jugador)).isNull();
    }

    @Test
    void sinFilaDeLaTemporadaNoLaCreaYOtraTemporadaNoSeToca() {

        long jugador = jugador("Pablo", "Garcia", 1, 3, null, false);
        jdbc.update("DELETE FROM JUGADOR_TEMPORADA WHERE JUGADOR_ID = ?", jugador);
        jdbc.update("INSERT INTO TEMPORADAS VALUES (4, '2025-26', DATE '2025-09-01', DATE '2026-06-30', 0)");
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, EQUIPO_ID, DORSAL, ESTADO) VALUES"
                + " (?, 4, 1, 3, 'CONFIRMADO')", jugador);

        service.actualizar(ENTRENADOR_CADETE, jugador, 7, null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM JUGADOR_TEMPORADA WHERE TEMPORADA_ID = 5", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT DORSAL FROM JUGADOR_TEMPORADA WHERE TEMPORADA_ID = 4", Integer.class))
                .isEqualTo(3);
    }

    // ---- el panel aplica la misma regla -------------------------------------------------------

    @Test
    void elPanelNoDejaRepetirElDorsalDelEquipo() {

        jugador("Pablo", "Garcia Lopez", 1, 7, null, false);
        long otro = jugador("Luis", "Perez", 1, 9, null, false);

        JugadorDTO dto = jugadorService.obtenerJugadorPorId(otro);
        dto.setDorsal(7);

        assertThatThrownBy(() -> jugadorService.guardarJugador(dto))
                .isInstanceOf(DorsalRepetidoException.class)
                .hasMessage("El dorsal 7 ya lo tiene Pablo Garcia");
        assertThat(dorsal(otro)).isEqualTo(9);

        dto.setDorsal(10);
        assertThat(jugadorService.guardarJugador(dto)).isTrue();
        assertThat(dorsal(otro)).isEqualTo(10);
    }

    @Test
    void elPanelPermiteElDorsalDeUnaBajaOSinDorsal() {

        jugador("Baja", "Antiguo", 1, 7, null, true);
        long activo = jugador("Luis", "Perez", 1, 9, null, false);

        JugadorDTO dto = jugadorService.obtenerJugadorPorId(activo);
        dto.setDorsal(7);
        assertThat(jugadorService.guardarJugador(dto)).isTrue();

        dto.setDorsal(null);
        assertThat(jugadorService.guardarJugador(dto)).isTrue();
        assertThat(dorsal(activo)).isNull();
    }

    // ---- la regla solo se comprueba si el dorsal (o el equipo) cambia -------------------------

    @Test
    void conUnDorsalYaRepetidoPorDatosAntiguosSePuedeCambiarSoloLaPosicion() {

        jugador("Pablo", "Garcia", 1, 7, null, false);
        long repetido = jugador("Luis", "Perez", 1, 7, null, false);

        JugadorEquipoAppDTO dto = service.actualizar(ENTRENADOR_CADETE, repetido, 7, "PORTERO");

        assertThat(dto.getDorsal()).isEqualTo(7);
        assertThat(posicion(repetido)).isEqualTo("Portero");

        // Quitar el dorsal también vale (deja de estar repetido)
        service.actualizar(ENTRENADOR_CADETE, repetido, null, "PORTERO");
        assertThat(dorsal(repetido)).isNull();
    }

    @Test
    void cambiarAlDorsalDeOtroSigueDandoConflictoAunqueYaHayaRepetidos() {

        jugador("Pablo", "Garcia", 1, 7, null, false);
        jugador("Luis", "Perez", 1, 7, null, false);
        long otro = jugador("Ana", "Diaz", 1, 9, null, false);

        assertThatThrownBy(() -> service.actualizar(ENTRENADOR_CADETE, otro, 7, null))
                .isInstanceOf(DorsalRepetidoException.class)
                .hasMessageStartingWith("El dorsal 7 ya lo tiene ");
        assertThat(dorsal(otro)).isEqualTo(9);
    }

    @Test
    void elPanelGuardaOtrosCambiosDeUnJugadorConElDorsalYaRepetido() {

        jugador("Pablo", "Garcia", 1, 7, null, false);
        long repetido = jugador("Luis", "Perez", 1, 7, null, false);

        JugadorDTO dto = jugadorService.obtenerJugadorPorId(repetido);
        dto.setAnioNacimiento(2012);
        dto.setPosicion("Defensa");

        assertThat(jugadorService.guardarJugador(dto)).isTrue();
        assertThat(jdbc.queryForObject("SELECT ANIO_NACIMIENTO FROM JUGADORES WHERE ID = ?", Integer.class, repetido))
                .isEqualTo(2012);
        assertThat(dorsal(repetido)).isEqualTo(7);
        assertThat(posicion(repetido)).isEqualTo("Defensa");
    }

    @Test
    void elPanelSigueBloqueandoUnDorsalNuevoOcupado() {

        jugador("Pablo", "Garcia", 1, 7, null, false);
        jugador("Luis", "Perez", 1, 7, null, false);
        long otro = jugador("Ana", "Diaz", 1, 9, null, false);

        JugadorDTO dto = jugadorService.obtenerJugadorPorId(otro);
        dto.setDorsal(7);

        assertThatThrownBy(() -> jugadorService.guardarJugador(dto)).isInstanceOf(DorsalRepetidoException.class);
    }

    @Test
    void elPanelComprobaElDorsalAlCambiarDeEquipo() {

        jugador("Pablo", "Garcia", 1, 5, null, false);
        long deOtroEquipo = jugador("Luis", "Perez", 2, 5, null, false);

        JugadorDTO dto = jugadorService.obtenerJugadorPorId(deOtroEquipo);
        dto.setEquipoId(1L);
        dto.setEquipo("Cadete A");
        dto.setCategoria("Cadete");

        assertThatThrownBy(() -> jugadorService.guardarJugador(dto))
                .isInstanceOf(DorsalRepetidoException.class)
                .hasMessage("El dorsal 5 ya lo tiene Pablo Garcia");
        assertThat(jdbc.queryForObject("SELECT EQUIPO_ID FROM JUGADORES WHERE ID = ?", Long.class, deOtroEquipo))
                .isEqualTo(2L);

        // Con el dorsal libre en el equipo nuevo sí puede cambiar
        dto.setDorsal(6);
        assertThat(jugadorService.guardarJugador(dto)).isTrue();
        assertThat(jdbc.queryForObject("SELECT EQUIPO_ID FROM JUGADORES WHERE ID = ?", Long.class, deOtroEquipo))
                .isEqualTo(1L);
    }

    @Test
    void elNombreCortoUsaElPrimerApellidoAunqueHayaVariosEspacios() {

        jugador("Pablo", "Garcia   Lopez", 1, 7, null, false);
        long otro = jugador("Luis", "Perez", 1, 9, null, false);

        assertThatThrownBy(() -> service.actualizar(ENTRENADOR_CADETE, otro, 7, null))
                .hasMessage("El dorsal 7 ya lo tiene Pablo Garcia");

        com.mikedev.mutxamelcf.model.Jugador tabulado = new com.mikedev.mutxamelcf.model.Jugador();
        tabulado.setNombre(" Ana ");
        tabulado.setApellidos("De\t la   Cruz");

        assertThat(ReglaDorsal.nombreCorto(tabulado)).isEqualTo("Ana De");
    }
}
