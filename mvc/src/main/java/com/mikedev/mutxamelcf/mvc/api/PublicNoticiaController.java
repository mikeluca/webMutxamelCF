package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.NoticiaAppDTO;
import com.mikedev.mutxamelcf.service.NoticiaService;

@RestController
@RequestMapping("/api/public/noticias")
public class PublicNoticiaController {

    private final NoticiaService noticiaService;

    public PublicNoticiaController(NoticiaService noticiaService) {
        this.noticiaService = noticiaService;
    }

    /**
     * Página de noticias, más reciente primero.
     *
     * GET /api/public/noticias
     * Sin parámetros: últimas 5.
     * ?antesId={id}: las noticias inmediatamente anteriores a esa
     * (para cargar historial anterior de 5 en 5).
     * ?limite={n}: tamaño de página (por defecto 5, entre 1 y 20).
     */
    @GetMapping
    public List<NoticiaAppDTO> obtenerNoticias(
            @RequestParam(required = false) Integer antesId,
            @RequestParam(required = false) Integer limite) {

        return noticiaService.obtenerNoticiasParaAppPagina(antesId, limite);
    }

    @GetMapping("/{id}")
    public NoticiaAppDTO obtenerNoticia(@PathVariable int id) {
        return noticiaService.obtenerNoticiaParaApp(id);
    }

    @GetMapping("/{id}/imagen")
    public ResponseEntity<byte[]> obtenerImagen(@PathVariable int id) {

        byte[] imagen = noticiaService.obtenerImagenNoticia(id);

        if (imagen == null || imagen.length == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity
                .ok()
                .header("Content-Type", "image/jpeg")
                .body(imagen);
    }

    @GetMapping("/{id}/imagen-mini")
    public ResponseEntity<byte[]> obtenerImagenMini(
            @PathVariable int id) {

        byte[] imagen = noticiaService.obtenerImagenNoticiaMini(id);

        if (imagen == null || imagen.length == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header("Content-Type", "image/jpeg")
                .body(imagen);
    }
}