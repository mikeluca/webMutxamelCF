package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ConvocatoriaJugadorDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoAsistenciaDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.FamiliarJugadorDTO;
import com.mikedev.mutxamelcf.model.Jugador;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorPublicDTO;
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

    private JugadorServiceImpl jugadorService;

    @BeforeEach
    void setUp() {
        jugadorService = new JugadorServiceImpl(jugadorDao, familiarJugadorService, usuarioAppVinculoDao,
                cuotaJugadorService, convocatoriaJugadorDao, entrenamientoAsistenciaDao);
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
        when(jugadorDao.obtenerTodosPorEquipo("Senior A")).thenReturn(List.of(jugador));

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

        when(jugadorDao.obtenerTodosPorEquipo("Senior A")).thenReturn(List.of(jugador));

        List<JugadorPublicDTO> resultado = jugadorService.obtenerJugadoresPublicosPorEquipo("Senior A");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Juan");
        assertThat(resultado.get(0).getDorsal()).isEqualTo(9);
    }
}
