package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.CuerpoTecnicoDTO;
import com.mikedev.mutxamelcf.model.CuerpoTecnicoPublicDTO;
import com.mikedev.mutxamelcf.model.EquipoDTO;
import com.mikedev.mutxamelcf.model.JugadorDTO;
import com.mikedev.mutxamelcf.model.JugadorPublicDTO;
import com.mikedev.mutxamelcf.service.EquipoService;
import com.mikedev.mutxamelcf.service.JugadorService;
import com.mikedev.mutxamelcf.service.CuerpoTecnicoService;

@RestController
@RequestMapping("/api/public/equipos")
public class PublicEquipoController {

    private final EquipoService equipoService;
    private final JugadorService jugadorService;
    private final CuerpoTecnicoService cuerpoTecnicoService;

    public PublicEquipoController(
            EquipoService equipoService,
            JugadorService jugadorService,
            CuerpoTecnicoService cuerpoTecnicoService) {
        this.equipoService = equipoService;
        this.jugadorService = jugadorService;
        this.cuerpoTecnicoService = cuerpoTecnicoService;
    }

    @GetMapping
    public List<EquipoDTO> obtenerEquipos() {
        return equipoService.obtenerTodos();
    }

    @GetMapping("/{id}")
    public EquipoDTO obtenerEquipo(
            @PathVariable Long id) {
        return equipoService.obtenerEquipoPorId(id);
    }

    @GetMapping("/{id}/jugadores")
    public List<JugadorPublicDTO> obtenerJugadores(
            @PathVariable Long id) {

        EquipoDTO equipo = equipoService.obtenerEquipoPorId(id);

        if (equipo == null) {
            return List.of();
        }

        return jugadorService
                .obtenerJugadoresPublicosPorEquipo(
                        equipo.getNombre());
    }

    @GetMapping("/{id}/cuerpo-tecnico")
    public List<CuerpoTecnicoPublicDTO> obtenerCuerpoTecnico(
            @PathVariable Long id) {

        EquipoDTO equipo = equipoService.obtenerEquipoPorId(id);

        if (equipo == null) {
            return List.of();
        }

        return cuerpoTecnicoService
                .obtenerCuerpoTecnicoPublicoPorEquipo(
                        equipo.getNombre());
    }
}