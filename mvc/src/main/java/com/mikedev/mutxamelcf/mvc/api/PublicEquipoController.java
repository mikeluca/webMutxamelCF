package com.mikedev.mutxamelcf.mvc.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
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
        return equipoService.obtenerPublicos();
    }

    @GetMapping("/{id}")
    public EquipoDTO obtenerEquipo(
            @PathVariable Long id) {
        return equipoService.obtenerEquipoPorId(id);
    }

    /**
     * GET /api/public/equipos/{id}/plantilla
     *
     * Igual que /jugadores, pero distingue "plantilla vacía" de "plantillas en preparación": el equipo todavía no tiene
     * ningún jugador con equipo definitivo (reparto de septiembre pendiente). 404 si el equipo no existe.
     */
    @GetMapping("/{id}/plantilla")
    public ResponseEntity<Map<String, Object>> obtenerPlantilla(@PathVariable Long id) {
        EquipoDTO equipo = equipoService.obtenerEquipoPorId(id);

        if (equipo == null) {
            return ResponseEntity.notFound().build();
        }

        boolean enPreparacion = jugadorService.plantillaEnPreparacion(equipo.getNombre());
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("enPreparacion", enPreparacion);
        respuesta.put("jugadores", enPreparacion ? List.of()
                : jugadorService.obtenerJugadoresPublicosPorEquipo(equipo.getNombre()));

        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/{id}/jugadores")
    public List<JugadorPublicDTO> obtenerJugadores(
            @PathVariable Long id) {

        EquipoDTO equipo = equipoService.obtenerEquipoPorId(id);

        if (equipo == null || jugadorService.plantillaEnPreparacion(equipo.getNombre())) {
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

        if (equipo == null || jugadorService.plantillaEnPreparacion(equipo.getNombre())) {
            return List.of();
        }

        // Por EQUIPO_ID, a través de CUERPO_TECNICO_EQUIPO; el puesto es el de este equipo
        return cuerpoTecnicoService
                .obtenerCuerpoTecnicoPublicoPorEquipoId(
                        equipo.getId());
    }
}