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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicEquipoControllerTest {

    @Test
    void obtenerEquiposDelegaEnElServicio() {
        EquipoService equipoService = mock(EquipoService.class);
        PublicEquipoController controller = new PublicEquipoController(equipoService, mock(JugadorService.class),
                mock(CuerpoTecnicoService.class));

        when(equipoService.obtenerPublicos()).thenReturn(List.of(new EquipoDTO()));

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
        equipo.setId(1L);
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
        equipo.setId(1L);
        equipo.setNombre("Senior A");
        when(equipoService.obtenerEquipoPorId(1L)).thenReturn(equipo);
        when(cuerpoTecnicoService.obtenerCuerpoTecnicoPublicoPorEquipoId(1L))
                .thenReturn(List.of(new CuerpoTecnicoPublicDTO()));

        assertThat(controller.obtenerCuerpoTecnico(1L)).hasSize(1);
    }

    @Test
    void conLaPlantillaEnPreparacionNoSePublicanJugadoresNiCuerpoTecnico() {
        EquipoService equipoService = mock(EquipoService.class);
        JugadorService jugadorService = mock(JugadorService.class);
        CuerpoTecnicoService cuerpoTecnicoService = mock(CuerpoTecnicoService.class);
        PublicEquipoController controller = new PublicEquipoController(equipoService, jugadorService,
                cuerpoTecnicoService);
        EquipoDTO equipo = new EquipoDTO();
        equipo.setId(5L);
        equipo.setNombre("Cadete A");
        when(equipoService.obtenerEquipoPorId(5L)).thenReturn(equipo);
        when(jugadorService.plantillaEnPreparacion("Cadete A")).thenReturn(true);

        assertThat(controller.obtenerJugadores(5L)).isEmpty();
        assertThat(controller.obtenerCuerpoTecnico(5L)).isEmpty();
        assertThat(controller.obtenerPlantilla(5L).getBody()).containsEntry("enPreparacion", true)
                .containsEntry("jugadores", List.of());
        org.mockito.Mockito.verify(jugadorService, org.mockito.Mockito.never()).obtenerJugadoresPublicosPorEquipo(any());
        org.mockito.Mockito.verify(cuerpoTecnicoService, org.mockito.Mockito.never())
                .obtenerCuerpoTecnicoPublicoPorEquipoId(any());
    }

    @Test
    void laPlantillaPublicadaIncluyeLosJugadoresYNoEstaEnPreparacion() {
        EquipoService equipoService = mock(EquipoService.class);
        JugadorService jugadorService = mock(JugadorService.class);
        PublicEquipoController controller = new PublicEquipoController(equipoService, jugadorService,
                mock(CuerpoTecnicoService.class));
        EquipoDTO equipo = new EquipoDTO();
        equipo.setId(5L);
        equipo.setNombre("Cadete A");
        when(equipoService.obtenerEquipoPorId(5L)).thenReturn(equipo);
        when(jugadorService.plantillaEnPreparacion("Cadete A")).thenReturn(false);
        when(jugadorService.obtenerJugadoresPublicosPorEquipo("Cadete A")).thenReturn(List.of(new JugadorPublicDTO()));

        assertThat(controller.obtenerPlantilla(5L).getBody()).containsEntry("enPreparacion", false);
        assertThat((List<?>) controller.obtenerPlantilla(5L).getBody().get("jugadores")).hasSize(1);
        assertThat(controller.obtenerPlantilla(99L).getStatusCode().value()).isEqualTo(404);
    }
}
