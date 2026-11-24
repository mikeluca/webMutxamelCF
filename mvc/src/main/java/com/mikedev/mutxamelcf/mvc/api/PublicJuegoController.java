package com.mikedev.mutxamelcf.mvc.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.JuegoService;

/**
 * El ranking público del juego de Mutxigol (sin sesión): los 50 mejores de cada juego, solo con el alias. Ver
 * docs/api-app-juego.md.
 */
@RestController
@RequestMapping("/api/public/juego")
public class PublicJuegoController {

    private final JuegoService juegoService;

    public PublicJuegoController(JuegoService juegoService) {
        this.juegoService = juegoService;
    }

    /** GET /api/public/juego/ranking?juego=PENALTIS|TOQUES|ATRAPA&amp;periodo=SEMANA|HISTORICO (por defecto SEMANA) */
    @GetMapping("/ranking")
    public ResponseEntity<?> ranking(
            @RequestParam(name = "juego", required = false) String juego,
            @RequestParam(name = "periodo", required = false) String periodo) {

        return JuegoRespuestas.ejecutar(() -> ResponseEntity.ok(juegoService.ranking(juego, periodo)));
    }
}
