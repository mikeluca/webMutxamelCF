package com.mikedev.mutxamelcf.mvc.controller;

import com.mikedev.mutxamelcf.model.AlineacionRequest;
import com.mikedev.mutxamelcf.model.GolFavorRequest;
import com.mikedev.mutxamelcf.service.PartidoEnVivoService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.function.Function;

/**
 * Retransmisión en directo de los partidos del Primer Equipo, reservada al rol
 * RETRANSMISION (ver {@link PartidoEnVivoService}). Todos los eventos llevan el
 * partidoId en la ruta y devuelven el estado resultante del partido.
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

        return ejecutar(authentication, usuarioId -> {
            partidoEnVivoService.enviarAlineacion(usuarioId, request.getOnceInicial(), request.getSuplentes());
            return null;
        });
    }

    /** Partidos del Primer Equipo de hoy, no cancelados. */
    @GetMapping("/partidos-disponibles")
    public ResponseEntity<?> partidosDisponibles(Authentication authentication) {

        return ejecutar(authentication, partidoEnVivoService::obtenerPartidosDisponibles);
    }

    @GetMapping("/{partidoId}")
    public ResponseEntity<?> estado(@PathVariable Long partidoId, Authentication authentication) {

        return ejecutar(authentication, usuarioId -> partidoEnVivoService.obtenerEstado(usuarioId, partidoId));
    }

    /** Jugadores entre los que elegir al autor de un gol. */
    @GetMapping("/{partidoId}/convocados")
    public ResponseEntity<?> convocados(@PathVariable Long partidoId, Authentication authentication) {

        return ejecutar(authentication, usuarioId -> partidoEnVivoService.obtenerConvocados(usuarioId, partidoId));
    }

    @PostMapping("/{partidoId}/inicio")
    public ResponseEntity<?> inicio(@PathVariable Long partidoId, Authentication authentication) {

        return ejecutar(authentication, usuarioId -> partidoEnVivoService.enviarInicioPartido(usuarioId, partidoId));
    }

    @PostMapping("/{partidoId}/gol-favor")
    public ResponseEntity<?> golFavor(
            @PathVariable Long partidoId,
            @Valid @RequestBody GolFavorRequest request,
            Authentication authentication) {

        return ejecutar(authentication, usuarioId -> partidoEnVivoService.enviarGolFavor(
                usuarioId, partidoId, request.getJugadorId(), request.getAutorTexto()));
    }

    @PostMapping("/{partidoId}/gol-contra")
    public ResponseEntity<?> golContra(@PathVariable Long partidoId, Authentication authentication) {

        return ejecutar(authentication, usuarioId -> partidoEnVivoService.enviarGolContra(usuarioId, partidoId));
    }

    @PostMapping("/{partidoId}/descanso")
    public ResponseEntity<?> descanso(@PathVariable Long partidoId, Authentication authentication) {

        return ejecutar(authentication, usuarioId -> partidoEnVivoService.enviarDescanso(usuarioId, partidoId));
    }

    @PostMapping("/{partidoId}/segunda-parte")
    public ResponseEntity<?> segundaParte(@PathVariable Long partidoId, Authentication authentication) {

        return ejecutar(authentication, usuarioId -> partidoEnVivoService.enviarSegundaParte(usuarioId, partidoId));
    }

    @PostMapping("/{partidoId}/final")
    public ResponseEntity<?> finalPartido(@PathVariable Long partidoId, Authentication authentication) {

        return ejecutar(authentication, usuarioId -> partidoEnVivoService.enviarFinalPartido(usuarioId, partidoId));
    }

    /** Anula el último evento del partido (solo durante los 2 minutos siguientes). */
    @PostMapping("/{partidoId}/eventos/{eventoId}/deshacer")
    public ResponseEntity<?> deshacer(
            @PathVariable Long partidoId,
            @PathVariable Long eventoId,
            Authentication authentication) {

        return ejecutar(authentication, usuarioId -> partidoEnVivoService.deshacer(usuarioId, partidoId, eventoId));
    }

    private ResponseEntity<?> ejecutar(Authentication authentication, Function<Long, Object> accion) {

        if (authentication == null || !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.valueOf(authentication.getName());

            Object resultado = accion.apply(usuarioId);

            return resultado == null
                    ? ResponseEntity.ok().build()
                    : ResponseEntity.ok(resultado);

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
