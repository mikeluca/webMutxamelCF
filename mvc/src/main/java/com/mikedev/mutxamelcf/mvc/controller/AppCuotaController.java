package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.CuotaFamiliarResponse;
import com.mikedev.mutxamelcf.service.CuotaFamiliarService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cuotas de los jugadores vinculados a la cuenta que consulta (jugador
 * propio o hijo/a como familiar).
 */
@RestController
@RequestMapping("/api/app/cuotas")
public class AppCuotaController {

    private final CuotaFamiliarService cuotaFamiliarService;

    public AppCuotaController(CuotaFamiliarService cuotaFamiliarService) {
        this.cuotaFamiliarService = cuotaFamiliarService;
    }

    @GetMapping
    public ResponseEntity<?> misCuotas(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }

        try {

            int usuarioId = Integer.parseInt(authentication.getName());

            List<CuotaFamiliarResponse> cuotas = cuotaFamiliarService.obtenerCuotasDeMisJugadores(usuarioId);

            return ResponseEntity.ok(cuotas);

        } catch (NumberFormatException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no válido");

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las cuotas");
        }
    }
}
