package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorTemporadaDao;
import com.mikedev.mutxamelcf.dao.TemporadaDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaEntrenamientoDao;
import com.mikedev.mutxamelcf.dao.JustificacionFaltaPartidoDao;
import com.mikedev.mutxamelcf.dao.PartidoEstadisticaJugadorDao;
import com.mikedev.mutxamelcf.dao.PartidoLiveEventoDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.FamiliarJugadorDTO;
import com.mikedev.mutxamelcf.model.HistorialJugadorDTO;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorPublicDTO;
import com.mikedev.mutxamelcf.model.JugadorTemporada;
import com.mikedev.mutxamelcf.model.Temporada;
import com.mikedev.mutxamelcf.service.CuotaJugadorService;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;

@ExtendWith(MockitoExtension.class)
class JugadorServiceImplTest {

    @Mock
    private JugadorDao jugadorDao;

    @Mock
    private FamiliarJugadorService familiarJugadorService;

    @Mock
    private UsuarioAppVinculoDao usuarioAppVinculoDao;

    @Mock
    private CuotaJugadorService cuotaJugadorService;

    @Mock
    private ConvocatoriaJugadorDao convocatoriaJugadorDao;

    @Mock
    private EntrenamientoAsistenciaDao entrenamientoAsistenciaDao;

    @Mock
    private PartidoEstadisticaJugadorDao partidoEstadisticaJugadorDao;

    @Mock
    private PartidoLiveEventoDao partidoLiveEventoDao;

    @Mock
    private JustificacionFaltaEntrenamientoDao justificacionFaltaEntrenamientoDao;

    @Mock
    private JustificacionFaltaPartidoDao justificacionFaltaPartidoDao;

    @Mock
    private EquipoDao equipoDao;

    @Mock
    private TemporadaDao temporadaDao;

    @Mock
    private JugadorTemporadaDao jugadorTemporadaDao;

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneId.of("Europe/Madrid"));

    private JugadorServiceImpl jugadorService;

    @BeforeEach
    void setUp() {
        jugadorService = new JugadorServiceImpl(jugadorDao, familiarJugadorService, usuarioAppVinculoDao,
                cuotaJugadorService, convocatoriaJugadorDao, entrenamientoAsistenciaDao,
                partidoEstadisticaJugadorDao, partidoLiveEventoDao, justificacionFaltaEntrenamientoDao,
                justificacionFaltaPartidoDao, equipoDao, temporadaDao, jugadorTemporadaDao, RELOJ);
    }

    private static Temporada temporadaActiva() {
        Temporada t = new Temporada();
        t.setId(3L);
        t.setNombre("2026-27");
        return t;
    }

    private static Jugador jugadorEnEquipo() {
        Jugador j = new Jugador();
        j.setId(10L);
        j.setNombre("Pablo");
        j.setEquipoId(5L);
        j.setEquipo("Cadete A");
        j.setCategoria("Cadete");
        j.setDorsal(9);
        return j;
    }

    @Test
    void guardarJugadorActualizaLaFilaDeLaTemporadaActiva() {
        when(jugadorDao.guardarJugador(any(Jugador.class))).thenReturn(true);
        when(jugadorDao.obtenerPorId(10L)).thenReturn(jugadorEnEquipo());
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporadaActiva());

        JugadorDTO dto = new JugadorDTO();
        dto.setId(10L);
        dto.setEquipoId(5L);

        assertThat(jugadorService.guardarJugador(dto)).isTrue();

        ArgumentCaptor<JugadorTemporada> fila = ArgumentCaptor.forClass(JugadorTemporada.class);
        verify(jugadorTemporadaDao).guardar(fila.capture());
        assertThat(fila.getValue().getJugadorId()).isEqualTo(10L);
        assertThat(fila.getValue().getTemporadaId()).isEqualTo(3L);
        assertThat(fila.getValue().getEquipoId()).isEqualTo(5L);
        assertThat(fila.getValue().getEquipoNombre()).isEqualTo("Cadete A");
        assertThat(fila.getValue().getCategoria()).isEqualTo("Cadete");
        assertThat(fila.getValue().getDorsal()).isEqualTo(9);
        assertThat(fila.getValue().isBaja()).isFalse();
    }

    @Test
    void guardarJugadorSinTemporadaActivaNoToscaLaTabla() {
        when(jugadorDao.guardarJugador(any(Jugador.class))).thenReturn(true);
        when(jugadorDao.obtenerPorId(10L)).thenReturn(jugadorEnEquipo());
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(null);

        JugadorDTO dto = new JugadorDTO();
        dto.setId(10L);

        assertThat(jugadorService.guardarJugador(dto)).isTrue();

        verify(jugadorTemporadaDao, never()).guardar(any());
    }

    @Test
    void editarUnJugadorDeBajaNoLeDaEquipo() {
        Jugador deBaja = new Jugador();
        deBaja.setId(10L);
        deBaja.setFechaBaja(LocalDate.of(2026, 6, 30));
        when(jugadorDao.obtenerPorId(10L)).thenReturn(deBaja);
        when(jugadorDao.guardarJugador(any(Jugador.class))).thenReturn(true);

        JugadorDTO dto = new JugadorDTO();
        dto.setId(10L);
        dto.setEquipoId(5L);
        dto.setEquipo("Cadete A");
        dto.setCategoria("Cadete");
        jugadorService.guardarJugador(dto);

        ArgumentCaptor<Jugador> guardado = ArgumentCaptor.forClass(Jugador.class);
        verify(jugadorDao).guardarJugador(guardado.capture());
        assertThat(guardado.getValue().getEquipoId()).isNull();
        assertThat(guardado.getValue().getEquipo()).isNull();
    }

    @Test
    void darDeBajaPonLaFechaDeHoyYMarcaLaFilaDeLaTemporadaActiva() {
        when(jugadorDao.obtenerPorId(10L)).thenReturn(jugadorEnEquipo());
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporadaActiva());

        jugadorService.darDeBaja(10L);

        ArgumentCaptor<JugadorTemporada> fila = ArgumentCaptor.forClass(JugadorTemporada.class);
        verify(jugadorTemporadaDao).guardar(fila.capture());
        assertThat(fila.getValue().isBaja()).isTrue();
        // La fila conserva el equipo en el que estaba antes de la baja
        assertThat(fila.getValue().getEquipoId()).isEqualTo(5L);
        verify(jugadorDao).darDeBaja(10L, LocalDate.of(2026, 10, 27));
    }

    @Test
    void darDeBajaAUnJugadorYaDeBajaFalla() {
        Jugador deBaja = jugadorEnEquipo();
        deBaja.setFechaBaja(LocalDate.of(2026, 6, 30));
        when(jugadorDao.obtenerPorId(10L)).thenReturn(deBaja);

        assertThatThrownBy(() -> jugadorService.darDeBaja(10L)).isInstanceOf(IllegalStateException.class);

        verify(jugadorDao, never()).darDeBaja(any(), any());
    }

    @Test
    void reactivarAsignaElEquipoYDesmarcaLaBajaEnLaTemporada() {
        Jugador deBaja = new Jugador();
        deBaja.setId(10L);
        deBaja.setFechaBaja(LocalDate.of(2026, 6, 30));
        Equipo equipo = new Equipo();
        equipo.setId(5L);
        equipo.setNombre("Cadete A");
        equipo.setCategoria("Cadete");
        equipo.setDeporte("F");
        when(jugadorDao.obtenerPorId(10L)).thenReturn(deBaja, jugadorEnEquipo());
        when(equipoDao.obtenerEquipoPorId(5L)).thenReturn(equipo);
        when(temporadaDao.obtenerTemporadaActiva()).thenReturn(temporadaActiva());
        when(jugadorTemporadaDao.obtener(10L, 3L)).thenReturn(null);

        jugadorService.reactivar(10L, 5L);

        verify(jugadorDao).reactivar(10L, 5L, "Cadete A", "Cadete", "F");
        ArgumentCaptor<JugadorTemporada> fila = ArgumentCaptor.forClass(JugadorTemporada.class);
        verify(jugadorTemporadaDao).guardar(fila.capture());
        assertThat(fila.getValue().isBaja()).isFalse();
        assertThat(fila.getValue().getEquipoId()).isEqualTo(5L);
    }

    @Test
    void reactivarSinEquipoFalla() {
        Jugador deBaja = new Jugador();
        deBaja.setId(10L);
        deBaja.setFechaBaja(LocalDate.of(2026, 6, 30));
        when(jugadorDao.obtenerPorId(10L)).thenReturn(deBaja);

        assertThatThrownBy(() -> jugadorService.reactivar(10L, null)).isInstanceOf(IllegalArgumentException.class);

        verify(jugadorDao, never()).reactivar(any(), any(), any(), any(), any());
    }

    @Test
    void elHistorialSeDevuelveSinDatosDelJugador() {
        JugadorTemporada fila = new JugadorTemporada();
        fila.setTemporadaNombre("2025-26");
        fila.setEquipoNombre("Infantil A");
        fila.setCategoria("Infantil");
        fila.setDorsal(4);
        fila.setBaja(false);
        when(jugadorTemporadaDao.listarPorJugador(10L)).thenReturn(List.of(fila));

        assertThat(jugadorService.obtenerHistorial(10L)).containsExactly(
                new HistorialJugadorDTO("2025-26", "Infantil A", "Infantil", 4, false));
    }

    @Test
    void guardarJugadorDelegaEnElDao() {
        when(jugadorDao.guardarJugador(any(Jugador.class))).thenReturn(true);

        JugadorDTO dto = new JugadorDTO();
        dto.setNombre("Juan");

        assertThat(jugadorService.guardarJugador(dto)).isTrue();
    }

    @Test
    void obtenerJugadorPorIdCodificaLaFotoEnBase64() {
        Jugador jugador = new Jugador();
        jugador.setId(1L);
        jugador.setNombre("Juan");
        jugador.setFoto(new byte[] { 1, 2, 3 });

        when(jugadorDao.obtenerPorId(1L)).thenReturn(jugador);

        JugadorDTO resultado = jugadorService.obtenerJugadorPorId(1L);

        assertThat(resultado.getFotoBase64()).isEqualTo(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }));
    }

    @Test
    void obtenerJugadorPorIdSinFotoDejaFotoBase64Null() {
        Jugador jugador = new Jugador();
        jugador.setId(1L);
        jugador.setFoto(null);

        when(jugadorDao.obtenerPorId(1L)).thenReturn(jugador);

        JugadorDTO resultado = jugadorService.obtenerJugadorPorId(1L);

        assertThat(resultado.getFotoBase64()).isNull();
    }

    @Test
    void obtenerJugadorPorIdDevuelveNullCuandoNoExiste() {
        when(jugadorDao.obtenerPorId(99L)).thenReturn(null);

        assertThat(jugadorService.obtenerJugadorPorId(99L)).isNull();
    }

    @Test
    void eliminarJugadorLanzaExcepcionSiTieneFamiliaresVinculados() {
        when(familiarJugadorService.obtenerFamiliaresDeJugador(1L)).thenReturn(List.of(new FamiliarJugadorDTO()));

        assertThatThrownBy(() -> jugadorService.eliminarJugador(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("familiares vinculados");

        verify(jugadorDao, never()).eliminar(any());
    }

    @Test
    void eliminarJugadorLanzaExcepcionSiTieneCuentaDeApp() {
        when(familiarJugadorService.obtenerFamiliaresDeJugador(1L)).thenReturn(List.of());
        when(usuarioAppVinculoDao.jugadorTieneCuenta(1L)).thenReturn(true);

        assertThatThrownBy(() -> jugadorService.eliminarJugador(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cuenta de la app");
    }

    @Test
    void eliminarJugadorBorraCuandoNoTieneDependencias() {
        when(familiarJugadorService.obtenerFamiliaresDeJugador(1L)).thenReturn(List.of());
        when(usuarioAppVinculoDao.jugadorTieneCuenta(1L)).thenReturn(false);
        when(cuotaJugadorService.obtenerPorJugador(1L)).thenReturn(List.of());
        when(convocatoriaJugadorDao.existePorJugador(1L)).thenReturn(false);
        when(entrenamientoAsistenciaDao.existePorJugador(1L)).thenReturn(false);

        jugadorService.eliminarJugador(1L);

        verify(jugadorDao).eliminar(1L);
    }

    @Test
    void obtenerJugadoresPorCategoriaDelegaEnElDao() {
        Jugador jugador = new Jugador();
        jugador.setNombre("Juan");
        when(jugadorDao.obtenerTodosPorCategoria("SENIOR")).thenReturn(List.of(jugador));

        assertThat(jugadorService.obtenerJugadoresPorCategoria("SENIOR")).hasSize(1);
    }

    @Test
    void obtenerJugadoresPorEquipoDelegaEnElDao() {
        Jugador jugador = new Jugador();
        when(jugadorDao.obtenerTodosPorNombreEquipo("Senior A")).thenReturn(List.of(jugador));

        assertThat(jugadorService.obtenerJugadoresPorEquipo("Senior A")).hasSize(1);
    }

    @Test
    void obtenerTodosDelegaEnElDao() {
        when(jugadorDao.obtenerTodos()).thenReturn(List.of(new Jugador()));

        assertThat(jugadorService.obtenerTodos()).hasSize(1);
    }

    @Test
    void obtenerJugadoresPublicosPorEquipoMapeaCamposPublicosYMiniatura() {
        Jugador jugador = new Jugador();
        jugador.setId(1L);
        jugador.setNombre("Juan");
        jugador.setApellidos("Perez");
        jugador.setCategoria("SENIOR");
        jugador.setEquipo("Senior A");
        jugador.setDeporte("FUTBOL");
        jugador.setDorsal(9);
        jugador.setPosicion("DELANTERO");
        jugador.setFoto(null);

        when(jugadorDao.obtenerTodosPorNombreEquipo("Senior A")).thenReturn(List.of(jugador));

        List<JugadorPublicDTO> resultado = jugadorService.obtenerJugadoresPublicosPorEquipo("Senior A");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Juan");
        assertThat(resultado.get(0).getDorsal()).isEqualTo(9);
    }
}
