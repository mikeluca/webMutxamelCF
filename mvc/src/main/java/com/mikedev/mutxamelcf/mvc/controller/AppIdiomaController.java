package com.mikedev.mutxamelcf.mvc.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.IdiomaApp;
import com.mikedev.mutxamelcf.model.IdiomaRequest;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import jakarta.validation.Valid;

/** Idioma de la cuenta: en el que se generan sus avisos (bandeja y push) y sus emails. */
@RestController
@RequestMapping("/api/app/preferencias/idioma")
public class AppIdiomaController {

    private final UsuarioAppService usuarioAppService;

    public AppIdiomaController(UsuarioAppService usuarioAppService) {
        this.usuarioAppService = usuarioAppService;
    }

    /**
     * El usuario cambia el idioma en Ajustes. Valores admitidos: es, ca, en; cualquier otro se guarda como es.
     * Responde con el idioma guardado.
     */
    @PutMapping
    public ResponseEntity<?> actualizar(
            @Valid @RequestBody IdiomaRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        int usuarioId;

        try {
            usuarioId = Integer.parseInt(authentication.getName());
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        String idioma = IdiomaApp.normalizar(request.getIdioma());

        usuarioAppService.actualizarIdioma(usuarioId, idioma);

        return ResponseEntity.ok(Map.of("idioma", idioma));
    }
}
