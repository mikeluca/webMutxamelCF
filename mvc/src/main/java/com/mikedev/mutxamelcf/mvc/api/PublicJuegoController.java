package com.mikedev.mutxamelcf.mvc.api;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.JuegoService;

/**
 * El ranking público del juego de Mutxigol (sin sesión): los 50 mejores de cada juego, solo con el alias, y los escudos para Parejas. Ver
 * docs/api-app-juego.md.
 */
@RestController
@RequestMapping("/api/public/juego")
public class PublicJuegoController {

    private final JuegoService juegoService;

    public PublicJuegoController(JuegoService juegoService) {
        this.juegoService = juegoService;
    }

    /**
     * GET /api/public/juego/ranking?juego=ATRAPA|CABEZAZOS|PAREJAS&amp;periodo=SEMANA|HISTORICO (por defecto
     * SEMANA)
     */
    @GetMapping("/ranking")
    public ResponseEntity<?> ranking(
            @RequestParam(name = "juego", required = false) String juego,
            @RequestParam(name = "periodo", required = false) String periodo) {

        return JuegoRespuestas.ejecutar(() -> ResponseEntity.ok(juegoService.ranking(juego, periodo)));
    }

    /**
     * GET /api/public/juego/escudos?cantidad=8 (de 2 a 12): clubes rivales con escudo, al azar, para las fichas de
     * Parejas. Sin datos personales; la respuesta se puede cachear 5 minutos.
     */
    @GetMapping("/escudos")
    public ResponseEntity<?> escudos(@RequestParam(name = "cantidad", required = false) Integer cantidad) {

        return JuegoRespuestas.ejecutar(() -> ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(juegoService.escudos(cantidad)));
    }
}
