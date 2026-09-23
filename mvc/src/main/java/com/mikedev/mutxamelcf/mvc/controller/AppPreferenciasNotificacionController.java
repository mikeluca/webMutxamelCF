package com.mikedev.mutxamelcf.mvc.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.mikedev.mutxamelcf.model.PreferenciasNotificacionRequest;
import com.mikedev.mutxamelcf.model.PreferenciasNotificacionResponse;
import com.mikedev.mutxamelcf.service.PreferenciasNotificacionService;

@RestController
@RequestMapping("/api/app/preferencias-notificacion")
public class AppPreferenciasNotificacionController {

    private final PreferenciasNotificacionService preferenciasService;

    public AppPreferenciasNotificacionController(
            PreferenciasNotificacionService preferenciasService) {

        this.preferenciasService = preferenciasService;
    }

    @GetMapping
    public ResponseEntity<?> obtener(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            PreferenciasNotificacionResponse response = preferenciasService.obtenerPorUsuario(
                    usuarioId);

            return ResponseEntity.ok(response);

        } catch (NumberFormatException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las preferencias");
        }
    }

    @PutMapping
    public ResponseEntity<?> actualizar(
            Authentication authentication,
            @RequestBody PreferenciasNotificacionRequest request) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.parseLong(authentication.getName());

            PreferenciasNotificacionResponse response = preferenciasService.actualizar(
                    usuarioId,
                    request);

            return ResponseEntity.ok(response);

        } catch (NumberFormatException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar las preferencias");
        }
    }
}