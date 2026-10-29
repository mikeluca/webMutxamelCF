package com.mikedev.mutxamelcf.mvc.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.ContinuidadService;

/**
 * Confirmación de continuidad desde la app: las familias (y los propios jugadores con cuenta) contestan si cada
 * jugador sigue la temporada siguiente. Solo ven y contestan por los jugadores con los que están vinculados.
 */
@RestController
@RequestMapping("/api/app/continuidad")
public class AppContinuidadController {

    private final ContinuidadService continuidadService;

    public AppContinuidadController(ContinuidadService continuidadService) {
        this.continuidadService = continuidadService;
    }

    /** GET /api/app/continuidad: los jugadores del usuario que tienen que confirmar; lista vacía si no hay borrador. */
    @GetMapping
    public ResponseEntity<?> listar(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.ok(continuidadService.listar(Long.valueOf(authentication.getName())));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");
        }
    }

    /**
     * PUT /api/app/continuidad/{jugadorId} {respuesta: SIGUE | NO_SIGUE | DUDA}
     *
     * Se puede cambiar de respuesta hasta el cierre de la temporada. NO_SIGUE queda como baja propuesta, pendiente de
     * que la confirme el coordinador.
     */
    @PutMapping("/{jugadorId}")
    public ResponseEntity<?> responder(@PathVariable Long jugadorId, @RequestBody Map<String, String> cuerpo,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.ok(continuidadService.responder(Long.valueOf(authentication.getName()), jugadorId,
                    cuerpo == null ? null : cuerpo.get("respuesta")));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (IllegalStateException e) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }
}
