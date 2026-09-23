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

import com.mikedev.mutxamelcf.dao.CuerpoTecnicoDao;
import com.mikedev.mutxamelcf.dao.UsuarioAppVinculoDao;
import com.mikedev.mutxamelcf.model.CuerpoTecnico;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoPublicDTO;

@ExtendWith(MockitoExtension.class)
class CuerpoTecnicoServiceImplTest {

    @Mock
    private CuerpoTecnicoDao cuerpoTecnicoDao;

    @Mock
    private UsuarioAppVinculoDao usuarioAppVinculoDao;

    private CuerpoTecnicoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CuerpoTecnicoServiceImpl(cuerpoTecnicoDao, usuarioAppVinculoDao);
    }

    @Test
    void guardarCuerpoTecnicoDelegaEnElDao() {
        when(cuerpoTecnicoDao.guardar(any(CuerpoTecnico.class))).thenReturn(true);

        CuerpoTecnicoDTO dto = new CuerpoTecnicoDTO();
        dto.setNombre("Ana");

        assertThat(service.guardarCuerpoTecnico(dto)).isTrue();
    }

    @Test
    void obtenerCuerpoTecnicoPorIdCodificaLaFotoEnBase64() {
        CuerpoTecnico staff = new CuerpoTecnico();
        staff.setId(1L);
        staff.setFoto(new byte[] { 5, 6 });

        when(cuerpoTecnicoDao.obtenerPorId(1L)).thenReturn(staff);

        CuerpoTecnicoDTO resultado = service.obtenerCuerpoTecnicoPorId(1L);

        assertThat(resultado.getFotoBase64()).isEqualTo(Base64.getEncoder().encodeToString(new byte[] { 5, 6 }));
    }

    @Test
    void eliminarCuerpoTecnicoLanzaExcepcionSiTieneCuentaDeApp() {
        when(usuarioAppVinculoDao.cuerpoTecnicoTieneCuenta(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminarCuerpoTecnico(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cuenta de la app");

        verify(cuerpoTecnicoDao, never()).eliminar(any());
    }

    @Test
    void eliminarCuerpoTecnicoBorraCuandoNoTieneCuenta() {
        when(usuarioAppVinculoDao.cuerpoTecnicoTieneCuenta(1L)).thenReturn(false);

        service.eliminarCuerpoTecnico(1L);

        verify(cuerpoTecnicoDao).eliminar(1L);
    }

    @Test
    void obtenerCuerpoTecnicoPorCategoriaDelegaEnElDao() {
        when(cuerpoTecnicoDao.obtenerTodosPorCategoria("SENIOR")).thenReturn(List.of(new CuerpoTecnico()));

        assertThat(service.obtenerCuerpoTecnicoPorCategoria("SENIOR")).hasSize(1);
    }

    @Test
    void obtenerCuerpoTecnicoPorEquipoDelegaEnElDao() {
        when(cuerpoTecnicoDao.obtenerTodosPorEquipo("Senior A")).thenReturn(List.of(new CuerpoTecnico()));

        assertThat(service.obtenerCuerpoTecnicoPorEquipo("Senior A")).hasSize(1);
    }

    @Test
    void obtenerTodosDelegaEnElDao() {
        when(cuerpoTecnicoDao.obtenerTodos()).thenReturn(List.of(new CuerpoTecnico()));

        assertThat(service.obtenerTodos()).hasSize(1);
    }

    @Test
    void obtenerCuerpoTecnicoPublicoPorEquipoMapeaCamposPublicos() {
        CuerpoTecnico staff = new CuerpoTecnico();
        staff.setId(1L);
        staff.setNombre("Ana");
        staff.setApellidos("Garcia");
        staff.setCategoria("SENIOR");
        staff.setEquipo("Senior A");
        staff.setDeporte("FUTBOL");
        staff.setPuesto("ENTRENADOR");
        staff.setFoto(null);

        when(cuerpoTecnicoDao.obtenerTodosPorEquipo("Senior A")).thenReturn(List.of(staff));

        List<CuerpoTecnicoPublicDTO> resultado = service.obtenerCuerpoTecnicoPublicoPorEquipo("Senior A");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getPuesto()).isEqualTo("ENTRENADOR");
    }
}
