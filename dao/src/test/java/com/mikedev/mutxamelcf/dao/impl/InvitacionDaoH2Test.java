package com.mikedev.mutxamelcf.dao.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.mikedev.mutxamelcf.dao.EsquemaH2;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.EstadoInvitacion;
import com.mikedev.mutxamelcf.model.InvitacionDetalle;
import com.mikedev.mutxamelcf.model.JugadorInvitable;

/**
 * Las consultas de los jugadores invitados (InvitacionDaoImpl) y las columnas nuevas de CONVOCATORIA_JUGADOR
 * (ConvocatoriaJugadorDaoImpl) sobre H2 en modo Oracle.
 *
 * <p>Equipos: 1 = Cadete A (destino), 2 = Cadete B (origen del invitado), 3 = Infantil A. Partidos: 10 = Cadete A
 * el 14/11/2026 (con convocatoria 100), 11 = Cadete B el mismo día (con convocatoria 101), 12 = Cadete A otro día.
 */
class InvitacionDaoH2Test {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 14);

    private JdbcTemplate jdbc;
    private ConvocatoriaJugadorDaoImpl convocatoriaJugadorDao;
    private InvitacionDaoImpl dao;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", ""));

        jdbc.execute("CREATE TABLE EQUIPO (ID NUMBER PRIMARY KEY, NOMBRE VARCHAR2(100))");
        jdbc.execute("""
                CREATE TABLE PARTIDOS (
                    ID NUMBER PRIMARY KEY, EQUIPO_ID NUMBER, DIA DATE, HORA VARCHAR2(10), RIVAL VARCHAR2(150),
                    CANCELADO NUMBER(1) DEFAULT 0)""");
        jdbc.execute("""
                CREATE TABLE CONVOCATORIAS (
                    ID NUMBER PRIMARY KEY, EQUIPO_ID NUMBER, PARTIDO_ID NUMBER, USUARIO_ENTRENADOR_ID NUMBER)""");

        EsquemaH2.crearPersonal(jdbc);
        EsquemaH2.crear(jdbc);
        EsquemaH2.crearPlantillas(jdbc);

        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (1, 'Cadete A')");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (2, 'Cadete B')");
        jdbc.update("INSERT INTO EQUIPO (ID, NOMBRE) VALUES (3, 'Infantil A')");

        partido(10, 1, DIA, "11:00", "Pego", false);
        partido(11, 2, DIA, "09:30", "Denia", false);
        partido(12, 1, DIA.plusDays(7), "11:00", "Elche", false);

        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID) VALUES (100, 1, 10, 7)");
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID) VALUES (101, 2, 11, 8)");

        // Jugadores 1 a 5, de Cadete B (los que usan los tests por su id).
        for (int i = 1; i <= 5; i++) {
            jugador("J" + i, "Apellido" + i, i, 2, false);
        }

        convocatoriaJugadorDao = new ConvocatoriaJugadorDaoImpl(jdbc);
        dao = new InvitacionDaoImpl(jdbc);
    }

    private void partido(long id, long equipo, LocalDate dia, String hora, String rival, boolean cancelado) {
        jdbc.update("INSERT INTO PARTIDOS (ID, EQUIPO_ID, DIA, HORA, RIVAL, CANCELADO) VALUES (?, ?, ?, ?, ?, ?)",
                id, equipo, java.sql.Date.valueOf(dia), hora, rival, cancelado ? 1 : 0);
    }

    private long jugador(String nombre, String apellidos, Integer dorsal, long equipoId, boolean baja) {
        jdbc.update("INSERT INTO JUGADORES (NOMBRE, APELLIDOS, DORSAL, EQUIPO_ID, FECHA_BAJA) VALUES (?, ?, ?, ?, ?)",
                nombre, apellidos, dorsal, equipoId, baja ? java.sql.Date.valueOf("2026-09-01") : null);

        return jdbc.queryForObject("SELECT MAX(ID) FROM JUGADORES", Long.class);
    }

    private void temporada(long jugadorId, long temporadaId, long equipoId, String estado, int definitivo) {
        jdbc.update("INSERT INTO JUGADOR_TEMPORADA (JUGADOR_ID, TEMPORADA_ID, EQUIPO_ID, ESTADO, EQUIPO_DEFINITIVO)"
                + " VALUES (?, ?, ?, ?, ?)", jugadorId, temporadaId, equipoId, estado, definitivo);
    }

    private ConvocatoriaJugador propio(long convocatoria, long jugador) {
        return convocatoriaJugadorDao.guardar(new ConvocatoriaJugador(null, convocatoria, jugador));
    }

    private ConvocatoriaJugador invitado(long convocatoria, long jugador, long origen, EstadoInvitacion estado) {
        ConvocatoriaJugador cj = new ConvocatoriaJugador(null, convocatoria, jugador);
        cj.setEquipoOrigenId(origen);
        cj.setEstadoInvitacion(estado);
        cj.setFechaSolicitud(LocalDateTime.of(2026, 11, 10, 9, 0));
        cj.setUsuarioInvitaId(7L);

        return convocatoriaJugadorDao.guardar(cj);
    }

    // ------------------------------------------------------------------ ConvocatoriaJugadorDao

    @Test
    void guardaYLeeTodosLosCamposDeLaInvitacion() {
        long ana = jugador("Ana", "Pérez", 7, 2, false);

        ConvocatoriaJugador cj = new ConvocatoriaJugador(null, 100L, ana);
        cj.setEquipoOrigenId(2L);
        cj.setEstadoInvitacion(EstadoInvitacion.PENDIENTE_ENTRENADOR);
        cj.setFechaSolicitud(LocalDateTime.of(2026, 11, 10, 9, 0));
        cj.setUsuarioInvitaId(7L);
        cj.setUsuarioCoordinadorId(3L);
        cj.setFechaCoordinacion(LocalDateTime.of(2026, 11, 10, 10, 0));
        cj.setFechaPeticionEntrenador(LocalDateTime.of(2026, 11, 10, 10, 1));
        cj.setAviso12hEnviado(true);
        cj.setMotivoRechazo("x");

        convocatoriaJugadorDao.guardar(cj);

        ConvocatoriaJugador leido = convocatoriaJugadorDao.obtenerPorId(cj.getId());

        assertThat(leido.isInvitado()).isTrue();
        assertThat(leido.getEquipoOrigenId()).isEqualTo(2L);
        assertThat(leido.getEstadoInvitacion()).isEqualTo(EstadoInvitacion.PENDIENTE_ENTRENADOR);
        assertThat(leido.getFechaSolicitud()).isEqualTo(LocalDateTime.of(2026, 11, 10, 9, 0));
        assertThat(leido.getUsuarioInvitaId()).isEqualTo(7L);
        assertThat(leido.getUsuarioCoordinadorId()).isEqualTo(3L);
        assertThat(leido.getFechaCoordinacion()).isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 0));
        assertThat(leido.getFechaPeticionEntrenador()).isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 1));
        assertThat(leido.isAviso12hEnviado()).isTrue();
        assertThat(leido.getFechaAutorizacion()).isNull();
        assertThat(leido.getMotivoRechazo()).isEqualTo("x");
    }

    @Test
    void unJugadorDelPropioEquipoNoTieneNadaDeInvitacion() {
        ConvocatoriaJugador cj = propio(100, 1);

        ConvocatoriaJugador leido = convocatoriaJugadorDao.obtenerPorId(cj.getId());

        assertThat(leido.isInvitado()).isFalse();
        assertThat(leido.getEquipoOrigenId()).isNull();
        assertThat(leido.getEstadoInvitacion()).isNull();
        assertThat(leido.isAviso12hEnviado()).isFalse();
        assertThat(leido.isConvocadoEfectivo()).isTrue();
    }

    @Test
    void obtenerPorConvocatoriaSoloTraeLosPropiosYLosInvitadosAutorizados() {
        propio(100, 1);
        invitado(100, 2, 2, EstadoInvitacion.AUTORIZADO);
        invitado(100, 3, 2, EstadoInvitacion.PENDIENTE_COORDINACION);
        invitado(100, 4, 2, EstadoInvitacion.PENDIENTE_ENTRENADOR);
        invitado(100, 5, 2, EstadoInvitacion.RECHAZADO);

        assertThat(convocatoriaJugadorDao.obtenerPorConvocatoria(100L))
                .extracting(ConvocatoriaJugador::getJugadorId).containsExactly(1L, 2L);
        assertThat(convocatoriaJugadorDao.obtenerTodasPorConvocatoria(100L))
                .extracting(ConvocatoriaJugador::getJugadorId).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void actualizarInvitacionGuardaElEstadoYLasFechas() {
        ConvocatoriaJugador cj = invitado(100, 2, 2, EstadoInvitacion.PENDIENTE_COORDINACION);

        cj.setEstadoInvitacion(EstadoInvitacion.AUTORIZADO);
        cj.setUsuarioCoordinadorId(3L);
        cj.setFechaAutorizacion(LocalDateTime.of(2026, 11, 11, 8, 0));

        convocatoriaJugadorDao.actualizarInvitacion(cj);

        ConvocatoriaJugador leido = convocatoriaJugadorDao.obtenerPorConvocatoriaYJugador(100L, 2L);

        assertThat(leido.getEstadoInvitacion()).isEqualTo(EstadoInvitacion.AUTORIZADO);
        assertThat(leido.getUsuarioCoordinadorId()).isEqualTo(3L);
        assertThat(leido.getFechaAutorizacion()).isEqualTo(LocalDateTime.of(2026, 11, 11, 8, 0));
        assertThat(leido.getEquipoOrigenId()).isEqualTo(2L);
    }

    @Test
    void unInvitadoNoPuedeQuedarSinEstado() {
        ConvocatoriaJugador cj = new ConvocatoriaJugador(null, 100L, 2L);
        cj.setEquipoOrigenId(2L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> convocatoriaJugadorDao.guardar(cj))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------ InvitacionDao

    @Test
    void obtenerDetalleTraeJugadorEquiposYPartido() {
        long ana = jugador("Ana", "Pérez", 7, 2, false);
        ConvocatoriaJugador cj = invitado(100, ana, 2, EstadoInvitacion.PENDIENTE_COORDINACION);

        InvitacionDetalle detalle = dao.obtenerDetalle(cj.getId());

        assertThat(detalle.jugador()).isEqualTo("Ana Pérez");
        assertThat(detalle.equipoOrigen()).isEqualTo("Cadete B");
        assertThat(detalle.equipoDestino()).isEqualTo("Cadete A");
        assertThat(detalle.equipoDestinoId()).isEqualTo(1L);
        assertThat(detalle.partidoId()).isEqualTo(10L);
        assertThat(detalle.dia()).isEqualTo(DIA);
        assertThat(detalle.hora()).isEqualTo("11:00");
        assertThat(detalle.rival()).isEqualTo("Pego");
        assertThat(detalle.cancelado()).isFalse();
        // La fila no guarda USUARIO_INVITA_ID en este caso concreto: lo trae igual y, sin él, el de la convocatoria.
        assertThat(detalle.usuarioInvitaId()).isEqualTo(7L);
        assertThat(dao.obtenerDetalle(propio(100, 1).getId())).isNull();
    }

    @Test
    void sinUsuarioQueInvitaSeUsaElDeLaConvocatoria() {
        ConvocatoriaJugador cj = new ConvocatoriaJugador(null, 101L, 2L);
        cj.setEquipoOrigenId(1L);
        cj.setEstadoInvitacion(EstadoInvitacion.PENDIENTE_COORDINACION);
        cj.setFechaSolicitud(LocalDateTime.of(2026, 11, 10, 9, 0));
        convocatoriaJugadorDao.guardar(cj);

        assertThat(dao.obtenerDetalle(cj.getId()).usuarioInvitaId()).isEqualTo(8L);
    }

    @Test
    void pendientesIncluyeLasDosPendientesDePartidosNoCanceladosDesdeHoy() {
        invitado(100, 2, 2, EstadoInvitacion.PENDIENTE_COORDINACION);
        invitado(100, 3, 2, EstadoInvitacion.PENDIENTE_ENTRENADOR);
        invitado(100, 4, 2, EstadoInvitacion.AUTORIZADO);
        invitado(100, 5, 2, EstadoInvitacion.RECHAZADO);

        assertThat(dao.pendientes(DIA)).extracting(InvitacionDetalle::jugadorId).containsExactly(2L, 3L);
        assertThat(dao.pendientes(DIA.plusDays(1))).isEmpty();

        jdbc.update("UPDATE PARTIDOS SET CANCELADO = 1 WHERE ID = 10");

        assertThat(dao.pendientes(DIA)).isEmpty();
    }

    @Test
    void delJugadorEnDiaIgnoraLasRechazadasLaExcluidaYOtrosDias() {
        ConvocatoriaJugador autorizada = invitado(100, 2, 2, EstadoInvitacion.AUTORIZADO);
        invitado(101, 2, 1, EstadoInvitacion.PENDIENTE_COORDINACION);
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID) VALUES (102, 1, 12, 7)");
        invitado(102, 2, 2, EstadoInvitacion.AUTORIZADO);
        invitado(100, 3, 2, EstadoInvitacion.RECHAZADO);

        assertThat(dao.delJugadorEnDia(2L, DIA, null)).hasSize(2);
        assertThat(dao.delJugadorEnDia(2L, DIA, autorizada.getId())).hasSize(1)
                .allMatch(i -> i.estado() == EstadoInvitacion.PENDIENTE_COORDINACION);
        assertThat(dao.delJugadorEnDia(3L, DIA, null)).isEmpty();
        assertThat(dao.delJugadorEnDia(2L, DIA.plusDays(7), null)).hasSize(1);
    }

    @Test
    void autorizadasDelJugadorSoloTraeLasAutorizadasNoCanceladasEnElRango() {
        invitado(100, 2, 2, EstadoInvitacion.AUTORIZADO);
        invitado(101, 2, 1, EstadoInvitacion.PENDIENTE_ENTRENADOR);
        jdbc.update("INSERT INTO CONVOCATORIAS (ID, EQUIPO_ID, PARTIDO_ID, USUARIO_ENTRENADOR_ID) VALUES (102, 1, 12, 7)");
        invitado(102, 2, 2, EstadoInvitacion.AUTORIZADO);

        assertThat(dao.autorizadasDelJugador(2L, DIA, DIA)).extracting(InvitacionDetalle::partidoId)
                .containsExactly(10L);
        assertThat(dao.autorizadasDelJugador(2L, DIA, DIA.plusDays(7))).extracting(InvitacionDetalle::partidoId)
                .containsExactly(10L, 12L);

        jdbc.update("UPDATE PARTIDOS SET CANCELADO = 1 WHERE ID = 10");

        assertThat(dao.autorizadasDelJugador(2L, DIA, DIA.plusDays(7))).extracting(InvitacionDetalle::partidoId)
                .containsExactly(12L);
    }

    @Test
    void vencidasSinAvisoSoloTraeLasPendientesDeEntrenadorAntiguasSinAvisar() {
        LocalDateTime hace13h = LocalDateTime.of(2026, 11, 10, 8, 0);

        ConvocatoriaJugador vencida = invitado(100, 2, 2, EstadoInvitacion.PENDIENTE_ENTRENADOR);
        vencida.setFechaPeticionEntrenador(hace13h);
        convocatoriaJugadorDao.actualizarInvitacion(vencida);

        ConvocatoriaJugador yaAvisada = invitado(100, 3, 2, EstadoInvitacion.PENDIENTE_ENTRENADOR);
        yaAvisada.setFechaPeticionEntrenador(hace13h);
        yaAvisada.setAviso12hEnviado(true);
        convocatoriaJugadorDao.actualizarInvitacion(yaAvisada);

        ConvocatoriaJugador reciente = invitado(100, 4, 2, EstadoInvitacion.PENDIENTE_ENTRENADOR);
        reciente.setFechaPeticionEntrenador(hace13h.plusHours(13));
        convocatoriaJugadorDao.actualizarInvitacion(reciente);

        ConvocatoriaJugador autorizada = invitado(100, 5, 2, EstadoInvitacion.AUTORIZADO);
        autorizada.setFechaPeticionEntrenador(hace13h);
        convocatoriaJugadorDao.actualizarInvitacion(autorizada);

        assertThat(dao.vencidasSinAviso(hace13h.plusHours(12))).extracting(InvitacionDetalle::jugadorId)
                .containsExactly(2L);
    }

    @Test
    void partidosDelEquipoEnDiaIgnoraCanceladosYOtrosDias() {
        assertThat(dao.partidosDelEquipoEnDia(1L, DIA)).extracting(p -> p.id()).containsExactly(10L);
        assertThat(dao.partidosDelEquipoEnDia(2L, DIA)).extracting(p -> p.rival()).containsExactly("Denia");
        assertThat(dao.partidosDelEquipoEnDia(3L, DIA)).isEmpty();

        jdbc.update("UPDATE PARTIDOS SET CANCELADO = 1 WHERE ID = 11");

        assertThat(dao.partidosDelEquipoEnDia(2L, DIA)).isEmpty();
    }

    @Test
    void filasPropiasDelJugadorEnDiaSoloSonLasDelPropioEquipoEseDia() {
        propio(101, 2);
        // Es invitado en el partido del otro equipo: no cuenta como convocado de su propio equipo.
        invitado(100, 2, 2, EstadoInvitacion.AUTORIZADO);

        assertThat(dao.filasPropiasDelJugadorEnDia(2L, 2L, DIA)).extracting(ConvocatoriaJugador::getConvocatoriaId)
                .containsExactly(101L);
        assertThat(dao.filasPropiasDelJugadorEnDia(1L, 2L, DIA)).isEmpty();
        assertThat(dao.filasPropiasDelJugadorEnDia(2L, 2L, DIA.plusDays(1))).isEmpty();

        jdbc.update("UPDATE PARTIDOS SET CANCELADO = 1 WHERE ID = 11");

        assertThat(dao.filasPropiasDelJugadorEnDia(2L, 2L, DIA)).isEmpty();
    }

    @Test
    void jugadoresInvitablesSonLosConfirmadosConEquipoDefinitivoYSinBaja() {
        long confirmado = jugador("Ana", "Zapata", 9, 2, false);
        long confirmadoSinDorsal = jugador("Biel", "Alós", null, 2, false);
        long prueba = jugador("Carla", "Gil", 3, 2, false);
        long baja = jugador("Dani", "Ruiz", 4, 2, true);
        long sinDefinitivo = jugador("Eva", "Sanz", 5, 2, false);
        long otroEquipo = jugador("Fran", "Mas", 6, 3, false);
        long otraTemporada = jugador("Gema", "Lis", 8, 2, false);
        long sinFilaDeTemporada = jugador("Hugo", "Pla", 10, 2, false);

        temporada(confirmado, 5, 2, "CONFIRMADO", 1);
        temporada(confirmadoSinDorsal, 5, 2, "CONFIRMADO", 1);
        temporada(prueba, 5, 2, "PRUEBA", 1);
        temporada(baja, 5, 2, "CONFIRMADO", 1);
        temporada(sinDefinitivo, 5, 2, "CONFIRMADO", 0);
        temporada(otroEquipo, 5, 3, "CONFIRMADO", 1);
        temporada(otraTemporada, 4, 2, "CONFIRMADO", 1);

        List<JugadorInvitable> invitables = dao.jugadoresInvitables(2L, 5L);

        assertThat(invitables).extracting(JugadorInvitable::id).containsExactly(confirmado, confirmadoSinDorsal);
        assertThat(invitables.get(0).nombre()).isEqualTo("Ana");
        assertThat(invitables.get(0).dorsal()).isEqualTo(9);
        assertThat(invitables.get(1).dorsal()).isNull();
        assertThat(sinFilaDeTemporada).isPositive();
    }
}
