package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.Familiar;
import com.mikedev.mutxamelcf.model.FamiliarContactoDTO;
import com.mikedev.mutxamelcf.model.FamiliarDTO;
import com.mikedev.mutxamelcf.model.FamiliarJugadorDTO;
import com.mikedev.mutxamelcf.service.FamiliarJugadorService;

@ExtendWith(MockitoExtension.class)
class FamiliarServiceImplTest {

    @Mock
    private FamiliarDao familiarDao;

    @Mock
    private FamiliarJugadorService familiarJugadorService;

    @Mock
    private UsuarioAppVinculoDao usuarioAppVinculoDao;

    private FamiliarServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FamiliarServiceImpl(familiarDao, familiarJugadorService, usuarioAppVinculoDao);
    }

    @Test
    void guardarFamiliarTrasladaElIdGeneradoAlDto() {
        when(familiarDao.guardarFamiliar(any(Familiar.class))).thenAnswer(invocation -> {
            Familiar familiar = invocation.getArgument(0);
            familiar.setId(42L);
            return true;
        });

        FamiliarDTO dto = new FamiliarDTO();
        dto.setNombre("Ana");

        boolean resultado = service.guardarFamiliar(dto);

        assertThat(resultado).isTrue();
        assertThat(dto.getId()).isEqualTo(42L);
    }

    @Test
    void guardarFamiliarNoTocaElDtoSiFalla() {
        when(familiarDao.guardarFamiliar(any(Familiar.class))).thenReturn(false);

        FamiliarDTO dto = new FamiliarDTO();

        assertThat(service.guardarFamiliar(dto)).isFalse();
        assertThat(dto.getId()).isNull();
    }

    @Test
    void obtenerFamiliarPorIdDevuelveNullCuandoNoExiste() {
        when(familiarDao.obtenerPorId(99L)).thenReturn(null);

        assertThat(service.obtenerFamiliarPorId(99L)).isNull();
    }

    @Test
    void obtenerTodosDelegaEnElDao() {
        when(familiarDao.obtenerTodos()).thenReturn(List.of(new Familiar()));

        assertThat(service.obtenerTodos()).hasSize(1);
    }

    @Test
    void eliminarFamiliarLanzaExcepcionSiTieneJugadoresAsociados() {
        when(familiarJugadorService.tieneJugadores(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminarFamiliar(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asociado a uno o varios jugadores");

        verify(familiarDao, never()).eliminar(any());
    }

    @Test
    void eliminarFamiliarLanzaExcepcionSiTieneCuentaDeApp() {
        when(familiarJugadorService.tieneJugadores(1L)).thenReturn(false);
        when(usuarioAppVinculoDao.familiarTieneCuenta(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminarFamiliar(1L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eliminarFamiliarBorraCuandoNoTieneDependencias() {
        when(familiarJugadorService.tieneJugadores(1L)).thenReturn(false);
        when(usuarioAppVinculoDao.familiarTieneCuenta(1L)).thenReturn(false);

        service.eliminarFamiliar(1L);

        verify(familiarDao).eliminar(1L);
    }

    @Test
    void obtenerFamiliarPrincipalDeJugadorDevuelveNullSiNoHayRelacionPrincipal() {
        when(familiarJugadorService.obtenerPrincipalDeJugador(1L)).thenReturn(null);

        assertThat(service.obtenerFamiliarPrincipalDeJugador(1L)).isNull();
    }

    @Test
    void obtenerFamiliarPrincipalDeJugadorResuelveElFamiliar() {
        FamiliarJugadorDTO relacion = new FamiliarJugadorDTO();
        relacion.setFamiliarId(5L);
        when(familiarJugadorService.obtenerPrincipalDeJugador(1L)).thenReturn(relacion);

        Familiar familiar = new Familiar();
        familiar.setId(5L);
        familiar.setNombre("Ana");
        when(familiarDao.obtenerPorId(5L)).thenReturn(familiar);

        FamiliarDTO resultado = service.obtenerFamiliarPrincipalDeJugador(1L);

        assertThat(resultado.getNombre()).isEqualTo("Ana");
    }

    @Test
    void obtenerFamiliaresPorJugadorMapeaAFamiliarContactoDTO() {
        Familiar familiar = new Familiar();
        familiar.setId(1L);
        familiar.setNombre("Ana");
        familiar.setApellidos("Garcia");
        familiar.setTelefono("600000000");
        familiar.setEmail("ana@example.com");
        familiar.setWhatsappActivo(1);
        familiar.setParentesco("MADRE");
        familiar.setEsPrincipal(1);

        when(familiarDao.obtenerPorJugador(1L)).thenReturn(List.of(familiar));

        List<FamiliarContactoDTO> resultado = service.obtenerFamiliaresPorJugador(1L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getParentesco()).isEqualTo("MADRE");
        assertThat(resultado.get(0).getEsPrincipal()).isEqualTo(1);
    }
}
