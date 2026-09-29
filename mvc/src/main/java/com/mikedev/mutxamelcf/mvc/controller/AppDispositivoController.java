package com.mikedev.mutxamelcf.mvc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.mikedev.mutxamelcf.model.DispositivoAppRequest;
import com.mikedev.mutxamelcf.service.DispositivoAppService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/app/dispositivos")
public class AppDispositivoController {

    private final DispositivoAppService dispositivoAppService;

    public AppDispositivoController(
            DispositivoAppService dispositivoAppService) {

        this.dispositivoAppService = dispositivoAppService;
    }

    @PostMapping
    public ResponseEntity<Void> registrar(
            @Valid @RequestBody DispositivoAppRequest request,
            Authentication authentication) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            return ResponseEntity.status(401).build();
        }

        Long usuarioId = Long.valueOf(authentication.getName());

        dispositivoAppService.registrar(
                usuarioId,
                request);

        return ResponseEntity.ok().build();
    }

    /**
     * SEC-07: desregistra el token FCM del dispositivo al cerrar sesión,
     * para que un móvil compartido (varios hijos, tablet del club) deje
     * de recibir las notificaciones del usuario que acaba de salir.
     *
     * N-01: esta ruta admite peticiones sin sesión válida (ver
     * SecurityConfig) para que una app con un token ya inválido reciba
     * 204 en vez de 401 al hacer logout, sin entrar en el bucle de
     * cerrarSesion() -> DELETE -> 401 -> cerrarSesion() de la app. Sin
     * usuario autenticado no se desactiva ningún token: no abre ningún
     * hueco de autorización.
     */
    @DeleteMapping
    public ResponseEntity<Void> desregistrar(
            @RequestParam String tokenFcm,
            Authentication authentication) {

        if (authentication == null ||
                authentication instanceof AnonymousAuthenticationToken) {

            return ResponseEntity.noContent().build();
        }

        Long usuarioId = Long.valueOf(authentication.getName());

        dispositivoAppService.desactivar(
                usuarioId,
                tokenFcm);

        return ResponseEntity.ok().build();
    }
}