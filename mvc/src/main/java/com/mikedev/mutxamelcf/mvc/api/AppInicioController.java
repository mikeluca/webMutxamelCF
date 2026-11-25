package com.mikedev.mutxamelcf.mvc.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.InicioAppService;

/**
 * Inicio de la app (docs/api-app-inicio-pendientes.md): "Tu próximo partido". Cualquier cuenta con sesión; solo
 * devuelve lo de la propia cuenta y sus hijos.
 */
@RestController
@RequestMapping("/api/app/inicio")
public class AppInicioController {

    private static final Logger logger = LoggerFactory.getLogger(AppInicioController.class);

    private final InicioAppService inicioAppService;

    public AppInicioController(InicioAppService inicioAppService) {
        this.inicioAppService = inicioAppService;
    }

    /** GET /api/app/inicio/proximos-partidos */
    @GetMapping("/proximos-partidos")
    public ResponseEntity<?> proximosPartidos(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.ok(inicioAppService.obtenerProximosPartidos(Long.parseLong(authentication.getName())));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (Exception e) {

            logger.error("Error al obtener los próximos partidos", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener los próximos partidos");
        }
    }
}
