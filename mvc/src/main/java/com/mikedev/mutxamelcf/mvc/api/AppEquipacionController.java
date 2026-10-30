package com.mikedev.mutxamelcf.mvc.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.EquipacionService;

/** Equipajes del club para elegir en la convocatoria (docs/api-app-equipaje-convocatoria.md). */
@RestController
@RequestMapping("/api/app/equipaciones")
public class AppEquipacionController {

    private final EquipacionService equipacionService;

    public AppEquipacionController(EquipacionService equipacionService) {
        this.equipacionService = equipacionService;
    }

    /** GET /api/app/equipaciones: los activos por ORDEN y NOMBRE. 403 si el usuario no gestiona ningún equipo. */
    @GetMapping
    public ResponseEntity<?> listar(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.valueOf(authentication.getName());

            return ResponseEntity.ok(equipacionService.obtenerActivasParaApp(usuarioId));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener los equipajes");
        }
    }
}
