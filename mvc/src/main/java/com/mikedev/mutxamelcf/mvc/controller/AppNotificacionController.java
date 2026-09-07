package com.mikedev.mutxamelcf.mvc.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.mikedev.mutxamelcf.model.NotificacionAppResponse;
import com.mikedev.mutxamelcf.model.NotificacionesNoLeidasResponse;
import com.mikedev.mutxamelcf.service.NotificacionAppService;

@RestController
@RequestMapping("/api/app/notificaciones")
public class AppNotificacionController {

    private final NotificacionAppService notificacionService;

    public AppNotificacionController(
            NotificacionAppService notificacionService) {

        this.notificacionService = notificacionService;
    }

    @GetMapping
    public ResponseEntity<?> obtener(
            Authentication authentication) {

        if (!autenticado(authentication)) {
            return unauthorized();
        }

        try {

            Long usuarioId = obtenerUsuarioId(authentication);

            List<NotificacionAppResponse> notificaciones = notificacionService.obtenerPorUsuario(
                    usuarioId);

            return ResponseEntity.ok(notificaciones);

        } catch (NumberFormatException e) {

            return unauthorized();

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las notificaciones");
        }
    }

    @GetMapping("/no-leidas")
    public ResponseEntity<?> obtenerNoLeidas(
            Authentication authentication) {

        if (!autenticado(authentication)) {
            return unauthorized();
        }

        try {

            Long usuarioId = obtenerUsuarioId(authentication);

            List<NotificacionAppResponse> notificaciones = notificacionService.obtenerNoLeidas(
                    usuarioId);

            return ResponseEntity.ok(notificaciones);

        } catch (NumberFormatException e) {

            return unauthorized();

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las notificaciones no leídas");
        }
    }

    @GetMapping("/no-leidas/count")
    public ResponseEntity<?> contarNoLeidas(
            Authentication authentication) {

        if (!autenticado(authentication)) {
            return unauthorized();
        }

        try {

            Long usuarioId = obtenerUsuarioId(authentication);

            NotificacionesNoLeidasResponse response = notificacionService.contarNoLeidas(
                    usuarioId);

            return ResponseEntity.ok(response);

        } catch (NumberFormatException e) {

            return unauthorized();

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al contar las notificaciones");
        }
    }

    @PutMapping("/{id}/leida")
    public ResponseEntity<?> marcarComoLeida(
            Authentication authentication,
            @PathVariable Long id) {

        if (!autenticado(authentication)) {
            return unauthorized();
        }

        try {

            Long usuarioId = obtenerUsuarioId(authentication);

            notificacionService.marcarComoLeida(
                    id,
                    usuarioId);

            return ResponseEntity.ok().build();

        } catch (NumberFormatException e) {

            return unauthorized();

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al marcar la notificación");
        }
    }

    @PutMapping("/leidas")
    public ResponseEntity<?> marcarTodasComoLeidas(
            Authentication authentication) {

        if (!autenticado(authentication)) {
            return unauthorized();
        }

        try {

            Long usuarioId = obtenerUsuarioId(authentication);

            notificacionService.marcarTodasComoLeidas(
                    usuarioId);

            return ResponseEntity.ok().build();

        } catch (NumberFormatException e) {

            return unauthorized();

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al marcar las notificaciones");
        }
    }

    private boolean autenticado(
            Authentication authentication) {

        return authentication != null
                && authentication.isAuthenticated();
    }

    private Long obtenerUsuarioId(
            Authentication authentication) {

        return Long.parseLong(authentication.getName());
    }

    private ResponseEntity<String> unauthorized() {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("Usuario no autenticado");
    }
}