package com.mikedev.mutxamelcf.mvc.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.PendientesService;

/**
 * "Pendientes" de la app (docs/api-app-inicio-pendientes.md): lo que la cuenta tiene por hacer según sus roles. Cualquier
 * cuenta con sesión; una de solo familia o jugador recibe la lista vacía.
 */
@RestController
@RequestMapping("/api/app/pendientes")
public class AppPendientesController {

    private static final Logger logger = LoggerFactory.getLogger(AppPendientesController.class);

    private final PendientesService pendientesService;

    public AppPendientesController(PendientesService pendientesService) {
        this.pendientesService = pendientesService;
    }

    /** GET /api/app/pendientes */
    @GetMapping
    public ResponseEntity<?> pendientes(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.ok(pendientesService.obtener(Long.parseLong(authentication.getName())));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (Exception e) {

            logger.error("Error al obtener los pendientes", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener los pendientes");
        }
    }
}
