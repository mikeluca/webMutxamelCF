package com.mikedev.mutxamelcf.mvc.api;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoPublicDTO;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorPublicDTO;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicEquipoControllerTest {

    @Test
    void obtenerEquiposDelegaEnElServicio() {
        EquipoService equipoService = mock(EquipoService.class);
        PublicEquipoController controller = new PublicEquipoController(equipoService, mock(JugadorService.class),
                mock(CuerpoTecnicoService.class));

        when(equipoService.obtenerTodos()).thenReturn(List.of(new EquipoDTO()));

        assertThat(controller.obtenerEquipos()).hasSize(1);
    }

    @Test
    void obtenerEquipoDelegaEnElServicio() {
        EquipoService equipoService = mock(EquipoService.class);
        PublicEquipoController controller = new PublicEquipoController(equipoService, mock(JugadorService.class),
                mock(CuerpoTecnicoService.class));

        EquipoDTO equipo = new EquipoDTO();
        when(equipoService.obtenerEquipoPorId(1L)).thenReturn(equipo);

        assertThat(controller.obtenerEquipo(1L)).isSameAs(equipo);
    }

    @Test
    void obtenerJugadoresDevuelveListaVaciaSiElEquipoNoExiste() {
        EquipoService equipoService = mock(EquipoService.class);
        PublicEquipoController controller = new PublicEquipoController(equipoService, mock(JugadorService.class),
                mock(CuerpoTecnicoService.class));

        when(equipoService.obtenerEquipoPorId(99L)).thenReturn(null);

        assertThat(controller.obtenerJugadores(99L)).isEmpty();
    }

    @Test
    void obtenerJugadoresDelegaEnElServicioConElNombreDelEquipo() {
        EquipoService equipoService = mock(EquipoService.class);
        JugadorService jugadorService = mock(JugadorService.class);
        PublicEquipoController controller = new PublicEquipoController(equipoService, jugadorService,
                mock(CuerpoTecnicoService.class));

        EquipoDTO equipo = new EquipoDTO();
        equipo.setNombre("Senior A");
        when(equipoService.obtenerEquipoPorId(1L)).thenReturn(equipo);
        when(jugadorService.obtenerJugadoresPublicosPorEquipo("Senior A")).thenReturn(List.of(new JugadorPublicDTO()));

        assertThat(controller.obtenerJugadores(1L)).hasSize(1);
    }

    @Test
    void obtenerCuerpoTecnicoDevuelveListaVaciaSiElEquipoNoExiste() {
        EquipoService equipoService = mock(EquipoService.class);
        PublicEquipoController controller = new PublicEquipoController(equipoService, mock(JugadorService.class),
                mock(CuerpoTecnicoService.class));

        when(equipoService.obtenerEquipoPorId(99L)).thenReturn(null);

        assertThat(controller.obtenerCuerpoTecnico(99L)).isEmpty();
    }

    @Test
    void obtenerCuerpoTecnicoDelegaEnElServicioConElNombreDelEquipo() {
        EquipoService equipoService = mock(EquipoService.class);
        CuerpoTecnicoService cuerpoTecnicoService = mock(CuerpoTecnicoService.class);
        PublicEquipoController controller = new PublicEquipoController(equipoService, mock(JugadorService.class),
                cuerpoTecnicoService);

        EquipoDTO equipo = new EquipoDTO();
        equipo.setNombre("Senior A");
        when(equipoService.obtenerEquipoPorId(1L)).thenReturn(equipo);
        when(cuerpoTecnicoService.obtenerCuerpoTecnicoPublicoPorEquipo("Senior A"))
                .thenReturn(List.of(new CuerpoTecnicoPublicDTO()));

        assertThat(controller.obtenerCuerpoTecnico(1L)).hasSize(1);
    }
}
