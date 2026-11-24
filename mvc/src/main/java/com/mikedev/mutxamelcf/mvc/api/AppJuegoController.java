package com.mikedev.mutxamelcf.mvc.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.JuegoService;

/**
 * El perfil y las puntuaciones del juego de Mutxigol de la cuenta con sesión (cualquier rol de la app). Ver
 * docs/api-app-juego.md.
 */
@RestController
@RequestMapping("/api/app/juego")
public class AppJuegoController {

    /** PUT /perfil: alias y/o equipación (al menos uno). */
    public record PerfilRequest(String alias, String equipacion) {
    }

    /** POST /puntuacion. */
    public record PuntuacionRequest(String juego, Integer puntos, Integer duracionMs) {
    }

    private final JuegoService juegoService;

    public AppJuegoController(JuegoService juegoService) {
        this.juegoService = juegoService;
    }

    /** GET /api/app/juego/perfil */
    @GetMapping("/perfil")
    public ResponseEntity<?> perfil(Authentication authentication) {

        int usuarioId = JuegoRespuestas.usuarioId(authentication);

        if (usuarioId <= 0) {
            return JuegoRespuestas.sinSesion();
        }

        return JuegoRespuestas.ejecutar(() -> ResponseEntity.ok(juegoService.perfil(usuarioId)));
    }

    /** PUT /api/app/juego/perfil {alias?, equipacion?} */
    @PutMapping("/perfil")
    public ResponseEntity<?> actualizarPerfil(@RequestBody(required = false) PerfilRequest request,
            Authentication authentication) {

        int usuarioId = JuegoRespuestas.usuarioId(authentication);

        if (usuarioId <= 0) {
            return JuegoRespuestas.sinSesion();
        }

        return JuegoRespuestas.ejecutar(() -> {
            PerfilRequest r = request == null ? new PerfilRequest(null, null) : request;

            return ResponseEntity.ok(juegoService.actualizarPerfil(usuarioId, r.alias(), r.equipacion()));
        });
    }

    /** POST /api/app/juego/puntuacion {juego, puntos, duracionMs}: 201 con récords y posición. */
    @PostMapping("/puntuacion")
    public ResponseEntity<?> guardarPuntuacion(@RequestBody(required = false) PuntuacionRequest request,
            Authentication authentication) {

        int usuarioId = JuegoRespuestas.usuarioId(authentication);

        if (usuarioId <= 0) {
            return JuegoRespuestas.sinSesion();
        }

        return JuegoRespuestas.ejecutar(() -> {
            PuntuacionRequest r = request == null ? new PuntuacionRequest(null, null, null) : request;

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(juegoService.guardarPuntuacion(usuarioId, r.juego(), r.puntos(), r.duracionMs()));
        });
    }
}
