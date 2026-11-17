package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.service.ImagenesRedesService;

/** Imágenes para redes sociales: solo COORDINADOR y ADMIN_APP (lo comprueba el servicio). */
@RestController
@RequestMapping("/api/app/imagenes-redes")
public class AppImagenesRedesController {

    private final ImagenesRedesService imagenesRedesService;

    public AppImagenesRedesController(ImagenesRedesService imagenesRedesService) {
        this.imagenesRedesService = imagenesRedesService;
    }

    /** GET /api/app/imagenes-redes?fecha=yyyy-MM-dd : las imágenes del fin de semana y los textos sugeridos. */
    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String fecha, Authentication authentication) {

        LocalDate dia;

        try {
            dia = parsear(fecha);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("La fecha debe tener el formato yyyy-MM-dd");
        }

        return responder(authentication, "Error al obtener las imágenes",
                usuarioId -> ResponseEntity.ok(imagenesRedesService.listar(usuarioId, dia)));
    }

    /** GET /api/app/imagenes-redes/{id}/png */
    @GetMapping("/{id}/png")
    public ResponseEntity<?> descargar(@PathVariable Long id, Authentication authentication) {

        return responder(authentication, "Error al descargar la imagen", usuarioId -> {

            byte[] png = imagenesRedesService.descargar(usuarioId, id);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.noCache().cachePrivate())
                    .body(png);
        });
    }

    /**
     * POST /api/app/imagenes-redes/regenerar?tipo=PREVIA|RESULTADOS&amp;fecha=yyyy-MM-dd : genera lo que haya en el
     * sistema (partidos y resultados introducidos). Sin push.
     */
    @PostMapping("/regenerar")
    public ResponseEntity<?> regenerar(
            @RequestParam String tipo,
            @RequestParam(required = false) String fecha,
            Authentication authentication) {

        LocalDate dia;

        try {
            dia = parsear(fecha);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("La fecha debe tener el formato yyyy-MM-dd");
        }

        return responder(authentication, "Error al generar las imágenes",
                usuarioId -> ResponseEntity.ok(imagenesRedesService.regenerar(usuarioId, tipo, dia)));
    }

    private static LocalDate parsear(String fecha) {
        return fecha == null || fecha.isBlank() ? null : LocalDate.parse(fecha.trim());
    }

    @FunctionalInterface
    private interface Accion {
        ResponseEntity<?> ejecutar(Long usuarioId);
    }

    private ResponseEntity<?> responder(Authentication authentication, String errorGenerico, Accion accion) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return accion.ejecutar(Long.valueOf(authentication.getName()));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorGenerico);
        }
    }
}
