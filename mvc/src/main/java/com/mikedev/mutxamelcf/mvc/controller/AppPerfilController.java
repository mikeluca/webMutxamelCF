package com.mikedev.mutxamelcf.mvc.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.PerfilAppResponse;
import com.mikedev.mutxamelcf.service.PerfilAppService;

@RestController
@RequestMapping("/api/app/perfil")
public class AppPerfilController {

    private static final Logger logger = LoggerFactory.getLogger(AppPerfilController.class);

    private final PerfilAppService perfilAppService;

    public AppPerfilController(
            PerfilAppService perfilAppService) {

        this.perfilAppService = perfilAppService;
    }

    @GetMapping
    public ResponseEntity<?> obtenerPerfil(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }

        try {

            int usuarioId = Integer.parseInt(authentication.getName());

            PerfilAppResponse perfil = perfilAppService.obtenerPerfil(usuarioId);

            return ResponseEntity.ok(perfil);

        } catch (NumberFormatException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());

        } catch (Exception e) {

            logger.error(
                    "Error al obtener el perfil: usuarioId={}, error={}",
                    authentication.getName(),
                    e.getMessage(),
                    e);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener el perfil");
        }
    }
}