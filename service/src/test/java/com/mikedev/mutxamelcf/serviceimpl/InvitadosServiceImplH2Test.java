package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.CategoriaEdadDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.dao.PartidoDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppDao;
import com.mikedev.mutxamelcf.dao.impl.ConvocatoriaDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.ConvocatoriaJugadorDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.InvitacionDaoImpl;
import com.mikedev.mutxamelcf.dao.impl.JustificacionFaltaPartidoDaoImpl;
import com.mikedev.mutxamelcf.model.CategoriaEdad;
import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ConflictoInvitacionException;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugadorResponse;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EstadoInvitacion;
import com.mikedev.mutxamelcf.model.InvitacionPendienteResponse;
import com.mikedev.mutxamelcf.model.InvitacionResponse;
import com.mikedev.mutxamelcf.model.JugadorInvitableResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.model.OpcionesPushComunicacion;
import com.mikedev.mutxamelcf.model.Partido;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.JustificacionFaltaPartidoService;

/**
 * El flujo de los jugadores invitados (docs/convocatoria-invitados.md) con los DAO reales de invitaciones,
 * convocatorias y justificaciones sobre H2; el resto de dependencias, simuladas.
 *
 * <p>Equipos: 1 Cadete A (destino), 2 Cadete B, 3 Infantil A, 4 Cadete Fem, 5 Veteranos, 6 Cadete C. Partidos: 10 =
 * Cadete A el sábado 14/11/2026 (convocatoria 100), 11 = Cadete B ese mismo sábado (convocatoria 101), 12 = Infantil
 * A el domingo 15/11, 13 = Cadete C el sábado 14/11 (sin convocatoria). Jugadores: 1 Ana y 2 Biel y 4 Dani y 5 Eva de
 * Cadete B, 3 Carla de Infantil A, 6 Fran de Cadete A, 7 Gema de Cadete Fem. Usuarios: 3 coordinador, 4 admin, 7
 * entrenador de Cadete A, 8 entrenador de Cadete B, 9 entrenador de Infantil A; 20 es el propio Ana y 21 su familia.
 */
class InvitadosServiceImplH2Test {

    private static final LocalDate SABADO = LocalDate.of(2026, 11, 14);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 11, 15);
    private static final Long COORDINADOR = 3L;
    private static final Long ADMIN = 4L;
    private static final Long ENTRENADOR_DESTINO = 7L;
    private static final Long ENTRENADOR_ORIGEN = 8L;
    private static final Long ENTRENADOR_INFANTIL = 9L;
    private static final Long ANA = 1L;
    private static final Long BIEL = 2L;
    private static final Long CARLA = 3L;
    private static final Long DANI = 4L;
    private static final Long EVA = 5L;
    private static final Long FRAN = 6L;
    private static final Long GEMA = 7L;

    /** Un aviso enviado: título, destinatarios, remitente, texto, texto del push y data del push. */
    private record Aviso(String titulo, List<Long> destinos, Long remitente, String contenido, String push,
            Map<String, String> data) {
        String tipo() {
            return data.get("tipo");
        }
    }

    private JdbcTemplate jdbc;
    private ConvocatoriaJugadorDaoImpl convocatoriaJugadorDao;
    private InvitadosServiceImpl service;
    private EquipoGestionDao gestion;
    private JustificacionFaltaPartidoService justificacionService;
    private final List<Aviso> avisos = new ArrayList<>();
    private final Map<Long, Partido> partidos = new HashMap<>();
    private final Map<Long, Long> equipoDelJugador = new HashMap<>();
    private LocalDateTime ahora = LocalDateTime.of(2026, 11, 12, 10, 0);

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100))");
        jdbc.execute("""
                CREATE TABLE PARTIDOS (
                    ID NUMBER PRIMARY KEY, EQUIPO_ID NUMBER, DIA DATE, HORA VARCHAR2(10), RIVAL VARCHAR2(150),
                    CAMPO VARCHAR2(150), CANCELADO NUMBER(1) DEFAULT 0)""");
        jdbc.execute("""
                CREATE TABLE CONVOCATORIAS (
                    ID NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    EQUIPO_ID NUMBER NOT NULL,
                    PARTIDO_ID NUMBER NOT NULL,
                    HORA_CONVOCATORIA VARCHAR2(10),
                    LUGAR_CONVOCATORIA VARCHAR2(150),
                    USUARIO_ENTRENADOR_ID NUMBER,
                    FECHA_CREACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP)""");
        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crear(jdbc);
        EsquemaH2.crearPlantillas(jdbc);

        String[] nombresEquipo = { "Cadete A", "Cadete B", "Infantil A", "Cadete Fem", "Veteranos", "Cadete C" };

        for (int i = 0; i < nombresEquipo.length; i++) {
            jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (?, ?)", i + 1, nombresEquipo[i]);
        }

        partido(10, 1, SABADO, "11:00", "Pego");
        partido(11, 2, SABADO, "09:30", "Denia");
        partido(12, 3, DOMINGO, "10:00", "Elche");
        partido(13, 6, SABADO, "12:00", "Javea");

        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, HORA_CONVOCATORIA, LUGAR_CONVOCATORIA,"
                + " USUARIO_ENTRENADOR_ID) VALUES (100, 1, 10, '10:00', 'Vestuarios', 7)");
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, HORA_CONVOCATORIA, LUGAR_CONVOCATORIA,"
                + " USUARIO_ENTRENADOR_ID) VALUES (101, 2, 11, '08:30', 'Vestuarios', 8)");

        jugador(ANA, "Ana", 2);
        jugador(BIEL, "Biel", 2);
        jugador(CARLA, "Carla", 3);
        jugador(DANI, "Dani", 2);
        jugador(EVA, "Eva", 2);
        jugador(FRAN, "Fran", 1);
        jugador(GEMA, "Gema", 4);

        convocatoriaJugadorDao = new ConvocatoriaJugadorDaoImpl(jdbc);

        gestion = mock(EquipoGestionDao.class);
        when(gestion.obtenerNombreJugador(anyLong())).thenAnswer(i -> nombreJugador(i.getArgument(0)));
        when(gestion.obtenerNombreEquipo(anyLong())).thenAnswer(
                i -> nombresEquipo[((Long) i.getArgument(0)).intValue() - 1]);
        when(gestion.perteneceJugadorAEquipo(anyLong(), anyLong())).thenAnswer(
                i -> i.getArgument(1).equals(equipoDelJugador.get((Long) i.getArgument(0))));
        when(gestion.obtenerEquipoIdDeJugador(anyLong())).thenAnswer(i -> equipoDelJugador.get((Long) i.getArgument(0)));
        when(gestion.esCoordinadorOAdmin(anyLong())).thenAnswer(
                i -> COORDINADOR.equals(i.getArgument(0)) || ADMIN.equals(i.getArgument(0)));
        when(gestion.puedeGestionarEquipo(anyLong(), anyLong())).thenAnswer(i -> {
            Long usuario = i.getArgument(0);
            Long equipo = i.getArgument(1);
            return COORDINADOR.equals(usuario) || ADMIN.equals(usuario)
                    || (ENTRENADOR_DESTINO.equals(usuario) && equipo == 1L)
                    || (ENTRENADOR_ORIGEN.equals(usuario) && (equipo == 2L || equipo == 6L))
                    || (ENTRENADOR_INFANTIL.equals(usuario) && equipo == 3L);
        });
        when(gestion.obtenerCoordinadores()).thenReturn(List.of(COORDINADOR));
        when(gestion.obtenerAdministradoresApp()).thenReturn(List.of(ADMIN));
        when(gestion.obtenerEntrenadoresPorEquipo(1L)).thenReturn(List.of(ENTRENADOR_DESTINO));
        when(gestion.obtenerEntrenadoresPorEquipo(2L)).thenReturn(List.of(ENTRENADOR_ORIGEN));
        when(gestion.obtenerEntrenadoresPorEquipo(3L)).thenReturn(List.of(ENTRENADOR_INFANTIL));
        when(gestion.obtenerEntrenadoresPorEquipo(6L)).thenReturn(List.of());
        when(gestion.obtenerUsuariosPorJugador(ANA)).thenReturn(List.of(20L));
        when(gestion.obtenerUsuariosFamiliaresPorJugador(ANA)).thenReturn(List.of(21L));
        when(gestion.obtenerUsuariosPorJugador(CARLA)).thenReturn(List.of());
        when(gestion.obtenerUsuariosFamiliaresPorJugador(CARLA)).thenReturn(List.of(31L));

        PartidoDao partidoDao = mock(PartidoDao.class);
        when(partidoDao.obtenerPorId(anyLong())).thenAnswer(i -> partidos.get((Long) i.getArgument(0)));

        EquipoDao equipoDao = mock(EquipoDao.class);
        List<Equipo> activos = List.of(
                equipo(1, "Cadete", "A", "Cadete A", null),
                equipo(2, "Cadete", "B", "Cadete B", null),
                equipo(3, "Infantil", "A", "Infantil A", null),
                equipo(4, "Cadete", "Fem", "Cadete Fem", null),
                equipo(5, "Veteranos", "A", "Veteranos", null),
                equipo(6, "Cadete", "C", "Cadete C", null),
                equipo(7, "Cadete", "D", "Cadete (Pretemporada)", "PRETEMPORADA"));
        when(equipoDao.obtenerActivos()).thenReturn(activos);

        CategoriaEdadDao categoriaEdadDao = mock(CategoriaEdadDao.class);
        when(categoriaEdadDao.obtenerTodas()).thenReturn(List.of(
                new CategoriaEdad("Infantil", 13, 14, 5),
                new CategoriaEdad("Cadete", 15, 16, 6),
                new CategoriaEdad("Juvenil", 17, 18, 7)));

        TemporadaDao temporadaDao = mock(TemporadaDao.class);
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(new Temporada(5L, "2026/27", null, null, 1));

        UsuarioAppDao usuarioAppDao = mock(UsuarioAppDao.class);

        ComunicacionService comunicacionService = mock(ComunicacionService.class);
        when(comunicacionService.crearPrivada(any(Comunicacion.class), any(), anyLong(),
                any(OpcionesPushComunicacion.class))).thenAnswer(i -> {
                    Comunicacion c = i.getArgument(0);
                    OpcionesPushComunicacion opciones = i.getArgument(3);
                    avisos.add(new Aviso(c.getTitulo(), i.getArgument(1), i.getArgument(2), c.getContenido(),
                            opciones.contenidoPush(), opciones.datos()));
                    return c;
                });

        justificacionService = mock(JustificacionFaltaPartidoService.class);

        service = new InvitadosServiceImpl(
                convocatoriaJugadorDao,
                new InvitacionDaoImpl(jdbc),
                new ConvocatoriaDaoImpl(jdbc),
                partidoDao,
                equipoDao,
                categoriaEdadDao,
                temporadaDao,
                gestion,
                new JustificacionFaltaPartidoDaoImpl(jdbc),
                usuarioAppDao,
                comunicacionService,
                justificacionService,
                new ReglaInvitados(),
                new com.mikedev.mutxamelcf.dao.impl.EquipacionDaoImpl(jdbc),
                Clock.fixed(ahora.atZone(ZoneId.of("Europe/Madrid")).toInstant(), ZoneId.of("Europe/Madrid")));
    }

    // ------------------------------------------------------------------ montaje

    private void partido(long id, long equipo, LocalDate dia, String hora, String rival) {
        jdbc.update("INSERT INTO PARTIDOS (ID, EQUIPO_ID, DIA, HORA, RIVAL, CAMPO) VALUES (?, ?, ?, ?, ?, 'Campo')",
                id, equipo, Date.valueOf(dia), hora, rival);

        Partido partido = new Partido();
        partido.setId(id);
        partido.setEquipoId(equipo);
        partido.setDia(Date.valueOf(dia));
        partido.setHora(hora);
        partido.setRival(rival);
        partidos.put(id, partido);
    }

    private void jugador(long id, String nombre, long equipo) {
        jdbc.update("INSERT INTO JUGADORES (ID, NOMBRE, APELLIDOS, DORSAL, EQUIPO_ID) VALUES (?, ?, 'Prueba', ?, ?)",
                id, nombre, id, equipo);
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, EQUIPO_ID, ESTADO, EQUIPO_DEFINITIVO)"
                + " VALUES (?, 5, ?, 'CONFIRMADO', 1)", id, equipo);
        equipoDelJugador.put(id, equipo);
    }

    private String nombreJugador(Long id) {
        return jdbc.queryForObject("SELECT NOMBRE FROM JUGADORES WHERE ID = ?", String.class, id);
    }

    private static Equipo equipo(long id, String categoria, String grupo, String nombre, String tipo) {
        Equipo equipo = new Equipo(id, categoria, grupo, String.valueOf(id), nombre, "F");
        equipo.setTipoTemporada(tipo == null ? "NORMAL" : tipo);
        return equipo;
    }

    private Convocatoria convocatoria(long id) {
        return new ConvocatoriaDaoImpl(jdbc).obtenerPorId(id);
    }

    private void convocarEnOrigen(long jugador) {
        convocatoriaJugadorDao.guardar(new ConvocatoriaJugador(null, 101L, jugador));
    }

    private ConvocatoriaJugador fila(long convocatoria, long jugador) {
        return convocatoriaJugadorDao.obtenerPorConvocatoriaYJugador(convocatoria, jugador);
    }

    private void invitar(Long usuario, Long... jugadores) {
        service.sincronizarInvitados(usuario, convocatoria(100), partidos.get(10L), List.of(jugadores));
    }

    private List<Aviso> avisosDe(String tipo) {
        return avisos.stream().filter(a -> tipo.equals(a.tipo())).toList();
    }

    // ------------------------------------------------------------------ quién se puede invitar

    @Test
    void losEquiposInvitablesSonLosQueDejaLaRegla() {
        assertThat(service.equiposInvitables(ENTRENADOR_DESTINO, 10L))
                .extracting(e -> e.nombre())
                .containsExactly("Cadete B", "Infantil A", "Cadete C");
    }

    @Test
    void soloQuienGestionaElEquipoDelPartidoVeLosEquiposInvitables() {
        assertThatThrownBy(() -> service.equiposInvitables(ENTRENADOR_ORIGEN, 10L))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void losJugadoresInvitablesMarcanLosYaInvitadosYLosBloqueadosPorOtraInvitacion() {
        invitar(ENTRENADOR_DESTINO, ANA);
        // Biel ya está autorizado con Cadete C ese sábado (otro partido que no es de Cadete A).
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID)"
                + " VALUES (103, 6, 13, 8)");
        ConvocatoriaJugador otra = new ConvocatoriaJugador(null, 103L, BIEL);
        otra.setEquipoOrigenId(2L);
        otra.setEstadoInvitacion(EstadoInvitacion.AUTORIZADO);
        convocatoriaJugadorDao.guardar(otra);

        List<JugadorInvitableResponse> jugadores = service.jugadoresInvitables(ENTRENADOR_DESTINO, 10L, 2L);

        assertThat(jugadores).extracting(JugadorInvitableResponse::jugadorId).containsExactly(ANA, BIEL, DANI, EVA);
        assertThat(jugadores.get(0).yaInvitado()).isTrue();
        assertThat(jugadores.get(0).bloqueado()).isFalse();
        assertThat(jugadores.get(1).bloqueado()).isTrue();
        assertThat(jugadores.get(1).motivoBloqueo()).contains("Cadete C");
        assertThat(jugadores.get(2).yaInvitado()).isFalse();
    }

    @Test
    void noSePuedenPedirLosJugadoresDeUnEquipoNoPermitido() {
        assertThatThrownBy(() -> service.jugadoresInvitables(ENTRENADOR_DESTINO, 10L, 4L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.jugadoresInvitables(ENTRENADOR_DESTINO, 10L, 5L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.jugadoresInvitables(ENTRENADOR_DESTINO, 10L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unEntrenadorNoPuedeInvitarAUnJugadorDeUnEquipoNoPermitido() {
        assertThatThrownBy(() -> invitar(ENTRENADOR_DESTINO, GEMA)).isInstanceOf(IllegalArgumentException.class);
        assertThat(convocatoriaJugadorDao.obtenerTodasPorConvocatoria(100L)).isEmpty();
    }

    @Test
    void unJugadorDelPropioEquipoNoSeInvita() {
        assertThatThrownBy(() -> invitar(ENTRENADOR_DESTINO, FRAN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya es de este equipo");
    }

    @Test
    void unJugadorDeBajaOAPruebaNoSeInvita() {
        jdbc.update("UPDATE JUGADOR_TEMPORADA SET ESTADO = 'PRUEBA' WHERE JUGADOR_ID = ?", ANA);
        jdbc.update("UPDATE JUGADORES SET FECHA_BAJA = DATE '2026-10-01' WHERE ID = ?", BIEL);

        assertThatThrownBy(() -> invitar(ENTRENADOR_DESTINO, ANA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> invitar(ENTRENADOR_DESTINO, BIEL)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ invitar y visto bueno

    @Test
    void invitarDejaAlJugadorPendienteDeCoordinacionYAvisaATodaCoordinacion() {
        invitar(ENTRENADOR_DESTINO, ANA);

        ConvocatoriaJugador fila = fila(100, ANA);

        assertThat(fila.getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_COORDINACION);
        assertThat(fila.getEquipoOrigenId()).isEqualTo(2L);
        assertThat(fila.getUsuarioInvitaId()).isEqualTo(ENTRENADOR_DESTINO);
        assertThat(fila.getFechaSolicitud()).isEqualTo(ahora);

        Aviso aviso = avisosDe("INVITACION_PENDIENTE").get(0);

        assertThat(aviso.destinos()).containsExactlyInAnyOrder(COORDINADOR, ADMIN);
        assertThat(avisos).hasSize(1);
        // Todavía no se avisa ni a la familia ni al jugador ni a su entrenador.
        assertThat(avisosDe("INVITACION_AUTORIZADA")).isEmpty();
    }

    @Test
    void unInvitadoPendienteNoSaleEnLosConvocadosEfectivos() {
        invitar(ENTRENADOR_DESTINO, ANA);

        assertThat(convocatoriaJugadorDao.obtenerPorConvocatoria(100L)).isEmpty();
        assertThat(service.invitadosSinAutorizar(100L)).extracting(ConvocatoriaJugadorResponse::getEstadoInvitacion)
                .containsExactly("PENDIENTE_COORDINACION");
    }

    @Test
    void siConvocaUnCoordinadorQuedaAprobadoAlMomento() {
        // Carla es de Infantil A, que no juega el sábado: autorizada sin más.
        invitar(COORDINADOR, CARLA);

        ConvocatoriaJugador fila = fila(100, CARLA);

        assertThat(fila.getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        assertThat(fila.getUsuarioCoordinadorId()).isEqualTo(COORDINADOR);
        assertThat(fila.getFechaAutorizacion()).isEqualTo(ahora);
        assertThat(avisosDe("INVITACION_PENDIENTE")).isEmpty();
    }

    @Test
    void sinSolapamientoSeAutorizaYSoloEntoncesSeAvisaALaFamiliaYAlEntrenadorDeOrigen() {
        invitar(ENTRENADOR_DESTINO, CARLA);
        assertThat(avisosDe("INVITACION_AUTORIZADA")).isEmpty();

        InvitacionResponse respuesta = service.aprobar(COORDINADOR, fila(100, CARLA).getId());

        assertThat(respuesta.estado()).isEqualTo("AUTORIZADO");
        assertThat(fila(100, CARLA).getUsuarioCoordinadorId()).isEqualTo(COORDINADOR);

        // La familia de Carla recibe el aviso de convocatoria (tipo CONVOCATORIA, para abrir el detalle)...
        Aviso familia = avisos.stream().filter(a -> a.destinos().contains(31L)).findFirst().orElseThrow();
        assertThat(familia.tipo()).isEqualTo("CONVOCATORIA");
        assertThat(familia.data()).containsEntry("convocatoriaId", "100").containsEntry("partidoId", "10");
        assertThat(familia.contenido()).contains("Cadete A").contains("10:00h en Vestuarios");

        // ... y el entrenador de Infantil A, que juega otro día, un aviso de que jugará con Cadete A.
        assertThat(avisosDe("INVITACION_AUTORIZADA")).hasSize(1);
        assertThat(avisosDe("INVITACION_AUTORIZADA").get(0).destinos()).containsExactly(ENTRENADOR_INFANTIL);
        assertThat(avisosDe("INVITACION_INFORMATIVA")).isEmpty();
    }

    @Test
    void siSuEquipoJuegaEseDiaPeroNoLoTieneConvocadoQuedaAutorizadoYSuEntrenadorRecibeUnAvisoInformativo() {
        invitar(ENTRENADOR_DESTINO, ANA);

        service.aprobar(COORDINADOR, fila(100, ANA).getId());

        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        assertThat(avisosDe("INVITACION_INFORMATIVA")).hasSize(1);
        assertThat(avisosDe("INVITACION_INFORMATIVA").get(0).destinos()).containsExactly(ENTRENADOR_ORIGEN);
        assertThat(avisosDe("INVITACION_LIBERAR")).isEmpty();
        // La familia y el propio jugador: dos avisos de convocatoria.
        assertThat(avisosDe("CONVOCATORIA")).extracting(a -> a.destinos().get(0))
                .containsExactlyInAnyOrder(20L, 21L);
        assertThat(avisosDe("CONVOCATORIA").stream().filter(a -> a.destinos().contains(20L)).findFirst().orElseThrow()
                .contenido()).startsWith("Has sido convocado con Cadete A");
    }

    @Test
    void siSuEntrenadorLoTieneConvocadoQuedaPendienteDeEsteYSeLePideQueLoLibere() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);

        service.aprobar(COORDINADOR, fila(100, ANA).getId());

        ConvocatoriaJugador fila = fila(100, ANA);

        assertThat(fila.getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_ENTRENADOR);
        assertThat(fila.getFechaPeticionEntrenador()).isEqualTo(ahora);
        assertThat(fila.isAviso12hEnviado()).isFalse();

        Aviso aviso = avisosDe("INVITACION_LIBERAR").get(0);

        assertThat(aviso.destinos()).containsExactly(ENTRENADOR_ORIGEN);
        assertThat(aviso.contenido()).contains("coincide con un partido de Cadete B").contains("quítalo de tu convocatoria");
        // Para llevarlo a su propia convocatoria de ese día.
        assertThat(aviso.data()).containsEntry("convocatoriaOrigenId", "101").containsEntry("invitacionId",
                String.valueOf(fila.getId()));
        // Nadie de la familia sabe nada todavía, y sigue convocado con su equipo.
        assertThat(avisosDe("CONVOCATORIA")).isEmpty();
        assertThat(fila(101, ANA)).isNotNull();
        assertThat(convocatoriaJugadorDao.obtenerPorConvocatoria(100L)).isEmpty();
    }

    @Test
    void siElPartidoDeSuEquipoSeCancelaNoHaySolapamiento() {
        convocarEnOrigen(ANA);
        jdbc.update("UPDATE PARTIDOS SET CANCELADO = 1 WHERE ID = 11");
        invitar(ENTRENADOR_DESTINO, ANA);

        service.aprobar(COORDINADOR, fila(100, ANA).getId());

        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        assertThat(avisosDe("INVITACION_INFORMATIVA")).isEmpty();
        assertThat(avisosDe("INVITACION_AUTORIZADA")).hasSize(1);
    }

    @Test
    void soloCoordinacionAprueba() {
        invitar(ENTRENADOR_DESTINO, ANA);

        assertThatThrownBy(() -> service.aprobar(ENTRENADOR_DESTINO, fila(100, ANA).getId()))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.pendientes(ENTRENADOR_ORIGEN)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.rechazar(ENTRENADOR_ORIGEN, fila(100, ANA).getId(), null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void noSeApruebaDosVeces() {
        invitar(ENTRENADOR_DESTINO, ANA);
        Long id = fila(100, ANA).getId();

        service.aprobar(COORDINADOR, id);

        assertThatThrownBy(() -> service.aprobar(ADMIN, id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazarAvisaAlQueInvitoSinElMotivoEnElPushYSePuedeReinvitar() {
        invitar(ENTRENADOR_DESTINO, ANA);
        Long id = fila(100, ANA).getId();

        InvitacionResponse respuesta = service.rechazar(COORDINADOR, id, "  Juega ese día  ");

        assertThat(respuesta.estado()).isEqualTo("RECHAZADO");
        assertThat(respuesta.motivoRechazo()).isEqualTo("Juega ese día");

        Aviso aviso = avisosDe("INVITACION_RECHAZADA").get(0);

        assertThat(aviso.destinos()).containsExactly(ENTRENADOR_DESTINO);
        assertThat(aviso.contenido()).contains("Motivo: Juega ese día");
        assertThat(aviso.push()).doesNotContain("Juega ese día");
        assertThat(avisosDe("INVITACION_AUTORIZADA")).isEmpty();
        assertThat(service.invitadosSinAutorizar(100L)).extracting(ConvocatoriaJugadorResponse::getMotivoRechazo)
                .containsExactly("Juega ese día");

        // Reinvitar al mismo jugador reutiliza la fila y vuelve a pedir el visto bueno.
        invitar(ENTRENADOR_DESTINO, ANA);

        assertThat(convocatoriaJugadorDao.obtenerTodasPorConvocatoria(100L)).hasSize(1);
        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_COORDINACION);
        assertThat(fila(100, ANA).getMotivoRechazo()).isNull();
        assertThat(fila(100, ANA).getId()).isEqualTo(id);
    }

    // ------------------------------------------------------------------ máximo de 3

    @Test
    void elMaximoEsDeTresInvitadosSinContarLosRechazados() {
        // Eva pasa a Cadete C: los invitados pueden venir de equipos distintos.
        jdbc.update("UPDATE JUGADOR_TEMPORADA SET EQUIPO_ID = 6 WHERE JUGADOR_ID = ?", EVA);
        equipoDelJugador.put(EVA, 6L);

        invitar(ENTRENADOR_DESTINO, ANA, BIEL, DANI);

        assertThatThrownBy(() -> invitar(ENTRENADOR_DESTINO, ANA, BIEL, DANI, EVA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("máximo 3");

        service.rechazar(COORDINADOR, fila(100, DANI).getId(), null);

        // Con Dani rechazado y fuera de la lista quedan 2 activos: cabe otro.
        invitar(ENTRENADOR_DESTINO, ANA, BIEL, EVA);

        assertThat(fila(100, EVA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_COORDINACION);
        assertThat(convocatoriaJugadorDao.obtenerTodasPorConvocatoria(100L)).extracting(ConvocatoriaJugador::getJugadorId)
                .containsExactlyInAnyOrder(ANA, BIEL, EVA);
    }

    @Test
    void losDesestimadosPorLaFamiliaNoCuentanParaElMaximo() {
        invitar(COORDINADOR, ANA, BIEL, CARLA);
        jdbc.update("INSERT INTO JUSTIFICACIONES_FALTA_PARTIDO (PARTIDO_ID, JUGADOR_ID, USUARIO_APP_ID, MOTIVO,"
                + " FECHA_CREACION) VALUES (10, ?, 21, 'Enfermo', CURRENT_TIMESTAMP)", ANA);

        invitar(COORDINADOR, ANA, BIEL, CARLA, DANI);

        assertThat(convocatoriaJugadorDao.obtenerTodasPorConvocatoria(100L)).hasSize(4);
    }

    @Test
    void sinLaListaDeInvitadosNoSeTocaNada() {
        invitar(ENTRENADOR_DESTINO, ANA);

        service.sincronizarInvitados(ENTRENADOR_DESTINO, convocatoria(100), partidos.get(10L), null);

        assertThat(fila(100, ANA)).isNotNull();
    }

    @Test
    void conUnaListaVaciaSeQuitanTodosLosInvitados() {
        invitar(ENTRENADOR_DESTINO, ANA);

        service.sincronizarInvitados(ENTRENADOR_DESTINO, convocatoria(100), partidos.get(10L), List.of());

        assertThat(convocatoriaJugadorDao.obtenerTodasPorConvocatoria(100L)).isEmpty();
        // Estaba pendiente de coordinación: no había nada que contar ni a la familia ni a su entrenador.
        assertThat(avisosDe("INVITACION_ANULADA")).isEmpty();
    }

    // ------------------------------------------------------------------ un día, un equipo

    @Test
    void noSePuedeInvitarAUnJugadorYaAutorizadoConOtroEquipoEseDia() {
        // Ana ya está autorizada con Cadete C ese sábado.
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID)"
                + " VALUES (103, 6, 13, 8)");
        ConvocatoriaJugador otra = new ConvocatoriaJugador(null, 103L, ANA);
        otra.setEquipoOrigenId(2L);
        otra.setEstadoInvitacion(EstadoInvitacion.AUTORIZADO);
        convocatoriaJugadorDao.guardar(otra);

        assertThatThrownBy(() -> invitar(ENTRENADOR_DESTINO, ANA))
                .isInstanceOfSatisfying(ConflictoInvitacionException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("JUGADOR_YA_INVITADO_ESE_DIA");
                    assertThat(e.getEquipoDestino()).isEqualTo("Cadete C");
                    assertThat(e.getJugadorId()).isEqualTo(ANA);
                    assertThat(e.getFecha()).isEqualTo(SABADO);
                });
        assertThat(fila(100, ANA)).isNull();
    }

    @Test
    void siLaOtraInvitacionSigueSinAutorizarSePermiteYCoordinacionVeLaMarca() {
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID)"
                + " VALUES (103, 6, 13, 8)");
        ConvocatoriaJugador otra = new ConvocatoriaJugador(null, 103L, ANA);
        otra.setEquipoOrigenId(2L);
        otra.setEstadoInvitacion(EstadoInvitacion.PENDIENTE_COORDINACION);
        convocatoriaJugadorDao.guardar(otra);

        invitar(ENTRENADOR_DESTINO, ANA);

        InvitacionPendienteResponse pendiente = service.pendientes(COORDINADOR).stream()
                .filter(p -> p.convocatoriaId().equals(100L)).findFirst().orElseThrow();

        assertThat(pendiente.otraInvitacion()).isNotNull();
        assertThat(pendiente.otraInvitacion().equipoDestino()).isEqualTo("Cadete C");
        assertThat(pendiente.otraInvitacion().estado()).isEqualTo("PENDIENTE_COORDINACION");
        assertThat(pendiente.otraInvitacion().rival()).isEqualTo("Javea");
    }

    @Test
    void alAprobarSeVuelveAComprobarQueNoHayaOtraAutorizadaEseDia() {
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID)"
                + " VALUES (103, 6, 13, 8)");
        ConvocatoriaJugador otra = new ConvocatoriaJugador(null, 103L, ANA);
        otra.setEquipoOrigenId(2L);
        otra.setEstadoInvitacion(EstadoInvitacion.PENDIENTE_COORDINACION);
        convocatoriaJugadorDao.guardar(otra);
        invitar(ENTRENADOR_DESTINO, ANA);

        service.aprobar(COORDINADOR, otra.getId());

        assertThatThrownBy(() -> service.aprobar(COORDINADOR, fila(100, ANA).getId()))
                .isInstanceOfSatisfying(ConflictoInvitacionException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("JUGADOR_YA_INVITADO_ESE_DIA"));
    }

    @Test
    void suEntrenadorNoPuedeConvocarloEseDiaSiYaEstaAutorizadoConOtroEquipo() {
        invitar(COORDINADOR, ANA);

        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);

        assertThatThrownBy(() -> service.comprobarNoInvitadoEseDia(List.of(ANA), partidos.get(11L)))
                .isInstanceOfSatisfying(ConflictoInvitacionException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("JUGADOR_INVITADO_ESE_DIA");
                    assertThat(e.getEquipoDestino()).isEqualTo("Cadete A");
                    assertThat(e.getMessage()).contains("Ana ya está convocado como invitado con Cadete A");
                });

        // Otro día sí, y quien no es invitado tampoco tiene problema.
        service.comprobarNoInvitadoEseDia(List.of(ANA), partidos.get(12L));
        service.comprobarNoInvitadoEseDia(List.of(BIEL), partidos.get(11L));
    }

    @Test
    void unaInvitacionSinAutorizarNoBloqueaASuEntrenador() {
        invitar(ENTRENADOR_DESTINO, ANA);

        service.comprobarNoInvitadoEseDia(List.of(ANA), partidos.get(11L));
    }

    // ------------------------------------------------------------------ liberar

    @Test
    void suEntrenadorLoLiberaQuitandoloDeSuConvocatoriaYQuedaAutorizado() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());
        avisos.clear();

        InvitacionResponse respuesta = service.liberar(ENTRENADOR_ORIGEN, fila(100, ANA).getId());

        assertThat(respuesta.estado()).isEqualTo("AUTORIZADO");
        assertThat(fila(101, ANA)).isNull();
        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        assertThat(fila(100, ANA).getFechaAutorizacion()).isEqualTo(ahora);
        assertThat(convocatoriaJugadorDao.obtenerPorConvocatoria(100L)).extracting(ConvocatoriaJugador::getJugadorId)
                .containsExactly(ANA);

        // La familia y el jugador saben que ya no está con su equipo y que sí lo está con el otro.
        List<Aviso> aFamilia = avisos.stream().filter(a -> a.destinos().contains(21L)).toList();

        assertThat(aFamilia).hasSize(2);
        assertThat(aFamilia.get(0).contenido()).contains("Biel ya no está convocado".replace("Biel", "Ana"))
                .contains("Cadete B");
        assertThat(aFamilia.get(1).contenido()).contains("Ana ha sido convocado con Cadete A");
        // Quien lo libera no recibe aviso de lo que acaba de hacer.
        assertThat(avisos).noneMatch(a -> a.destinos().contains(ENTRENADOR_ORIGEN));
        assertThat(avisosDe("INVITACION_LIBERADA_COORDINACION")).isEmpty();
    }

    @Test
    void coordinacionTambienPuedeLiberarYSuEntrenadorRecibeElAviso() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());
        avisos.clear();

        service.liberar(ADMIN, fila(100, ANA).getId());

        assertThat(fila(101, ANA)).isNull();
        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);

        Aviso aviso = avisosDe("INVITACION_LIBERADA_COORDINACION").get(0);

        assertThat(aviso.destinos()).containsExactly(ENTRENADOR_ORIGEN);
        assertThat(aviso.contenido()).contains("Coordinación ha liberado a Ana").contains("lo ha quitado de tu convocatoria");
    }

    @Test
    void soloSuEntrenadorOCoordinacionLiberan() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());

        assertThatThrownBy(() -> service.liberar(ENTRENADOR_DESTINO, fila(100, ANA).getId()))
                .isInstanceOf(SecurityException.class);
        assertThat(fila(101, ANA)).isNotNull();
    }

    @Test
    void soloSeLiberaLoQueEstaPendienteDeSuEntrenador() {
        invitar(ENTRENADOR_DESTINO, ANA);

        assertThatThrownBy(() -> service.liberar(ENTRENADOR_ORIGEN, fila(100, ANA).getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void siMientrasTantoQuedoAutorizadoConOtroEquipoNoSeLiberaYaQueNoSePuedeEstarEnDos() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());

        // Cadete C también la había pedido y su entrenador ya la tiene autorizada.
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID)"
                + " VALUES (103, 6, 13, 8)");
        ConvocatoriaJugador otra = new ConvocatoriaJugador(null, 103L, ANA);
        otra.setEquipoOrigenId(2L);
        otra.setEstadoInvitacion(EstadoInvitacion.AUTORIZADO);
        convocatoriaJugadorDao.guardar(otra);

        assertThatThrownBy(() -> service.liberar(ENTRENADOR_ORIGEN, fila(100, ANA).getId()))
                .isInstanceOf(ConflictoInvitacionException.class);
        assertThat(fila(101, ANA)).isNotNull();
        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_ENTRENADOR);
    }

    @Test
    void siSuEntrenadorLoQuitaDeSuConvocatoriaLaInvitacionQuedaAutorizada() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());
        avisos.clear();

        // Lo hace ConvocatoriaServiceImpl.actualizar tras quitar a Ana de su lista.
        convocatoriaJugadorDao.eliminarPorConvocatoriaYJugador(101L, ANA);
        service.alQuitarJugadoresPropios(ENTRENADOR_ORIGEN, 2L, SABADO, List.of(ANA, BIEL));

        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        assertThat(avisosDe("CONVOCATORIA")).extracting(a -> a.destinos().get(0)).containsExactlyInAnyOrder(20L, 21L);
    }

    @Test
    void quitarAUnJugadorQueNoEstabaPidiendoOtroEquipoNoHaceNada() {
        service.alQuitarJugadoresPropios(ENTRENADOR_ORIGEN, 2L, SABADO, List.of(ANA));
        service.alQuitarJugadoresPropios(ENTRENADOR_ORIGEN, 2L, null, List.of(ANA));
        service.alQuitarJugadoresPropios(ENTRENADOR_ORIGEN, 2L, SABADO, List.of());

        assertThat(avisos).isEmpty();
    }

    // ------------------------------------------------------------------ lista de coordinación

    @Test
    void laListaDeCoordinacionIncluyeLosPendientesDeSuEntrenadorConElSolapamientoYElPlazo() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA, BIEL);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());

        List<InvitacionPendienteResponse> pendientes = service.pendientes(ADMIN);

        assertThat(pendientes).extracting(InvitacionPendienteResponse::jugadorId).containsExactlyInAnyOrder(ANA, BIEL);

        InvitacionPendienteResponse ana = pendientes.stream().filter(p -> p.jugadorId().equals(ANA)).findFirst()
                .orElseThrow();

        assertThat(ana.estado()).isEqualTo("PENDIENTE_ENTRENADOR");
        assertThat(ana.jugador()).isEqualTo("Ana Prueba");
        assertThat(ana.equipoOrigen()).isEqualTo("Cadete B");
        assertThat(ana.equipoDestino()).isEqualTo("Cadete A");
        assertThat(ana.fechaPartido()).isEqualTo(SABADO);
        assertThat(ana.horaPartido()).isEqualTo("11:00");
        assertThat(ana.rival()).isEqualTo("Pego");
        assertThat(ana.usuarioInvitaId()).isEqualTo(ENTRENADOR_DESTINO);
        assertThat(ana.convoca()).isEqualTo("Cuerpo técnico de Cadete A");
        assertThat(ana.jugadorConvocadoEnOrigen()).isTrue();
        assertThat(ana.solapamiento()).extracting(InvitacionPendienteResponse.PartidoSolapado::rival)
                .containsExactly("Denia");
        assertThat(ana.plazoVencido()).isFalse();
        assertThat(ana.fechaPeticionEntrenador()).isEqualTo(ahora);

        InvitacionPendienteResponse biel = pendientes.stream().filter(p -> p.jugadorId().equals(BIEL)).findFirst()
                .orElseThrow();

        assertThat(biel.estado()).isEqualTo("PENDIENTE_COORDINACION");
        assertThat(biel.solapamiento()).hasSize(1);
        assertThat(biel.jugadorConvocadoEnOrigen()).isFalse();
        assertThat(biel.otraInvitacion()).isNull();
    }

    @Test
    void sinPartidoDeSuEquipoEseDiaNoHaySolapamiento() {
        invitar(ENTRENADOR_DESTINO, CARLA);

        InvitacionPendienteResponse pendiente = service.pendientes(COORDINADOR).get(0);

        assertThat(pendiente.solapamiento()).isEmpty();
        assertThat(pendiente.jugadorConvocadoEnOrigen()).isFalse();
    }

    @Test
    void laListaNoIncluyeLosAutorizadosNiLosRechazadosNiLosDePartidosYaJugados() {
        invitar(COORDINADOR, CARLA);
        invitar(ENTRENADOR_DESTINO, CARLA, ANA);
        service.rechazar(COORDINADOR, fila(100, ANA).getId(), null);

        assertThat(service.pendientes(COORDINADOR)).isEmpty();
    }

    // ------------------------------------------------------------------ plazo de 12 horas

    @Test
    void alPasarLas12HorasSeAvisaACoordinacionUnaSolaVez() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());
        avisos.clear();

        // A las 11 horas todavía no.
        ahora = ahora.plusHours(11);
        service = conReloj(ahora);
        assertThat(service.avisarPlazosVencidos()).isZero();

        ahora = ahora.plusHours(1);
        service = conReloj(ahora);
        assertThat(service.avisarPlazosVencidos()).isEqualTo(1);

        Aviso aviso = avisosDe("INVITACION_PLAZO").get(0);

        assertThat(aviso.destinos()).containsExactlyInAnyOrder(COORDINADOR, ADMIN);
        assertThat(aviso.contenido()).contains("más de 12 horas").contains("Cadete B").contains("Ana Prueba");
        assertThat(fila(100, ANA).isAviso12hEnviado()).isTrue();
        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_ENTRENADOR);

        // La lista de coordinación marca el plazo vencido, y el aviso no se repite.
        assertThat(service.pendientes(COORDINADOR).get(0).plazoVencido()).isTrue();
        assertThat(service.avisarPlazosVencidos()).isZero();
        assertThat(avisosDe("INVITACION_PLAZO")).hasSize(1);
    }

    @Test
    void unaInvitacionYaLiberadaNoGeneraAvisoDePlazo() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());
        service.liberar(ENTRENADOR_ORIGEN, fila(100, ANA).getId());
        avisos.clear();

        service = conReloj(ahora.plusHours(20));

        assertThat(service.avisarPlazosVencidos()).isZero();
        assertThat(avisos).isEmpty();
    }

    private InvitadosServiceImpl conReloj(LocalDateTime hora) {
        return new InvitadosServiceImpl(
                convocatoriaJugadorDao, new InvitacionDaoImpl(jdbc), new ConvocatoriaDaoImpl(jdbc),
                partidoDao(), equipoDao(), categoriaEdadDao(), temporadaDao(), gestion,
                new JustificacionFaltaPartidoDaoImpl(jdbc), mock(UsuarioAppDao.class), comunicacion(),
                justificacionService, new ReglaInvitados(),
                new com.mikedev.mutxamelcf.dao.impl.EquipacionDaoImpl(jdbc),
                Clock.fixed(hora.atZone(ZoneId.of("Europe/Madrid")).toInstant(), ZoneId.of("Europe/Madrid")));
    }

    // Los mismos colaboradores del setUp, para reconstruir el servicio con otro reloj.
    private PartidoDao partidoDao() {
        PartidoDao partidoDao = mock(PartidoDao.class);
        when(partidoDao.obtenerPorId(anyLong())).thenAnswer(i -> partidos.get((Long) i.getArgument(0)));
        return partidoDao;
    }

    private EquipoDao equipoDao() {
        EquipoDao equipoDao = mock(EquipoDao.class);
        when(equipoDao.obtenerActivos()).thenReturn(List.of(
                equipo(1, "Cadete", "A", "Cadete A", null),
                equipo(2, "Cadete", "B", "Cadete B", null),
                equipo(3, "Infantil", "A", "Infantil A", null)));
        return equipoDao;
    }

    private CategoriaEdadDao categoriaEdadDao() {
        CategoriaEdadDao dao = mock(CategoriaEdadDao.class);
        when(dao.obtenerTodas()).thenReturn(List.of(
                new CategoriaEdad("Infantil", 13, 14, 5), new CategoriaEdad("Cadete", 15, 16, 6)));
        return dao;
    }

    private TemporadaDao temporadaDao() {
        TemporadaDao dao = mock(TemporadaDao.class);
        when(dao.obtenerTemporadaActiva()).thenReturn(new Temporada(5L, "2026/27", null, null, 1));
        return dao;
    }

    private ComunicacionService comunicacion() {
        ComunicacionService comunicacionService = mock(ComunicacionService.class);
        when(comunicacionService.crearPrivada(any(Comunicacion.class), any(), any(),
                any(OpcionesPushComunicacion.class))).thenAnswer(i -> {
                    Comunicacion c = i.getArgument(0);
                    OpcionesPushComunicacion opciones = i.getArgument(3);
                    Long remitente = i.getArgument(2);
                    avisos.add(new Aviso(c.getTitulo(), i.getArgument(1), remitente, c.getContenido(),
                            opciones.contenidoPush(), opciones.datos()));
                    return c;
                });
        return comunicacionService;
    }

    // ------------------------------------------------------------------ desestimar

    @Test
    void desestimarEsUnaJustificacionDeAusenciaDeUnInvitadoAutorizado() {
        invitar(COORDINADOR, CARLA);

        service.desestimar(31L, 10L, CARLA, "Tiene cumpleaños");

        org.mockito.ArgumentCaptor<JustificacionFaltaRequest> captor = org.mockito.ArgumentCaptor
                .forClass(JustificacionFaltaRequest.class);
        verify(justificacionService).justificar(org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        assertThat(captor.getValue().getJugadorId()).isEqualTo(CARLA);
        assertThat(captor.getValue().getMotivo()).isEqualTo("Tiene cumpleaños");
    }

    @Test
    void soloSePuedeDesestimarAUnInvitadoYaAutorizado() {
        invitar(ENTRENADOR_DESTINO, ANA);

        assertThatThrownBy(() -> service.desestimar(21L, 10L, ANA, "No puede"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.desestimar(21L, 10L, FRAN, "No puede"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.desestimar(21L, 12L, ANA, "No puede"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(justificacionService, never()).justificar(anyLong(), anyLong(), any());
    }

    // ------------------------------------------------------------------ si se cancela algo

    @Test
    void alQuitarAUnInvitadoAutorizadoSeAvisaASuFamiliaYASuEntrenador() {
        invitar(COORDINADOR, ANA);
        avisos.clear();

        service.sincronizarInvitados(ENTRENADOR_DESTINO, convocatoria(100), partidos.get(10L), List.of());

        assertThat(fila(100, ANA)).isNull();

        List<Aviso> anulaciones = avisosDe("INVITACION_ANULADA");

        assertThat(anulaciones).hasSize(3);
        assertThat(anulaciones.stream().filter(a -> a.destinos().contains(20L)).findFirst().orElseThrow().contenido())
                .startsWith("Ya no juegas con Cadete A");
        assertThat(anulaciones.stream().filter(a -> a.destinos().contains(21L)).findFirst().orElseThrow().contenido())
                .startsWith("Ana ya no juega con Cadete A");
        assertThat(anulaciones.stream().filter(a -> a.destinos().contains(ENTRENADOR_ORIGEN)).findAny()).isPresent();
    }

    @Test
    void alQuitarAUnInvitadoPendienteDeSuEntrenadorSeAvisaSoloAEste() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());
        avisos.clear();

        service.sincronizarInvitados(ENTRENADOR_DESTINO, convocatoria(100), partidos.get(10L), List.of());

        assertThat(avisosDe("INVITACION_ANULADA")).hasSize(1);
        assertThat(avisosDe("INVITACION_ANULADA").get(0).destinos()).containsExactly(ENTRENADOR_ORIGEN);
    }

    @Test
    void alBorrarLaConvocatoriaOCancelarElPartidoSeAvisaALosInvitados() {
        invitar(COORDINADOR, ANA);
        avisos.clear();

        service.alEliminarConvocatoria(convocatoria(100), partidos.get(10L));

        assertThat(avisosDe("INVITACION_ANULADA")).hasSize(3);
        // El push no lleva el id de una convocatoria que ya no existe.
        assertThat(avisosDe("INVITACION_ANULADA")).allMatch(a -> !a.data().containsKey("convocatoriaId"));

        avisos.clear();

        service.alCancelarPartido(partidos.get(10L));

        assertThat(avisosDe("INVITACION_ANULADA")).hasSize(3);
        assertThat(avisosDe("INVITACION_ANULADA").get(0).contenido()).contains("Pego");
    }

    @Test
    void cancelarUnPartidoSinConvocatoriaNoHaceNada() {
        service.alCancelarPartido(partidos.get(12L));

        assertThat(avisos).isEmpty();
    }

    @Test
    void noSeCambiaElPartidoDeUnaConvocatoriaConInvitados() {
        service.comprobarSinInvitadosAlCambiarPartido(100L);

        invitar(ENTRENADOR_DESTINO, ANA);

        assertThatThrownBy(() -> service.comprobarSinInvitadosAlCambiarPartido(100L))
                .isInstanceOf(IllegalArgumentException.class);

        service.rechazar(COORDINADOR, fila(100, ANA).getId(), null);

        service.comprobarSinInvitadosAlCambiarPartido(100L);
    }

    // ------------------------------------------------------------------ calendario

    @Test
    void losPartidosComoInvitadoSonSoloLosDeLasInvitacionesAutorizadas() {
        invitar(COORDINADOR, ANA);
        invitar(ENTRENADOR_DESTINO, ANA, BIEL);

        assertThat(service.partidosComoInvitado(ANA, SABADO, SABADO)).extracting(Partido::getId).containsExactly(10L);
        assertThat(service.partidosComoInvitado(BIEL, SABADO, SABADO)).isEmpty();
        assertThat(service.partidosComoInvitado(ANA, DOMINGO, DOMINGO)).isEmpty();
    }

    // ------------------------------------------------------------------ si cambia la fecha del partido

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);

    /** Cambia el día del partido en la base de datos y en el objeto que ve el servicio; devuelve el día antiguo. */
    private LocalDate cambiarDia(long partidoId, LocalDate nuevo) {
        Partido partido = partidos.get(partidoId);
        LocalDate antiguo = partido.getDia() instanceof Date sql ? sql.toLocalDate() : null;

        jdbc.update("UPDATE PARTIDOS SET DIA = ? WHERE ID = ?", Date.valueOf(nuevo), partidoId);
        partido.setDia(Date.valueOf(nuevo));

        return antiguo;
    }

    @Test
    void siCambiaLaFechaDelPartidoDeDestinoSeAnulanLasInvitacionesNoRechazadasYSeAvisaAQuienInvito() {
        invitar(COORDINADOR, ANA);                  // autorizada
        invitar(ENTRENADOR_DESTINO, ANA, BIEL, DANI);    // Biel y Dani, pendientes de coordinación
        service.rechazar(COORDINADOR, fila(100, DANI).getId(), null);
        avisos.clear();

        LocalDate antiguo = cambiarDia(10, LUNES);
        service.alCambiarFechaPartido(partidos.get(10L), antiguo, LUNES);

        // Las anuladas desaparecen; la rechazada se queda como estaba.
        assertThat(fila(100, ANA)).isNull();
        assertThat(fila(100, BIEL)).isNull();
        assertThat(fila(100, DANI).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.RECHAZADO);

        // Anulada la autorizada: su familia, el jugador y su entrenador, con la fecha en que se jugaba.
        List<Aviso> anulaciones = avisosDe("INVITACION_ANULADA");

        assertThat(anulaciones).hasSize(3);
        assertThat(anulaciones).allMatch(a -> a.data().get("fechaPartido").equals("2026-11-14"));
        assertThat(anulaciones.stream().filter(a -> a.destinos().contains(21L)).findFirst().orElseThrow().contenido())
                .contains("Ana ya no juega con Cadete A").contains("14/11/2026");
        assertThat(anulaciones.stream().anyMatch(a -> a.destinos().contains(ENTRENADOR_ORIGEN))).isTrue();
        // De Biel, que solo esperaba a coordinación, no hay nada que contarle a nadie salvo a quien lo invitó.
        assertThat(anulaciones).noneMatch(a -> a.contenido().contains("Biel"));

        // A cada uno que invitó, un solo aviso con las fechas y los jugadores, para volver a invitar.
        List<Aviso> aplazados = avisosDe("INVITACION_FECHA_CAMBIADA");

        assertThat(aplazados).hasSize(2);

        Aviso alCoordinador = aplazados.stream().filter(a -> a.destinos().contains(COORDINADOR)).findFirst()
                .orElseThrow();
        Aviso alEntrenador = aplazados.stream().filter(a -> a.destinos().contains(ENTRENADOR_DESTINO)).findFirst()
                .orElseThrow();

        assertThat(alCoordinador.data()).containsEntry("jugadorIds", "1").containsEntry("fechaPartido", "2026-11-16")
                .containsEntry("fechaAnterior", "2026-11-14").containsEntry("convocatoriaId", "100")
                .containsEntry("partidoId", "10");
        assertThat(alEntrenador.data()).containsEntry("jugadorIds", "2");
        assertThat(alEntrenador.contenido()).contains("del 14/11/2026 al 16/11/2026").contains("Biel")
                .contains("vuelve a invitarlos para la nueva fecha");
        assertThat(alEntrenador.push()).doesNotContain("Biel");
    }

    @Test
    void siElPartidoNoCambiaDeDiaOSinConvocatoriaNoPasaNada() {
        invitar(COORDINADOR, ANA);
        avisos.clear();

        service.alCambiarFechaPartido(partidos.get(10L), SABADO, SABADO);
        service.alCambiarFechaPartido(partidos.get(10L), null, LUNES);
        service.alCambiarFechaPartido(null, SABADO, LUNES);
        // El partido 12 (Infantil A) no tiene convocatoria ni invitados.
        service.alCambiarFechaPartido(partidos.get(12L), DOMINGO, LUNES);

        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        assertThat(avisos).isEmpty();
    }

    @Test
    void siElPartidoDelEquipoDeOrigenSeVaDeEseDiaLoQueEsperabaASuEntrenadorQuedaAutorizado() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());
        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_ENTRENADOR);
        avisos.clear();

        // Cadete B juega el sábado 14 y lo pasan al domingo 15.
        LocalDate antiguo = cambiarDia(11, DOMINGO);
        service.alCambiarFechaPartido(partidos.get(11L), antiguo, DOMINGO);

        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        assertThat(fila(100, ANA).getFechaAutorizacion()).isEqualTo(ahora);
        // Con sus avisos: la familia y el jugador (convocatoria) y su entrenador (jugará con Cadete A).
        assertThat(avisosDe("CONVOCATORIA")).extracting(a -> a.destinos().get(0)).containsExactlyInAnyOrder(20L, 21L);
        assertThat(avisosDe("INVITACION_AUTORIZADA")).hasSize(1);
        assertThat(avisosDe("INVITACION_AUTORIZADA").get(0).destinos()).containsExactly(ENTRENADOR_ORIGEN);
    }

    @Test
    void siElPartidoDelEquipoDeOrigenLlegaAEseDiaYLoTieneConvocadoLoAutorizadoVuelveAEsperarASuEntrenador() {
        // Cadete B juega el domingo 15 y Ana está convocada con él; el sábado no juega.
        cambiarDia(11, DOMINGO);
        convocarEnOrigen(ANA);
        invitar(COORDINADOR, ANA);
        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        avisos.clear();

        // Lo adelantan al sábado 14, el día del partido al que está invitada.
        LocalDate antiguo = cambiarDia(11, SABADO);
        service.alCambiarFechaPartido(partidos.get(11L), antiguo, SABADO);

        ConvocatoriaJugador fila = fila(100, ANA);

        assertThat(fila.getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_ENTRENADOR);
        assertThat(fila.getFechaPeticionEntrenador()).isEqualTo(ahora);
        assertThat(fila.isAviso12hEnviado()).isFalse();

        // Su entrenador recibe la petición de liberarlo, con su convocatoria de ese día.
        Aviso liberar = avisosDe("INVITACION_LIBERAR").get(0);

        assertThat(liberar.destinos()).containsExactly(ENTRENADOR_ORIGEN);
        assertThat(liberar.data()).containsEntry("convocatoriaOrigenId", "101");

        // Y la familia y el jugador saben que ya no está confirmado.
        List<Aviso> solapamiento = avisosDe("INVITACION_SOLAPAMIENTO");

        assertThat(solapamiento).extracting(a -> a.destinos().get(0)).containsExactlyInAnyOrder(20L, 21L);
        assertThat(solapamiento.stream().filter(a -> a.destinos().contains(20L)).findFirst().orElseThrow().contenido())
                .startsWith("Dejas de estar confirmado con Cadete A el 14/11/2026");
        assertThat(convocatoriaJugadorDao.obtenerPorConvocatoria(100L)).isEmpty();
    }

    @Test
    void laReevaluacionNoTocaLasQueSiguenIgualNiLasQueYaEsperanASuEntrenador() {
        convocarEnOrigen(ANA);
        invitar(ENTRENADOR_DESTINO, ANA, CARLA);
        service.aprobar(COORDINADOR, fila(100, ANA).getId());     // pendiente de su entrenador
        service.aprobar(COORDINADOR, fila(100, CARLA).getId());   // autorizada (Infantil A juega otro día)
        avisos.clear();

        LocalDateTime peticion = fila(100, ANA).getFechaPeticionEntrenador();

        // Cadete B mueve su partido del sábado al lunes, pero tiene otro el sábado 14 en el que Ana también está
        // convocada: nada cambia.
        partido(14, 2, SABADO, "17:00", "Calp");
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID) VALUES (102, 2, 14, 8)");
        convocatoriaJugadorDao.guardar(new ConvocatoriaJugador(null, 102L, ANA));
        LocalDate antiguo = cambiarDia(11, LUNES);
        service.alCambiarFechaPartido(partidos.get(11L), antiguo, LUNES);

        assertThat(fila(100, ANA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_ENTRENADOR);
        assertThat(fila(100, ANA).getFechaPeticionEntrenador()).isEqualTo(peticion);
        assertThat(fila(100, CARLA).getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        assertThat(avisos).isEmpty();
    }

    @Test
    void elAvisoDeConvocadoConOtroEquipoLlevaElEquipajeYLaDuchaAlFinal() {
        jdbc.update("INSERT INTO EQUIPACIONES (NOMBRE) VALUES ('Segunda')");
        jdbc.update("INSERT INTO EQUIPACIONES (NOMBRE) VALUES ('Primera')");
        jdbc.update("UPDATE CONVOCATORIAS SET EQUIPACION_ID = (SELECT ID FROM EQUIPACIONES WHERE NOMBRE = 'Segunda'),"
                + " EQUIPACION_MOCHILA_ID = (SELECT ID FROM EQUIPACIONES WHERE NOMBRE = 'Primera') WHERE ID = 100");
        invitar(ENTRENADOR_DESTINO, ANA);

        service.aprobar(COORDINADOR, fila(100, ANA).getId());

        assertThat(avisosDe("CONVOCATORIA")).isNotEmpty().allSatisfy(a -> assertThat(a.contenido())
                .contains("La convocatoria es a las 10:00h en Vestuarios. Equipaje: Segunda. En la mochila: Primera.")
                .endsWith("\n\n🚿 Ducha recomendable después del partido"));
    }

    @Test
    void elAvisoDeConvocadoConOtroEquipoSinEquipajeSoloLlevaLaDucha() {
        invitar(ENTRENADOR_DESTINO, ANA);

        service.aprobar(COORDINADOR, fila(100, ANA).getId());

        assertThat(avisosDe("CONVOCATORIA")).isNotEmpty().allSatisfy(a -> assertThat(a.contenido())
                .contains("en Vestuarios.\n\n").doesNotContain("Equipaje")
                .endsWith("🚿 Ducha recomendable después del partido"));
    }
}
