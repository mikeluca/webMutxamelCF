package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.HorarioEntrenamientoActualizarRequest;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoCrearRequest;
import com.mikedev.mutxamelcf.model.HorarioEntrenamientoResponse;
import com.mikedev.mutxamelcf.service.HorarioEntrenamientoService;

import jakarta.validation.Valid;

/**
 * Horarios semanales fijos de entrenamiento de un equipo (recurrencia
 * "todos los martes a las 18:00", etc). Cada alta/edición dispara la
 * generación inmediata de las sesiones concretas correspondientes
 * (ver SesionEntrenamientoService/AppSesionEntrenamientoController).
 */
@RestController
@RequestMapping("/api/app/horarios-entrenamiento")
public class AppHorarioEntrenamientoController {

    private final HorarioEntrenamientoService horarioEntrenamientoService;

    public AppHorarioEntrenamientoController(HorarioEntrenamientoService horarioEntrenamientoService) {
        this.horarioEntrenamientoService = horarioEntrenamientoService;
    }

    /**
     * POST /api/app/horarios-entrenamiento
     */
    @PostMapping
    public ResponseEntity<?> crear(
            @Valid @RequestBody HorarioEntrenamientoCrearRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            HorarioEntrenamientoResponse creado = horarioEntrenamientoService.crear(usuarioId, request);

            return ResponseEntity.status(HttpStatus.CREATED).body(creado);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al crear el horario");
        }
    }

    /**
     * PUT /api/app/horarios-entrenamiento/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody HorarioEntrenamientoActualizarRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            HorarioEntrenamientoResponse actualizado = horarioEntrenamientoService.actualizar(usuarioId, id, request);

            return ResponseEntity.ok(actualizado);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar el horario");
        }
    }

    /**
     * DELETE /api/app/horarios-entrenamiento/{id}
     *
     * No borra la fila: desactiva el horario (ACTIVO = 0) y cancela sus
     * sesiones futuras todavía PROGRAMADA.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            horarioEntrenamientoService.eliminar(usuarioId, id);

            return ResponseEntity.noContent().build();

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al eliminar el horario");
        }
    }

    /**
     * GET /api/app/horarios-entrenamiento?equipoId=1
     */
    @GetMapping
    public ResponseEntity<?> obtenerPorEquipo(
            @RequestParam Long equipoId,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            List<HorarioEntrenamientoResponse> horarios = horarioEntrenamientoService.obtenerActivosPorEquipo(
                    usuarioId, equipoId);

            return ResponseEntity.ok(horarios);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener los horarios");
        }
    }

}
