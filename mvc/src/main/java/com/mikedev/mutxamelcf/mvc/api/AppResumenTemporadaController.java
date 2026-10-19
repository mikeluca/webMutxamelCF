package com.mikedev.mutxamelcf.mvc.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.ResumenTemporadaResponse;
import com.mikedev.mutxamelcf.service.ResumenTemporadaService;

@RestController
@RequestMapping("/api/app/jugadores")
public class AppResumenTemporadaController {

    private final ResumenTemporadaService resumenTemporadaService;

    public AppResumenTemporadaController(ResumenTemporadaService resumenTemporadaService) {
        this.resumenTemporadaService = resumenTemporadaService;
    }

    /**
     * GET /api/app/jugadores/{id}/resumen-temporada
     *
     * Estadísticas de partidos, asistencia a entrenamientos y respuestas a
     * convocatorias de la temporada activa. Solo con vínculo con el jugador o
     * si se gestiona su equipo (403 si no).
     */
    @GetMapping("/{id}/resumen-temporada")
    public ResponseEntity<?> obtenerResumen(@PathVariable Long id, Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.valueOf(authentication.getName());

            ResumenTemporadaResponse resumen = resumenTemporadaService.obtenerResumen(usuarioId, id);

            return ResponseEntity.ok(resumen);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener el resumen de la temporada");
        }
    }

}
