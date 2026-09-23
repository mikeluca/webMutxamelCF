package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mikedev.mutxamelcf.dao.ConvocatoriaDao;
import com.mikedev.mutxamelcf.dao.EntrenamientoDao;
import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.JugadorService;

@ExtendWith(MockitoExtension.class)
class EquipoServiceImplTest {

    @Mock
    private EquipoDao equipoDao;

    @Mock
    private JugadorService jugadorService;

    @Mock
    private CuerpoTecnicoService cuerpoTecnicoService;

    @Mock
    private ConvocatoriaDao convocatoriaDao;

    @Mock
    private EntrenamientoDao entrenamientoDao;

    private EquipoServiceImpl equipoService;

    @BeforeEach
    void setUp() {
        equipoService = new EquipoServiceImpl(equipoDao, jugadorService, cuerpoTecnicoService, convocatoriaDao,
                entrenamientoDao);
    }

    private static EquipoDTO nuevoEquipoDTO() {
        EquipoDTO dto = new EquipoDTO();
        // La categoria no lleva espacios: EnumEquipos.getOrdenByCategoria la
        // compara sin recortar, asi que un valor con espacios no encontraria
        // coincidencia (ver toEntity en EquipoServiceImpl).
        dto.setCategoria("Senior");
        dto.setGrupo(" A ");
        dto.setNombre(" Senior A ");
        dto.setDeporte(" FUTBOL ");
        return dto;
    }

    @Test
    void guardarRecortaEspaciosYDelegaEnElDao() {
        when(equipoDao.guardar(any(Equipo.class))).thenReturn(true);

        boolean resultado = equipoService.guardar(nuevoEquipoDTO());

        assertThat(resultado).isTrue();

        org.mockito.ArgumentCaptor<Equipo> captor = org.mockito.ArgumentCaptor.forClass(Equipo.class);
        verify(equipoDao).guardar(captor.capture());
        assertThat(captor.getValue().getGrupo()).isEqualTo("A");
        assertThat(captor.getValue().getNombre()).isEqualTo("Senior A");
    }

    @Test
    void obtenerEquipoPorIdMapeaAEquipoDTO() {
        Equipo equipo = new Equipo();
        equipo.setId(1L);
        equipo.setCategoria("SENIOR");
        equipo.setGrupo("A");
        equipo.setNombre("Senior A");
        equipo.setDeporte("FUTBOL");
        equipo.setOrden("1");

        when(equipoDao.obtenerEquipoPorId(1L)).thenReturn(equipo);

        EquipoDTO resultado = equipoService.obtenerEquipoPorId(1L);

        assertThat(resultado.getNombre()).isEqualTo("Senior A");
    }

    @Test
    void obtenerEquipoPorIdDevuelveNullCuandoNoExiste() {
        when(equipoDao.obtenerEquipoPorId(99L)).thenReturn(null);

        assertThat(equipoService.obtenerEquipoPorId(99L)).isNull();
    }

    @Test
    void eliminarEquipoNoHaceNadaSiElEquipoNoExiste() {
        when(equipoDao.obtenerEquipoPorId(1L)).thenReturn(null);

        equipoService.eliminarEquipo(1L);

        verify(equipoDao, never()).eliminarEquipo(any());
    }

    @Test
    void eliminarEquipoLanzaExcepcionSiTieneJugadoresAsignados() {
        Equipo equipo = new Equipo();
        equipo.setId(1L);
        equipo.setNombre("Senior A");

        when(equipoDao.obtenerEquipoPorId(1L)).thenReturn(equipo);
        when(jugadorService.obtenerJugadoresPorEquipo("Senior A")).thenReturn(List.of(new JugadorDTO()));
        when(cuerpoTecnicoService.obtenerCuerpoTecnicoPorEquipo("Senior A")).thenReturn(List.of());
        when(convocatoriaDao.obtenerPorEquipo(1L)).thenReturn(List.of());
        when(entrenamientoDao.obtenerPorEquipo(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> equipoService.eliminarEquipo(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jugadores asignados");

        verify(equipoDao, never()).eliminarEquipo(any());
    }

    @Test
    void eliminarEquipoBorraCuandoNoTieneDependencias() {
        Equipo equipo = new Equipo();
        equipo.setId(1L);
        equipo.setNombre("Senior A");

        when(equipoDao.obtenerEquipoPorId(1L)).thenReturn(equipo);
        when(jugadorService.obtenerJugadoresPorEquipo("Senior A")).thenReturn(List.of());
        when(cuerpoTecnicoService.obtenerCuerpoTecnicoPorEquipo("Senior A")).thenReturn(List.of());
        when(convocatoriaDao.obtenerPorEquipo(1L)).thenReturn(List.of());
        when(entrenamientoDao.obtenerPorEquipo(1L)).thenReturn(List.of());

        equipoService.eliminarEquipo(1L);

        verify(equipoDao).eliminarEquipo(1L);
    }

    @Test
    void obtenerCategoriasDelegaEnElDao() {
        when(equipoDao.obtenerCategorias()).thenReturn(List.of("SENIOR", "JUVENIL"));

        assertThat(equipoService.obtenerCategorias()).containsExactly("SENIOR", "JUVENIL");
    }

    @Test
    void obtenerTodosOrdenaPorOrdenYNombre() {
        Equipo equipoB = new Equipo();
        equipoB.setOrden("2");
        equipoB.setNombre("Senior B");
        equipoB.setCategoria("SENIOR");
        equipoB.setGrupo("B");
        equipoB.setDeporte("FUTBOL");

        Equipo equipoA = new Equipo();
        equipoA.setOrden("1");
        equipoA.setNombre("Senior A");
        equipoA.setCategoria("SENIOR");
        equipoA.setGrupo("A");
        equipoA.setDeporte("FUTBOL");

        when(equipoDao.obtenerTodos()).thenReturn(List.of(equipoB, equipoA));

        List<EquipoDTO> resultado = equipoService.obtenerTodos();

        assertThat(resultado).extracting(EquipoDTO::getNombre).containsExactly("Senior A", "Senior B");
    }

    @Test
    void obtenerTodosPorCategoriaOrdenaPorOrdenYNombre() {
        Equipo equipo = new Equipo();
        equipo.setOrden("1");
        equipo.setNombre("Senior A");
        equipo.setCategoria("SENIOR");
        equipo.setGrupo("A");
        equipo.setDeporte("FUTBOL");

        when(equipoDao.obtenerTodosPorCategoria("SENIOR")).thenReturn(List.of(equipo));

        List<EquipoDTO> resultado = equipoService.obtenerTodosPorCategoria("SENIOR");

        assertThat(resultado).hasSize(1);
    }

    @Test
    void obtenerEquiposAgrupadosPorCategoriaOrdenaCadaGrupoPorOrden() {
        Equipo equipoB = new Equipo();
        equipoB.setOrden("2");
        equipoB.setNombre("Senior B");
        equipoB.setCategoria("SENIOR");
        equipoB.setGrupo("B");
        equipoB.setDeporte("FUTBOL");

        Equipo equipoA = new Equipo();
        equipoA.setOrden("1");
        equipoA.setNombre("Senior A");
        equipoA.setCategoria("SENIOR");
        equipoA.setGrupo("A");
        equipoA.setDeporte("FUTBOL");

        when(equipoDao.obtenerEquiposAgrupadosPorCategoria("FUTBOL"))
                .thenReturn(Map.of("SENIOR", List.of(equipoB, equipoA)));

        Map<String, List<EquipoDTO>> resultado = equipoService.obtenerEquiposAgrupadosPorCategoria("FUTBOL");

        assertThat(resultado.get("SENIOR")).extracting(EquipoDTO::getNombre).containsExactly("Senior A", "Senior B");
    }
}
