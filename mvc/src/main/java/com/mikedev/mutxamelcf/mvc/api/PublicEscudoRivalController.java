package com.mikedev.mutxamelcf.mvc.api;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.ClubRivalService;

/** Escudos de los clubes rivales (docs/api-app-escudos-rivales.md). Público, sin sesión. */
@RestController
@RequestMapping("/api/public/escudos-rivales")
public class PublicEscudoRivalController {

    private final ClubRivalService clubRivalService;

    public PublicEscudoRivalController(ClubRivalService clubRivalService) {
        this.clubRivalService = clubRivalService;
    }

    /**
     * GET /api/public/escudos-rivales/{id}: el PNG (ya reducido a 256 px) o 404 si el club no existe o no tiene
     * escudo. La URL que da la API lleva la versión ({@code ?v=N}), que cambia con cada escudo nuevo: por eso la
     * respuesta es cacheable para siempre.
     */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> escudo(@PathVariable Long id) {

        byte[] png = clubRivalService.obtenerEscudo(id);

        if (png == null || png.length == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .body(png);
    }
}
