package com.mikedev.mutxamelcf.mvc.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.mikedev.mutxamelcf.service.EquipoFfcvService;
import com.mikedev.mutxamelcf.service.EquipoService;

class EquipoFfcvAdminControllerTest {

    private EquipoFfcvService equipoFfcvService;
    private EquipoService equipoService;
    private EquipoFfcvAdminController controller;

    @BeforeEach
    void setUp() {
        equipoFfcvService = mock(EquipoFfcvService.class);
        equipoService = mock(EquipoService.class);
        controller = new EquipoFfcvAdminController(equipoFfcvService, equipoService);
    }

    @Test
    void listarPoneRelacionesYEquiposEnElModelo() {
        Model model = new ExtendedModelMap();

        String vista = controller.listar(model);

        assertThat(vista).isEqualTo("admin/equipos-ffcv");
        assertThat(model.asMap()).containsKeys("relaciones", "equipos");
    }

    @Test
    void crearRedirigeAlListadoSiTodoVaBien() {
        assertThat(controller.crear("Segona FFCV, Grup 7", "B", 2L)).isEqualTo("redirect:/admin/equipos-ffcv");

        verify(equipoFfcvService).crear("Segona FFCV, Grup 7", "B", 2L);
    }

    @Test
    void crearRedirigeConElMensajeDeErrorDeValidacion() {
        doThrow(new IllegalArgumentException("Ya existe una relación para esa competición y letra"))
                .when(equipoFfcvService).crear("X", null, 2L);

        String destino = controller.crear("X", null, 2L);

        assertThat(destino).startsWith("redirect:/admin/equipos-ffcv?error=");
        assertThat(destino).contains("Ya%20existe");
    }

    @Test
    void actualizarYEliminarDelegan() {
        assertThat(controller.actualizar(1L, "Comp", "A", 2L)).isEqualTo("redirect:/admin/equipos-ffcv");
        assertThat(controller.eliminar(1L)).isEqualTo("redirect:/admin/equipos-ffcv");

        verify(equipoFfcvService).actualizar(1L, "Comp", "A", 2L);
        verify(equipoFfcvService).eliminar(1L);
    }

    @Test
    void unErrorInesperadoNoFiltraElDetalle() {
        doThrow(new IllegalStateException("detalle interno")).when(equipoFfcvService).eliminar(9L);

        String destino = controller.eliminar(9L);

        assertThat(destino).startsWith("redirect:/admin/equipos-ffcv?error=").doesNotContain("detalle");
    }

}
