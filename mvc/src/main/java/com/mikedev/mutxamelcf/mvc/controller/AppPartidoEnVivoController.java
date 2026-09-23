package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.AlineacionRequest;
import com.mikedev.mutxamelcf.model.GolFavorRequest;
import com.mikedev.mutxamelcf.service.PartidoEnVivoService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * Avisos en directo del partido del primer equipo, reservados al rol
 * RETRANSMISION (ver {@link PartidoEnVivoService}).
 */
@RestController
@RequestMapping("/api/app/partido-en-vivo")
public class AppPartidoEnVivoController {

    private final PartidoEnVivoService partidoEnVivoService;

    public AppPartidoEnVivoController(PartidoEnVivoService partidoEnVivoService) {
        this.partidoEnVivoService = partidoEnVivoService;
    }

    @PostMapping("/alineacion")
    public ResponseEntity<?> alineacion(
            @Valid @RequestBody AlineacionRequest request,
            Authentication authentication) {

        return ejecutar(authentication, usuarioId ->
                partidoEnVivoService.enviarAlineacion(
                        usuarioId, request.getOnceInicial(), request.getSuplentes()));
    }

    @PostMapping("/inicio")
    public ResponseEntity<?> inicio(Authentication authentication) {

        return ejecutar(authentication, partidoEnVivoService::enviarInicioPartido);
    }

    @PostMapping("/gol-favor")
    public ResponseEntity<?> golFavor(
            @Valid @RequestBody GolFavorRequest request,
            Authentication authentication) {

        return ejecutar(authentication, usuarioId ->
                partidoEnVivoService.enviarGolFavor(usuarioId, request.getAutor()));
    }

    @PostMapping("/gol-contra")
    public ResponseEntity<?> golContra(Authentication authentication) {

        return ejecutar(authentication, partidoEnVivoService::enviarGolContra);
    }

    @PostMapping("/descanso")
    public ResponseEntity<?> descanso(Authentication authentication) {

        return ejecutar(authentication, partidoEnVivoService::enviarDescanso);
    }

    @PostMapping("/segunda-parte")
    public ResponseEntity<?> segundaParte(Authentication authentication) {

        return ejecutar(authentication, partidoEnVivoService::enviarSegundaParte);
    }

    @PostMapping("/final")
    public ResponseEntity<?> finalPartido(Authentication authentication) {

        return ejecutar(authentication, partidoEnVivoService::enviarFinalPartido);
    }

    private ResponseEntity<?> ejecutar(Authentication authentication, java.util.function.Consumer<Long> accion) {

        if (authentication == null || !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.valueOf(authentication.getName());

            accion.accept(usuarioId);

            return ResponseEntity.ok().build();

        } catch (NumberFormatException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al enviar el aviso del partido en directo");
        }
    }
}
