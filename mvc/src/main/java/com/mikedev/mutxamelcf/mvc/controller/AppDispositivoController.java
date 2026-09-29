package com.mikedev.mutxamelcf.mvc.controller;

import org.springframework.http.ResponseEntity;
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
     */
    @DeleteMapping
    public ResponseEntity<Void> desregistrar(
            @RequestParam String tokenFcm,
            Authentication authentication) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            return ResponseEntity.status(401).build();
        }

        Long usuarioId = Long.valueOf(authentication.getName());

        dispositivoAppService.desactivar(
                usuarioId,
                tokenFcm);

        return ResponseEntity.ok().build();
    }
}