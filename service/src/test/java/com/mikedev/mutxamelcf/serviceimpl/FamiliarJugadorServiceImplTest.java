package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.FamiliarDao;
import com.mikedev.mutxamelcf.dao.FamiliarJugadorDao;
import com.mikedev.mutxamelcf.dao.JugadorDao;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.FamiliarJugador;
import com.mikedev.mutxamelcf.model.FamiliarJugadorDTO;
import com.mikedev.mutxamelcf.model.Jugador;

@ExtendWith(MockitoExtension.class)
class FamiliarJugadorServiceImplTest {

    @Mock
    private FamiliarJugadorDao familiarJugadorDao;

    @Mock
    private FamiliarDao familiarDao;

    @Mock
    private JugadorDao jugadorDao;

    private FamiliarJugadorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FamiliarJugadorServiceImpl(familiarJugadorDao, familiarDao, jugadorDao);
    }

    @Test
    void guardarFamiliarJugadorDevuelveFalseSiEsNull() {
        assertThat(service.guardarFamiliarJugador(null)).isFalse();
    }

    @Test
    void guardarFamiliarJugadorDevuelveFalseSiFaltanDatos() {
        FamiliarJugadorDTO dto = new FamiliarJugadorDTO();
        dto.setFamiliarId(1L);
        // sin jugadorId

        assertThat(service.guardarFamiliarJugador(dto)).isFalse();
        verify(familiarJugadorDao, never()).guardarFamiliarJugador(any());
    }

    @Test
    void guardarFamiliarJugadorDevuelveFalseSiLaRelacionYaExiste() {
        FamiliarJugadorDTO dto = new FamiliarJugadorDTO();
        dto.setFamiliarId(1L);
        dto.setJugadorId(2L);

        when(familiarJugadorDao.existeRelacion(1L, 2L)).thenReturn(true);

        assertThat(service.guardarFamiliarJugador(dto)).isFalse();
        verify(familiarJugadorDao, never()).guardarFamiliarJugador(any());
    }

    @Test
    void guardarFamiliarJugadorNoCompruebaDuplicadosAlEditar() {
        FamiliarJugadorDTO dto = new FamiliarJugadorDTO();
        dto.setId(10L);
        dto.setFamiliarId(1L);
        dto.setJugadorId(2L);

        when(familiarJugadorDao.guardarFamiliarJugador(any(FamiliarJugador.class))).thenReturn(true);

        assertThat(service.guardarFamiliarJugador(dto)).isTrue();
        verify(familiarJugadorDao, never()).existeRelacion(any(), any());
    }

    @Test
    void guardarFamiliarJugadorInsertaCuandoTodoEsValido() {
        FamiliarJugadorDTO dto = new FamiliarJugadorDTO();
        dto.setFamiliarId(1L);
        dto.setJugadorId(2L);

        when(familiarJugadorDao.existeRelacion(1L, 2L)).thenReturn(false);
        when(familiarJugadorDao.guardarFamiliarJugador(any(FamiliarJugador.class))).thenReturn(true);

        assertThat(service.guardarFamiliarJugador(dto)).isTrue();
    }

    @Test
    void obtenerPorIdEnriqueceConDatosDeFamiliarYJugador() {
        FamiliarJugador relacion = new FamiliarJugador();
        relacion.setId(1L);
        relacion.setFamiliarId(10L);
        relacion.setJugadorId(20L);

        when(familiarJugadorDao.obtenerPorId(1L)).thenReturn(relacion);

        Familiar familiar = new Familiar();
        familiar.setNombre("Ana");
        familiar.setApellidos("Garcia");
        when(familiarDao.obtenerPorId(10L)).thenReturn(familiar);

        Jugador jugador = new Jugador();
        jugador.setNombre("Juan");
        jugador.setDorsal(9);
        when(jugadorDao.obtenerPorId(20L)).thenReturn(jugador);

        FamiliarJugadorDTO resultado = service.obtenerPorId(1L);

        assertThat(resultado.getFamiliarNombre()).isEqualTo("Ana");
        assertThat(resultado.getJugadorNombre()).isEqualTo("Juan");
        assertThat(resultado.getJugadorDorsal()).isEqualTo(9);
    }

    @Test
    void obtenerPorIdDevuelveNullCuandoNoExisteLaRelacion() {
        when(familiarJugadorDao.obtenerPorId(99L)).thenReturn(null);

        assertThat(service.obtenerPorId(99L)).isNull();
    }

    @Test
    void obtenerTodosEnriqueceCadaRelacion() {
        FamiliarJugador relacion = new FamiliarJugador();
        relacion.setFamiliarId(10L);
        relacion.setJugadorId(20L);

        when(familiarJugadorDao.obtenerTodos()).thenReturn(List.of(relacion));
        when(familiarDao.obtenerPorId(10L)).thenReturn(null);
        when(jugadorDao.obtenerPorId(20L)).thenReturn(null);

        List<FamiliarJugadorDTO> resultado = service.obtenerTodos();

        assertThat(resultado).hasSize(1);
    }

    @Test
    void obtenerFamiliaresDeJugadorCompletaLosDatosDelFamiliar() {
        FamiliarJugador relacion = new FamiliarJugador();
        relacion.setFamiliarId(10L);

        when(familiarJugadorDao.obtenerFamiliaresDeJugador(20L)).thenReturn(List.of(relacion));

        Familiar familiar = new Familiar();
        familiar.setNombre("Ana");
        familiar.setTelefono("600000000");
        when(familiarDao.obtenerPorId(10L)).thenReturn(familiar);

        List<FamiliarJugadorDTO> resultado = service.obtenerFamiliaresDeJugador(20L);

        assertThat(resultado.get(0).getFamiliarNombre()).isEqualTo("Ana");
        assertThat(resultado.get(0).getFamiliarTelefono()).isEqualTo("600000000");
    }

    @Test
    void obtenerJugadoresDeFamiliarCompletaLosDatosDelJugador() {
        FamiliarJugador relacion = new FamiliarJugador();
        relacion.setJugadorId(20L);

        when(familiarJugadorDao.obtenerJugadoresDeFamiliar(10L)).thenReturn(List.of(relacion));

        Jugador jugador = new Jugador();
        jugador.setNombre("Juan");
        jugador.setEquipo("Senior A");
        when(jugadorDao.obtenerPorId(20L)).thenReturn(jugador);

        List<FamiliarJugadorDTO> resultado = service.obtenerJugadoresDeFamiliar(10L);

        assertThat(resultado.get(0).getJugadorNombre()).isEqualTo("Juan");
        assertThat(resultado.get(0).getJugadorEquipo()).isEqualTo("Senior A");
    }

    @Test
    void eliminarDelegaEnElDao() {
        service.eliminar(1L);

        verify(familiarJugadorDao).eliminar(1L);
    }

    @Test
    void obtenerPrincipalDeJugadorDevuelveNullCuandoNoExiste() {
        when(familiarJugadorDao.obtenerPrincipalDeJugador(1L)).thenReturn(null);

        assertThat(service.obtenerPrincipalDeJugador(1L)).isNull();
    }

    @Test
    void existeRelacionDelegaEnElDao() {
        when(familiarJugadorDao.existeRelacion(1L, 2L)).thenReturn(true);

        assertThat(service.existeRelacion(1L, 2L)).isTrue();
    }

    @Test
    void tieneJugadoresDelegaEnElDao() {
        when(familiarJugadorDao.tieneJugadores(1L)).thenReturn(true);

        assertThat(service.tieneJugadores(1L)).isTrue();
    }
}
