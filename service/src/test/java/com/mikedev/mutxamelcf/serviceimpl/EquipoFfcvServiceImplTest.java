package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mikedev.mutxamelcf.dao.EquipoDao;
import com.mikedev.mutxamelcf.dao.EquipoFfcvDao;
import com.mikedev.mutxamelcf.model.Equipo;
import com.mikedev.mutxamelcf.model.EquipoFfcv;
import com.mikedev.mutxamelcf.model.EquipoFfcvDTO;

class EquipoFfcvServiceImplTest {

    private EquipoFfcvDao equipoFfcvDao;
    private EquipoDao equipoDao;
    private EquipoFfcvServiceImpl service;

    @BeforeEach
    void setUp() {
        equipoFfcvDao = mock(EquipoFfcvDao.class);
        equipoDao = mock(EquipoDao.class);
        service = new EquipoFfcvServiceImpl(equipoFfcvDao, equipoDao);

        Equipo filial = new Equipo();
        filial.setId(2L);
        filial.setNombre("Filial");
        when(equipoDao.obtenerEquipoPorId(2L)).thenReturn(filial);
    }

    @Test
    void crearNormalizaLaCompeticionYLaLetra() {
        EquipoFfcvDTO dto = service.crear(" Segona FFCV\t, Grup - 7 ", " b ", 2L);

        ArgumentCaptor<EquipoFfcv> captor = ArgumentCaptor.forClass(EquipoFfcv.class);
        verify(equipoFfcvDao).crear(captor.capture());
        assertThat(captor.getValue().getCompeticion()).isEqualTo("Segona FFCV, Grup 7");
        assertThat(captor.getValue().getLetra()).isEqualTo("B");
        assertThat(dto.equipoNombre()).isEqualTo("Filial");
    }

    @Test
    void crearSinLetraGuardaNull() {
        service.crear("Primera FFCV, Grup 4", "  ", 2L);

        ArgumentCaptor<EquipoFfcv> captor = ArgumentCaptor.forClass(EquipoFfcv.class);
        verify(equipoFfcvDao).crear(captor.capture());
        assertThat(captor.getValue().getLetra()).isNull();
    }

    @Test
    void crearRechazaDatosInvalidos() {
        assertThatThrownBy(() -> service.crear(" ", "A", 2L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.crear("Comp", "A1", 2L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.crear("Comp", "A", 99L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.crear("Comp", "A", null)).isInstanceOf(IllegalArgumentException.class);
        verify(equipoFfcvDao, never()).crear(any());
    }

    @Test
    void crearRechazaUnaRelacionDuplicada() {
        when(equipoFfcvDao.obtenerPorCompeticionYLetra("Segona FFCV, Grup 7", "B"))
                .thenReturn(new EquipoFfcv(1L, 2L, "Segona FFCV, Grup 7", "B"));

        assertThatThrownBy(() -> service.crear("Segona FFCV, Grup 7", "B", 2L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarPermiteConservarSuPropiaCompeticionYLetra() {
        EquipoFfcv existente = new EquipoFfcv(1L, 2L, "Segona FFCV, Grup 7", "B");
        when(equipoFfcvDao.obtenerPorId(1L)).thenReturn(existente);
        when(equipoFfcvDao.obtenerPorCompeticionYLetra("Segona FFCV, Grup 7", "B")).thenReturn(existente);

        service.actualizar(1L, "Segona FFCV, Grup 7", "B", 2L);

        verify(equipoFfcvDao).actualizar(any());
    }

    @Test
    void actualizarYEliminarRechazanUnaRelacionInexistente() {
        assertThatThrownBy(() -> service.actualizar(9L, "Comp", "A", 2L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.eliminar(9L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminarBorraLaRelacion() {
        when(equipoFfcvDao.obtenerPorId(1L)).thenReturn(new EquipoFfcv(1L, 2L, "Comp", null));

        service.eliminar(1L);

        verify(equipoFfcvDao).eliminar(1L);
    }

    @Test
    void listarIncluyeElNombreDelEquipo() {
        when(equipoFfcvDao.listarTodos()).thenReturn(List.of(new EquipoFfcv(1L, 2L, "Comp", "B")));

        assertThat(service.listar()).singleElement().satisfies(dto -> {
            assertThat(dto.equipoNombre()).isEqualTo("Filial");
            assertThat(dto.letra()).isEqualTo("B");
        });
    }

}
