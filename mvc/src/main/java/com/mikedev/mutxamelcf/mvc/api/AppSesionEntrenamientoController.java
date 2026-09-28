package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.model.JustificacionFaltaResponse;
import com.mikedev.mutxamelcf.model.SesionCancelarRequest;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoActualizarRequest;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoCrearRequest;
import com.mikedev.mutxamelcf.model.SesionEntrenamientoResponse;
import com.mikedev.mutxamelcf.service.SesionEntrenamientoService;

import jakarta.validation.Valid;

/**
 * Sesiones concretas de entrenamiento (generadas a partir de un
 * horario recurrente, o sueltas) y justificación informativa de falta.
 */
@RestController
@RequestMapping("/api/app/sesiones-entrenamiento")
public class AppSesionEntrenamientoController {

    private final SesionEntrenamientoService sesionEntrenamientoService;

    public AppSesionEntrenamientoController(SesionEntrenamientoService sesionEntrenamientoService) {
        this.sesionEntrenamientoService = sesionEntrenamientoService;
    }

    /**
     * GET /api/app/sesiones-entrenamiento?equipoId=1&desde=2026-10-01&hasta=2026-10-31
     *
     * Solo exige estar autenticado (los horarios de entrenamiento no
     * son un dato sensible), tanto para la vista del entrenador como
     * para la de jugadores/familias.
     */
    @GetMapping
    public ResponseEntity<?> obtenerPorEquipoYRango(
            @RequestParam Long equipoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            List<SesionEntrenamientoResponse> sesiones = sesionEntrenamientoService.obtenerPorEquipoYRango(
                    equipoId, desde, hasta);

            return ResponseEntity.ok(sesiones);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener las sesiones");
        }
    }

    /**
     * GET /api/app/sesiones-entrenamiento/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            SesionEntrenamientoResponse sesion = sesionEntrenamientoService.obtenerPorId(id);

            return ResponseEntity.ok(sesion);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener la sesión");
        }
    }

    /**
     * POST /api/app/sesiones-entrenamiento
     *
     * Alta de una sesión suelta, no ligada a ningún horario recurrente.
     */
    @PostMapping
    public ResponseEntity<?> crear(
            @Valid @RequestBody SesionEntrenamientoCrearRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            SesionEntrenamientoResponse creada = sesionEntrenamientoService.crear(usuarioId, request);

            return ResponseEntity.status(HttpStatus.CREATED).body(creada);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al crear la sesión");
        }
    }

    /**
     * PUT /api/app/sesiones-entrenamiento/{id}
     *
     * Edita hora/lugar de esa única sesión (no afecta al resto de
     * sesiones generadas por el mismo horario, si lo tuviera).
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable Long id,
            @RequestBody SesionEntrenamientoActualizarRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            SesionEntrenamientoResponse actualizada = sesionEntrenamientoService.actualizar(usuarioId, id, request);

            return ResponseEntity.ok(actualizada);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar la sesión");
        }
    }

    /**
     * POST /api/app/sesiones-entrenamiento/{id}/cancelar
     *
     * Cancela esa sesión (ESTADO = CANCELADA) y notifica a los
     * jugadores del equipo y a sus familiares. El motivo es obligatorio.
     */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelar(
            @PathVariable Long id,
            @Valid @RequestBody SesionCancelarRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            sesionEntrenamientoService.cancelar(usuarioId, id, request.getMotivo());

            return ResponseEntity.noContent().build();

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al cancelar la sesión");
        }
    }

    /**
     * POST /api/app/sesiones-entrenamiento/{id}/justificaciones
     *
     * Aviso informativo de falta: no requiere aprobación. Solo puede
     * justificar el propio jugador o un familiar suyo.
     */
    @PostMapping("/{id}/justificaciones")
    public ResponseEntity<?> justificar(
            @PathVariable Long id,
            @Valid @RequestBody JustificacionFaltaRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            JustificacionFaltaResponse justificacion = sesionEntrenamientoService.justificar(usuarioId, id, request);

            return ResponseEntity.status(HttpStatus.CREATED).body(justificacion);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al justificar la falta");
        }
    }

    /**
     * GET /api/app/sesiones-entrenamiento/{id}/justificaciones
     *
     * Vista del entrenador: quién ha justificado su falta a esta
     * sesión. Requiere poder gestionar el equipo de la sesión.
     */
    @GetMapping("/{id}/justificaciones")
    public ResponseEntity<?> obtenerJustificaciones(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            List<JustificacionFaltaResponse> justificaciones = sesionEntrenamientoService.obtenerJustificaciones(
                    usuarioId, id);

            return ResponseEntity.ok(justificaciones);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las justificaciones");
        }
    }

}
