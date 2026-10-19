package com.mikedev.mutxamelcf.mvc.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.ClasificacionPublicDTO;
import com.mikedev.mutxamelcf.service.ClasificacionService;

@RestController
@RequestMapping("/api/public/equipos")
public class PublicClasificacionController {

    private final ClasificacionService clasificacionService;

    public PublicClasificacionController(ClasificacionService clasificacionService) {
        this.clasificacionService = clasificacionService;
    }

    /**
     * GET /api/public/equipos/{id}/clasificacion
     *
     * Clasificación completa del grupo del equipo, ordenada por posición.
     * 404 si todavía no se ha importado ninguna (o el equipo no existe).
     */
    @GetMapping("/{id}/clasificacion")
    public ResponseEntity<ClasificacionPublicDTO> obtenerClasificacion(@PathVariable Long id) {

        ClasificacionPublicDTO clasificacion = clasificacionService.obtenerPublicaPorEquipo(id);

        if (clasificacion == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(clasificacion);
    }

}
