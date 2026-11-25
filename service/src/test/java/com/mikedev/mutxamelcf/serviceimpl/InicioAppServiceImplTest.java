package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.CoordinacionDao;
import com.mikedev.mutxamelcf.dao.PerfilAppDao;
import com.mikedev.mutxamelcf.model.Convocatoria;
import com.mikedev.mutxamelcf.model.ConvocatoriaJugador;
import com.mikedev.mutxamelcf.model.ConvocatoriaPartidoResponse;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipacionResponse;
import com.mikedev.mutxamelcf.model.EquipoCoordinacion;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.PartidoDTO;
import com.mikedev.mutxamelcf.model.ProximosPartidosResponse;
import com.mikedev.mutxamelcf.model.ProximosPartidosResponse.Entrada;
import com.mikedev.mutxamelcf.model.ProximosPartidosResponse.EstadoConvocatoria;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.service.ConvocatoriaPartidoService;
import com.mikedev.mutxamelcf.service.PartidoService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

@ExtendWith(MockitoExtension.class)
class InicioAppServiceImplTest {

    private static final Long USUARIO = 7L;
    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    /** Miércoles 25/11/2026, 18:00. */
    private static final LocalDate HOY = LocalDate.of(2026, 11, 25);

    @Mock
    private PerfilAppDao perfilAppDao;
    @Mock
    private UsuarioAppService usuarioAppService;
    @Mock
    private PartidoService partidoService;
    @Mock
    private ConvocatoriaPartidoService convocatoriaPartidoService;
    @Mock
    private ConvocatoriaDao convocatoriaDao;
    @Mock
    private ConvocatoriaJugadorDao convocatoriaJugadorDao;
    @Mock
    private CoordinacionDao coordinacionDao;

    private InicioAppServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-25T17:00:00Z"), ZONA);

        service = new InicioAppServiceImpl(perfilAppDao, usuarioAppService, partidoService,
                convocatoriaPartidoService, convocatoriaDao, convocatoriaJugadorDao, coordinacionDao, reloj);

        lenient().when(perfilAppDao.obtenerJugadoresPropiosActivos(anyInt())).thenReturn(List.of());
        lenient().when(perfilAppDao.obtenerHijosActivos(anyInt())).thenReturn(List.of());
        lenient().when(perfilAppDao.obtenerEquiposPorUsuario(anyInt())).thenReturn(List.of());
        lenient().when(coordinacionDao.descansosEntre(any(), any())).thenReturn(List.of());
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private void roles(String... codigos) {
        List<RolApp> roles = java.util.Arrays.stream(codigos).map(c -> {
            RolApp rol = new RolApp();
            rol.setCodigo(c);
            return rol;
        }).toList();

        when(usuarioAppService.obtenerRoles(USUARIO.intValue())).thenReturn(roles);
    }

    private static Jugador jugador(long id, String nombre, long equipoId, String equipo) {
        Jugador j = new Jugador();
        j.setId(id);
        j.setNombre(nombre);
        j.setApellidos("García");
        j.setEquipoId(equipoId);
        j.setEquipo(equipo);
        return j;
    }

    private static Equipo equipo(long id, String nombre) {
        Equipo e = new Equipo();
        e.setId(id);
        e.setNombre(nombre);
        return e;
    }

    private static PartidoDTO partido(long id, long equipoId, String equipo, LocalDate dia, String hora) {
        PartidoDTO p = new PartidoDTO();
        p.setId(id);
        p.setEquipoId(equipoId);
        p.setEquipo(equipo);
        p.setRival("Rival " + id);
        p.setDia(Date.valueOf(dia));
        p.setHora(hora);
        return p;
    }

    // ---- jugador -------------------------------------------------------------------------------------------------

    @Test
    void jugadorConSuProximoPartidoConvocadoConCitacionYEquipaje() {
        roles("JUGADOR");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(jugador(100, "Leo", 10, "Alevín A")));

        PartidoDTO proximo = partido(30, 10, "Alevín A", HOY.plusDays(3), "11:00");
        proximo.setConvocado(true);
        PartidoDTO despues = partido(31, 10, "Alevín A", HOY.plusDays(10), "11:00");
        despues.setConvocado(null);
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L))
                .thenReturn(List.of(despues, proximo));

        EquipacionResponse verde = new EquipacionResponse(1L, "Verde", "verde", "negro", "verde", "#00AA00");
        EquipacionResponse blanca = new EquipacionResponse(2L, "Blanca", "blanca", "blanco", "blanca", "#FFFFFF");
        when(convocatoriaPartidoService.obtenerParaJugador(USUARIO, 30L, 100L)).thenReturn(
                new ConvocatoriaPartidoResponse(5L, "Rival 30", HOY.plusDays(3), "11:00", "Els Oms", true, true,
                        "10:15", "Puerta del campo", true,
                        List.of(new ConvocatoriaPartidoResponse.Convocado("Otro", "Niño", 7)), verde, blanca));

        List<Entrada> entradas = service.obtenerProximosPartidos(USUARIO).entradas();

        assertThat(entradas).hasSize(1);
        Entrada e = entradas.get(0);
        assertThat(e.tipo()).isEqualTo("JUGADOR");
        assertThat(e.jugadorId()).isEqualTo(100L);
        assertThat(e.nombreJugador()).isEqualTo("Leo García");
        assertThat(e.equipoId()).isEqualTo(10L);
        assertThat(e.nombreEquipo()).isEqualTo("Alevín A");
        assertThat(e.partido().getId()).isEqualTo(30L);
        assertThat(e.descansa()).isFalse();
        assertThat(e.convocatoria().estado()).isEqualTo("CONVOCADO");
        assertThat(e.convocatoria().horaCitacion()).isEqualTo("10:15");
        assertThat(e.convocatoria().lugarCitacion()).isEqualTo("Puerta del campo");
        assertThat(e.convocatoria().equipaje()).isEqualTo(verde);
        assertThat(e.convocatoria().equipajeMochila()).isEqualTo(blanca);
        assertThat(e.convocatoria().equipoInvita()).isNull();
    }

    @Test
    void nadaDeOtrosJugadoresEnLaRespuesta() {
        roles("JUGADOR");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(jugador(100, "Leo", 10, "Alevín A")));
        PartidoDTO proximo = partido(30, 10, "Alevín A", HOY.plusDays(3), "11:00");
        proximo.setConvocado(true);
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L)).thenReturn(List.of(proximo));
        when(convocatoriaPartidoService.obtenerParaJugador(USUARIO, 30L, 100L)).thenReturn(
                new ConvocatoriaPartidoResponse(5L, "R", HOY, "11:00", "c", true, true, "10:00", "l", true,
                        List.of(new ConvocatoriaPartidoResponse.Convocado("Secreto", "Compañero", 9)), null, null));

        ProximosPartidosResponse respuesta = service.obtenerProximosPartidos(USUARIO);

        // Ni el detalle de la convocatoria con la lista, ni sus nombres, salen en la respuesta.
        assertThat(respuesta.toString()).doesNotContain("Secreto").doesNotContain("Compañero");
        assertThat(java.util.Arrays.stream(EstadoConvocatoria.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)).doesNotContain("convocados");
    }

    @Test
    void noConvocadoAusenciaJustificadaYSinConvocatoria() {
        roles("FAMILIAR");
        when(perfilAppDao.obtenerHijosActivos(7)).thenReturn(List.of(
                jugador(100, "Leo", 10, "Alevín A"),
                jugador(101, "Ana", 11, "Benjamín"),
                jugador(102, "Pau", 12, "Prebenjamín")));

        PartidoDTO noConvocado = partido(30, 10, "Alevín A", HOY.plusDays(1), "11:00");
        noConvocado.setConvocado(false);
        PartidoDTO ausente = partido(31, 11, "Benjamín", HOY.plusDays(2), "11:00");
        ausente.setConvocado(true);
        ausente.setAusenciaJustificada(true);
        PartidoDTO sin = partido(32, 12, "Prebenjamín", HOY.plusDays(3), "11:00");
        sin.setConvocado(null);

        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L)).thenReturn(List.of(noConvocado));
        when(partidoService.obtenerPorEquipoYRangoFechas(11L, HOY, HOY.plusDays(14), 101L)).thenReturn(List.of(ausente));
        when(partidoService.obtenerPorEquipoYRangoFechas(12L, HOY, HOY.plusDays(14), 102L)).thenReturn(List.of(sin));

        List<Entrada> entradas = service.obtenerProximosPartidos(USUARIO).entradas();

        assertThat(entradas).extracting(Entrada::tipo).containsOnly("FAMILIAR");
        assertThat(entradas).extracting(Entrada::nombreJugador).containsExactly("Leo García", "Ana García", "Pau García");
        assertThat(entradas).extracting(e -> e.convocatoria().estado())
                .containsExactly("NO_CONVOCADO", "AUSENCIA_JUSTIFICADA", "SIN_CONVOCATORIA");
        verify(convocatoriaPartidoService, never()).obtenerParaJugador(anyLong(), anyLong(), anyLong());
    }

    @Test
    void familiarConDosHijosEnEquiposDistintosOrdenadosPorFechaYHora() {
        roles("FAMILIAR");
        when(perfilAppDao.obtenerHijosActivos(7)).thenReturn(List.of(
                jugador(100, "Leo", 10, "Alevín A"), jugador(101, "Ana", 11, "Benjamín")));

        PartidoDTO tarde = partido(30, 10, "Alevín A", HOY.plusDays(5), "16:00");
        PartidoDTO temprano = partido(31, 11, "Benjamín", HOY.plusDays(5), "10:00");
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L)).thenReturn(List.of(tarde));
        when(partidoService.obtenerPorEquipoYRangoFechas(11L, HOY, HOY.plusDays(14), 101L)).thenReturn(List.of(temprano));

        List<Entrada> entradas = service.obtenerProximosPartidos(USUARIO).entradas();

        assertThat(entradas).extracting(Entrada::jugadorId).containsExactly(101L, 100L);
        assertThat(entradas).extracting(Entrada::equipoId).containsExactly(11L, 10L);
    }

    @Test
    void jugadorQueEsAdemasFamiliarNoSeRepite() {
        roles("JUGADOR", "FAMILIAR");
        Jugador propio = jugador(100, "Leo", 10, "Alevín A");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(propio));
        // El mismo jugador sale también por la vía de familiar, más un hijo.
        when(perfilAppDao.obtenerHijosActivos(7)).thenReturn(List.of(propio, jugador(101, "Ana", 11, "Benjamín")));
        when(partidoService.obtenerPorEquipoYRangoFechas(eq(10L), any(), any(), eq(100L)))
                .thenReturn(List.of(partido(30, 10, "Alevín A", HOY.plusDays(2), "11:00")));
        when(partidoService.obtenerPorEquipoYRangoFechas(eq(11L), any(), any(), eq(101L)))
                .thenReturn(List.of(partido(31, 11, "Benjamín", HOY.plusDays(3), "11:00")));

        List<Entrada> entradas = service.obtenerProximosPartidos(USUARIO).entradas();

        assertThat(entradas).hasSize(2);
        assertThat(entradas.get(0).tipo()).isEqualTo("JUGADOR");
        assertThat(entradas.get(0).jugadorId()).isEqualTo(100L);
        assertThat(entradas.get(1).tipo()).isEqualTo("FAMILIAR");
        assertThat(entradas.get(1).jugadorId()).isEqualTo(101L);
    }

    @Test
    void invitadoAutorizadoAntesQueElPartidoDeSuEquipoEsSuProximo() {
        roles("JUGADOR");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(jugador(100, "Leo", 10, "Alevín A")));

        PartidoDTO propio = partido(30, 10, "Alevín A", HOY.plusDays(6), "11:00");
        propio.setConvocado(true);
        PartidoDTO invitado = partido(40, 20, "Infantil B", HOY.plusDays(2), "09:30");
        invitado.setInvitado(true);
        invitado.setConvocado(true);
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L))
                .thenReturn(List.of(propio, invitado));
        when(convocatoriaPartidoService.obtenerParaJugador(USUARIO, 40L, 100L)).thenReturn(
                new ConvocatoriaPartidoResponse(6L, "R", HOY, "09:30", "c", true, true, "08:45", "Campo 2", true,
                        List.of(), null, null));

        Entrada e = service.obtenerProximosPartidos(USUARIO).entradas().get(0);

        assertThat(e.partido().getId()).isEqualTo(40L);
        // La entrada sigue siendo la del equipo del jugador.
        assertThat(e.equipoId()).isEqualTo(10L);
        assertThat(e.convocatoria().estado()).isEqualTo("INVITADO");
        assertThat(e.convocatoria().equipoInvita()).isEqualTo("Infantil B");
        assertThat(e.convocatoria().horaCitacion()).isEqualTo("08:45");
        assertThat(e.convocatoria().lugarCitacion()).isEqualTo("Campo 2");
    }

    @Test
    void unPartidoYaEmpezadoOCanceladoNoSale() {
        roles("JUGADOR");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(jugador(100, "Leo", 10, "Alevín A")));

        // Ahora son las 18:00: el de hoy a las 17:30 ya empezó, el de mañana está cancelado.
        PartidoDTO empezado = partido(30, 10, "Alevín A", HOY, "17:30");
        PartidoDTO cancelado = partido(31, 10, "Alevín A", HOY.plusDays(1), "11:00");
        cancelado.setCancelado(true);
        PartidoDTO bueno = partido(32, 10, "Alevín A", HOY.plusDays(4), "11:00");
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L))
                .thenReturn(List.of(empezado, cancelado, bueno));

        assertThat(service.obtenerProximosPartidos(USUARIO).entradas().get(0).partido().getId()).isEqualTo(32L);
    }

    @Test
    void unPartidoDeHoyQueTodaviaNoHaEmpezadoSale() {
        roles("JUGADOR");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(jugador(100, "Leo", 10, "Alevín A")));
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L))
                .thenReturn(List.of(partido(30, 10, "Alevín A", HOY, "19:00")));

        assertThat(service.obtenerProximosPartidos(USUARIO).entradas()).hasSize(1);
    }

    @Test
    void descansaSiLaFfcvDaDescansoYNoHayOtroPartido() {
        roles("JUGADOR");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(jugador(100, "Leo", 10, "Alevín A")));
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L)).thenReturn(List.of());
        when(coordinacionDao.descansosEntre(HOY, HOY.plusDays(14)))
                .thenReturn(List.of(new EquipoCoordinacion(10L, "Alevín A", 9, HOY.plusDays(3))));

        List<Entrada> entradas = service.obtenerProximosPartidos(USUARIO).entradas();

        assertThat(entradas).hasSize(1);
        assertThat(entradas.get(0).descansa()).isTrue();
        assertThat(entradas.get(0).partido()).isNull();
        assertThat(entradas.get(0).convocatoria()).isNull();
        assertThat(entradas.get(0).nombreEquipo()).isEqualTo("Alevín A");
    }

    @Test
    void siHayPartidoEnLosCatorceDiasNoDescansa() {
        roles("JUGADOR");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(jugador(100, "Leo", 10, "Alevín A")));
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L))
                .thenReturn(List.of(partido(30, 10, "Alevín A", HOY.plusDays(9), "11:00")));
        lenient().when(coordinacionDao.descansosEntre(any(), any()))
                .thenReturn(List.of(new EquipoCoordinacion(10L, "Alevín A", 9, HOY.plusDays(3))));

        Entrada e = service.obtenerProximosPartidos(USUARIO).entradas().get(0);

        assertThat(e.descansa()).isFalse();
        assertThat(e.partido().getId()).isEqualTo(30L);
    }

    @Test
    void sinPartidoNiDescansoNoHayEntrada() {
        roles("JUGADOR");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(jugador(100, "Leo", 10, "Alevín A")));
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L)).thenReturn(List.of());

        assertThat(service.obtenerProximosPartidos(USUARIO).entradas()).isEmpty();
    }

    // ---- entrenador ----------------------------------------------------------------------------------------------

    @Test
    void entrenadorDeDosEquiposUnaEntradaPorEquipoConPublicadaOSinConvocatoria() {
        roles("ENTRENADOR");
        when(perfilAppDao.obtenerEquiposPorUsuario(7)).thenReturn(List.of(equipo(10, "Alevín A"), equipo(11, "Benjamín")));

        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14)))
                .thenReturn(List.of(partido(30, 10, "Alevín A", HOY.plusDays(3), "11:00")));
        when(partidoService.obtenerPorEquipoYRangoFechas(11L, HOY, HOY.plusDays(14)))
                .thenReturn(List.of(partido(31, 11, "Benjamín", HOY.plusDays(4), "10:00")));

        Convocatoria publicada = new Convocatoria();
        publicada.setId(5L);
        publicada.setHoraConvocatoria("10:15");
        publicada.setLugarConvocatoria("Els Oms");
        when(convocatoriaDao.obtenerPorPartidoId(30L)).thenReturn(publicada);
        when(convocatoriaDao.obtenerPorPartidoId(31L)).thenReturn(null);
        when(convocatoriaJugadorDao.obtenerPorConvocatoria(5L)).thenReturn(List.of(
                new ConvocatoriaJugador(1L, 5L, 100L), new ConvocatoriaJugador(2L, 5L, 101L),
                new ConvocatoriaJugador(3L, 5L, 102L)));

        List<Entrada> entradas = service.obtenerProximosPartidos(USUARIO).entradas();

        assertThat(entradas).extracting(Entrada::tipo).containsOnly("ENTRENADOR");
        assertThat(entradas).extracting(Entrada::equipoId).containsExactly(10L, 11L);
        assertThat(entradas).allSatisfy(e -> {
            assertThat(e.jugadorId()).isNull();
            assertThat(e.nombreJugador()).isNull();
        });
        assertThat(entradas.get(0).convocatoria().estado()).isEqualTo("PUBLICADA");
        assertThat(entradas.get(0).convocatoria().numeroConvocados()).isEqualTo(3);
        assertThat(entradas.get(1).convocatoria().estado()).isEqualTo("SIN_CONVOCATORIA");
    }

    @Test
    void coordinacionSinEquiposPropiosNoRecibeNingunEquipo() {
        roles("COORDINADOR");

        assertThat(service.obtenerProximosPartidos(USUARIO).entradas()).isEmpty();
        verify(partidoService, never()).obtenerPorEquipoYRangoFechas(anyLong(), any(), any());
    }

    @Test
    void coordinadorQueTambienEntrenaSoloRecibeSusEquipos() {
        roles("COORDINADOR", "ENTRENADOR");
        when(perfilAppDao.obtenerEquiposPorUsuario(7)).thenReturn(List.of(equipo(10, "Alevín A")));
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14)))
                .thenReturn(List.of(partido(30, 10, "Alevín A", HOY.plusDays(3), "11:00")));

        assertThat(service.obtenerProximosPartidos(USUARIO).entradas()).extracting(Entrada::equipoId).containsExactly(10L);
    }

    // ---- límites y errores ---------------------------------------------------------------------------------------

    @Test
    void comoMuchoSeisEntradasOrdenadasPorFechaYHora() {
        roles("FAMILIAR");
        java.util.List<Jugador> hijos = new java.util.ArrayList<>();

        for (int i = 0; i < 8; i++) {
            long equipo = 10 + i;
            hijos.add(jugador(100 + i, "Hijo" + i, equipo, "Equipo " + i));
            // El hijo 0 juega el que más tarde, el 7 el que más pronto.
            when(partidoService.obtenerPorEquipoYRangoFechas(eq(equipo), any(), any(), eq(100L + i)))
                    .thenReturn(List.of(partido(30 + i, equipo, "Equipo " + i, HOY.plusDays(10 - i), "11:00")));
        }

        when(perfilAppDao.obtenerHijosActivos(7)).thenReturn(hijos);

        List<Entrada> entradas = service.obtenerProximosPartidos(USUARIO).entradas();

        assertThat(entradas).hasSize(6);
        assertThat(entradas).extracting(Entrada::jugadorId).containsExactly(107L, 106L, 105L, 104L, 103L, 102L);
    }

    @Test
    void cuentaSinRolesNoDevuelveNada() {
        roles();

        assertThat(service.obtenerProximosPartidos(USUARIO).entradas()).isEmpty();
    }

    @Test
    void sinConvocatoriaEnElDetalleLaCitacionQuedaVacia() {
        roles("JUGADOR");
        when(perfilAppDao.obtenerJugadoresPropiosActivos(7)).thenReturn(List.of(jugador(100, "Leo", 10, "Alevín A")));
        PartidoDTO p = partido(30, 10, "Alevín A", HOY.plusDays(3), "11:00");
        p.setConvocado(true);
        when(partidoService.obtenerPorEquipoYRangoFechas(10L, HOY, HOY.plusDays(14), 100L)).thenReturn(List.of(p));
        when(convocatoriaPartidoService.obtenerParaJugador(USUARIO, 30L, 100L)).thenThrow(new NoSuchElementException());

        EstadoConvocatoria estado = service.obtenerProximosPartidos(USUARIO).entradas().get(0).convocatoria();

        assertThat(estado.estado()).isEqualTo("CONVOCADO");
        assertThat(estado.horaCitacion()).isNull();
    }

    @Test
    void unUsuarioNoValidoEsUnaSecurityException() {
        assertThatThrownBy(() -> service.obtenerProximosPartidos(null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.obtenerProximosPartidos(0L)).isInstanceOf(SecurityException.class);
    }
}
