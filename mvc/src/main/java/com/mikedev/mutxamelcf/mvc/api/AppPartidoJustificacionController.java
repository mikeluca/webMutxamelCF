package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.JustificacionFaltaPartidoResponse;
import com.mikedev.mutxamelcf.model.JustificacionFaltaRequest;
import com.mikedev.mutxamelcf.service.JustificacionFaltaPartidoService;

import jakarta.validation.Valid;

/**
 * Ausencia justificada a partidos (mismo estilo que las justificaciones de
 * entrenamientos). Justifican y retiran el propio jugador o un familiar; la
 * seguridad (vínculo, equipo, plazo) la comprueba el servicio, no la app.
 */
@RestController
@RequestMapping("/api/app/partidos/{id}/justificaciones")
public class AppPartidoJustificacionController {

    private final JustificacionFaltaPartidoService justificacionService;

    public AppPartidoJustificacionController(JustificacionFaltaPartidoService justificacionService) {
        this.justificacionService = justificacionService;
    }

    /** POST /api/app/partidos/{id}/justificaciones  {jugadorId, motivo}: crea o actualiza. */
    @PostMapping
    public ResponseEntity<?> justificar(
            @PathVariable Long id,
            @Valid @RequestBody JustificacionFaltaRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            JustificacionFaltaPartidoResponse respuesta = justificacionService.justificar(usuarioId, id, request);

            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al justificar la ausencia");
        }
    }

    /** DELETE /api/app/partidos/{id}/justificaciones/{jugadorId}: retira la justificación. */
    @DeleteMapping("/{jugadorId}")
    public ResponseEntity<?> retirar(
            @PathVariable Long id,
            @PathVariable Long jugadorId,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            justificacionService.retirar(usuarioId, id, jugadorId);

            return ResponseEntity.noContent().build();

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al retirar la ausencia");
        }
    }

    /** GET /api/app/partidos/{id}/justificaciones: vista del entrenador. */
    @GetMapping
    public ResponseEntity<?> obtener(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            List<JustificacionFaltaPartidoResponse> justificaciones = justificacionService.obtener(usuarioId, id);

            return ResponseEntity.ok(justificaciones);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las ausencias justificadas");
        }
    }
}
