package com.mikedev.mutxamelcf.mvc.api;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.ConvocatoriaPartidoService;

/**
 * Convocatoria de un partido vista por la familia (JUGADOR/FAMILIAR). El vínculo con el
 * jugador y su pertenencia al equipo del partido los comprueba el servicio.
 */
@RestController
@RequestMapping("/api/app/partidos/{id}/convocatoria")
public class AppPartidoConvocatoriaController {

    private final ConvocatoriaPartidoService convocatoriaPartidoService;

    public AppPartidoConvocatoriaController(ConvocatoriaPartidoService convocatoriaPartidoService) {
        this.convocatoriaPartidoService = convocatoriaPartidoService;
    }

    /** GET /api/app/partidos/{id}/convocatoria?jugadorId=: 404 si el partido aún no tiene convocatoria. */
    @GetMapping
    public ResponseEntity<?> obtener(
            @PathVariable Long id,
            @RequestParam Long jugadorId,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            return ResponseEntity.ok(convocatoriaPartidoService.obtenerParaJugador(usuarioId, id, jugadorId));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (NoSuchElementException e) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener la convocatoria");
        }
    }
}
