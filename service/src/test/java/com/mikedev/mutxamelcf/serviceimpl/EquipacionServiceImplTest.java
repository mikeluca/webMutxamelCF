package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mikedev.mutxamelcf.dao.EquipacionDao;
import com.mikedev.mutxamelcf.dao.EquipoGestionDao;
import com.mikedev.mutxamelcf.model.Equipacion;

class EquipacionServiceImplTest {

    private final EquipacionDao dao = mock(EquipacionDao.class);
    private final EquipoGestionDao gestion = mock(EquipoGestionDao.class);
    private final EquipacionServiceImpl service = new EquipacionServiceImpl(dao, gestion);

    private static Equipacion equipacion(Long id, String nombre) {
        Equipacion e = new Equipacion();
        e.setId(id);
        e.setNombre(nombre);
        return e;
    }

    @Test
    void laApiSoloListaLosActivosParaQuienGestionaAlgunEquipo() {
        when(gestion.gestionaAlgunEquipo(7L)).thenReturn(true);
        Equipacion segunda = equipacion(1L, "Segunda");
        segunda.setColorHex("#112233");
        when(dao.obtenerActivas()).thenReturn(List.of(segunda));

        var lista = service.obtenerActivasParaApp(7L);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).nombre()).isEqualTo("Segunda");
        assertThat(lista.get(0).colorHex()).isEqualTo("#112233");
    }

    @Test
    void quienNoGestionaNingunEquipoRecibe403() {
        when(gestion.gestionaAlgunEquipo(8L)).thenReturn(false);

        assertThatThrownBy(() -> service.obtenerActivasParaApp(8L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.obtenerActivasParaApp(null)).isInstanceOf(SecurityException.class);
        verify(dao, never()).obtenerActivas();
    }

    @Test
    void guardarValidaNombreColorYLongitudes() {
        assertThatThrownBy(() -> service.guardar(null, "  ", null, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nombre");
        assertThatThrownBy(() -> service.guardar(null, "x".repeat(51), null, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("50");
        assertThatThrownBy(() -> service.guardar(null, "Primera", null, null, null, "rojo", 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("#RRGGBB");
        assertThatThrownBy(() -> service.guardar(null, "Primera", null, null, null, "#12345", 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dao, never()).crear(any());
    }

    @Test
    void guardarRechazaUnNombreRepetidoSinDistinguirMayusculasPeroNoConsigoMismo() {
        when(dao.obtenerTodas()).thenReturn(List.of(equipacion(1L, "Primera")));

        assertThatThrownBy(() -> service.guardar(null, "primera", null, null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ya existe");

        // Editar el propio equipaje con el mismo nombre es válido.
        when(dao.obtenerPorId(1L)).thenReturn(equipacion(1L, "Primera"));
        service.guardar(1L, "Primera", "Blanca", null, null, "#ffffff", 3);
        verify(dao).actualizar(any());
    }

    @Test
    void guardarLimpiaLosTextosYNormalizaElColor() {
        when(dao.obtenerTodas()).thenReturn(List.of());
        when(dao.crear(any())).thenAnswer(i -> i.getArgument(0));

        Equipacion creada = service.guardar(null, "  Segunda ", " Azul ", "", null, "#0a0B0c", null);

        assertThat(creada.getNombre()).isEqualTo("Segunda");
        assertThat(creada.getCamiseta()).isEqualTo("Azul");
        assertThat(creada.getPantalon()).isNull();
        assertThat(creada.getColorHex()).isEqualTo("#0A0B0C");
        assertThat(creada.getOrden()).isZero();
        assertThat(creada.isActiva()).isTrue();
    }

    @Test
    void unEquipajeUsadoEnUnaConvocatoriaNoSeBorra() {
        when(dao.obtenerPorId(1L)).thenReturn(equipacion(1L, "Primera"));
        when(dao.contarConvocatorias(1L)).thenReturn(2);

        assertThatThrownBy(() -> service.eliminar(1L)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desactívalo");
        verify(dao, never()).eliminar(1L);
    }

    @Test
    void unEquipajeSinUsoSeBorraYSeDesactiva() {
        Equipacion e = equipacion(1L, "Primera");
        when(dao.obtenerPorId(1L)).thenReturn(e);
        when(dao.contarConvocatorias(1L)).thenReturn(0);

        service.eliminar(1L);
        verify(dao).eliminar(1L);

        service.cambiarActiva(1L, false);
        assertThat(e.isActiva()).isFalse();
        verify(dao).actualizar(e);
    }
}
