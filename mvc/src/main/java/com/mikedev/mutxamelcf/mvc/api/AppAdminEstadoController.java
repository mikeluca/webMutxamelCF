package com.mikedev.mutxamelcf.mvc.api;

import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.EstadoAppService;

/** Estado de la app para ADMIN_APP (la comprobación está en EstadoAppService: 403 al resto). */
@RestController
@RequestMapping("/api/app/admin/estado")
public class AppAdminEstadoController {

    private static final Logger logger = LoggerFactory.getLogger(AppAdminEstadoController.class);

    private final EstadoAppService estadoAppService;

    public AppAdminEstadoController(EstadoAppService estadoAppService) {
        this.estadoAppService = estadoAppService;
    }

    /** GET /api/app/admin/estado */
    @GetMapping
    public ResponseEntity<?> estado(Authentication authentication) {
        return responder(authentication, estadoAppService::obtenerEstado);
    }

    /** GET /api/app/admin/estado/usuarios?filtro=SIN_DISPOSITIVO|SIN_NOTIFICACIONES|VERSION_ANTIGUA|INACTIVOS */
    @GetMapping("/usuarios")
    public ResponseEntity<?> usuarios(@RequestParam String filtro, Authentication authentication) {
        return responder(authentication, solicitante -> estadoAppService.listarUsuarios(solicitante, filtro));
    }

    private ResponseEntity<?> responder(Authentication authentication, IntFunction<Object> consulta) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.ok(consulta.apply(Integer.parseInt(authentication.getName())));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            logger.error("Error al obtener el estado de la app", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener el estado de la app");
        }
    }
}
