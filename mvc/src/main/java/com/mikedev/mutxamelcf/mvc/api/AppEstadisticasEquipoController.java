package com.mikedev.mutxamelcf.mvc.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.EstadisticasEquipoService;

/**
 * Tarjetas acumuladas y estadísticas de un equipo, solo para su staff
 * (el servicio comprueba puedeGestionarEquipo: ENTRENADOR del equipo,
 * COORDINADOR y ADMIN_APP en cualquiera).
 */
@RestController
@RequestMapping("/api/app/equipos/{equipoId}")
public class AppEstadisticasEquipoController {

    private final EstadisticasEquipoService estadisticasEquipoService;

    public AppEstadisticasEquipoController(EstadisticasEquipoService estadisticasEquipoService) {
        this.estadisticasEquipoService = estadisticasEquipoService;
    }

    /**
     * GET /api/app/equipos/{equipoId}/tarjetas
     *
     * Por jugador: amarillas, rojas y estado (OK, A_UNA, SANCION_POSIBLE), de
     * más a menos amarillas.
     */
    @GetMapping("/tarjetas")
    public ResponseEntity<?> obtenerTarjetas(@PathVariable Long equipoId, Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.valueOf(authentication.getName());

            return ResponseEntity.ok(estadisticasEquipoService.obtenerTarjetas(usuarioId, equipoId));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener las tarjetas");
        }
    }

    /**
     * GET /api/app/equipos/{equipoId}/estadisticas
     *
     * Goleadores, asistentes, asistencia a entrenamientos y partidos de la
     * temporada activa.
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<?> obtenerEstadisticas(@PathVariable Long equipoId, Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.valueOf(authentication.getName());

            return ResponseEntity.ok(estadisticasEquipoService.obtenerEstadisticas(usuarioId, equipoId));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las estadísticas del equipo");
        }
    }
}
