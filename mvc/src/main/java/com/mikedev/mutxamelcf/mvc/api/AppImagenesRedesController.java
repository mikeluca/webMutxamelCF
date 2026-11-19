package com.mikedev.mutxamelcf.mvc.api;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

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

import com.mikedev.mutxamelcf.service.CartelFilialService;
import com.mikedev.mutxamelcf.service.CartelNoDisponibleException;
import com.mikedev.mutxamelcf.service.CartelPrimerEquipoService;
import com.mikedev.mutxamelcf.service.ImagenesRedesService;

/**
 * Imágenes para redes sociales (previa, resultados y carteles del próximo partido del Primer Equipo y del Filial): solo RETRANSMISION y
 * ADMIN_APP (lo comprueban los servicios). COORDINADOR ya no tiene acceso: recibe 403.
 */
@RestController
@RequestMapping("/api/app/imagenes-redes")
public class AppImagenesRedesController {

    private final ImagenesRedesService imagenesRedesService;
    private final CartelPrimerEquipoService cartelPrimerEquipoService;
    private final CartelFilialService cartelFilialService;

    public AppImagenesRedesController(ImagenesRedesService imagenesRedesService,
            CartelPrimerEquipoService cartelPrimerEquipoService, CartelFilialService cartelFilialService) {
        this.imagenesRedesService = imagenesRedesService;
        this.cartelPrimerEquipoService = cartelPrimerEquipoService;
        this.cartelFilialService = cartelFilialService;
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

    /**
     * GET /api/app/imagenes-redes/partido-primer-equipo : el próximo partido del Primer Equipo para el cartel
     * (disponible, motivo SIN_PARTIDO | YA_EMPEZADO, datos del partido y textoPublicacion en es y ca).
     */
    @GetMapping("/partido-primer-equipo")
    public ResponseEntity<?> partidoPrimerEquipo(Authentication authentication) {

        return responder(authentication, "Error al obtener el próximo partido",
                usuarioId -> ResponseEntity.ok(cartelPrimerEquipoService.partido(usuarioId)));
    }

    /**
     * GET /api/app/imagenes-redes/partido-primer-equipo/png?formato=PUBLICACION|HISTORIA&amp;idioma=es|ca&amp;racha=true
     * &amp;qr=true&amp;hashtags=true : el cartel, generado al momento. 409 con código si no está disponible; 400 si un
     * parámetro no es válido.
     */
    @GetMapping("/partido-primer-equipo/png")
    public ResponseEntity<?> cartelPrimerEquipo(
            @RequestParam(defaultValue = "PUBLICACION") String formato,
            @RequestParam(defaultValue = "es") String idioma,
            @RequestParam(defaultValue = "true") boolean racha,
            @RequestParam(defaultValue = "true") boolean qr,
            @RequestParam(defaultValue = "true") boolean hashtags,
            Authentication authentication) {

        return responder(authentication, "Error al generar el cartel", usuarioId -> {

            byte[] png = cartelPrimerEquipoService.png(usuarioId, formato, idioma, racha, qr, hashtags);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.noStore())
                    .body(png);
        });
    }


    /**
     * GET /api/app/imagenes-redes/partido-filial : el próximo partido del Filial para el cartel (mismo contrato que
     * {@code partido-primer-equipo}).
     */
    @GetMapping("/partido-filial")
    public ResponseEntity<?> partidoFilial(Authentication authentication) {

        return responder(authentication, "Error al obtener el próximo partido",
                usuarioId -> ResponseEntity.ok(cartelFilialService.partido(usuarioId)));
    }

    /**
     * GET /api/app/imagenes-redes/partido-filial/png?formato=PUBLICACION|HISTORIA&amp;idioma=es|ca&amp;racha=true
     * &amp;qr=true&amp;hashtags=true : el cartel del Filial, generado al momento (mismos parámetros, 409 y 400 que el del
     * Primer Equipo).
     */
    @GetMapping("/partido-filial/png")
    public ResponseEntity<?> cartelFilial(
            @RequestParam(defaultValue = "PUBLICACION") String formato,
            @RequestParam(defaultValue = "es") String idioma,
            @RequestParam(defaultValue = "true") boolean racha,
            @RequestParam(defaultValue = "true") boolean qr,
            @RequestParam(defaultValue = "true") boolean hashtags,
            Authentication authentication) {

        return responder(authentication, "Error al generar el cartel", usuarioId -> {

            byte[] png = cartelFilialService.png(usuarioId, formato, idioma, racha, qr, hashtags);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.noStore())
                    .body(png);
        });
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

        } catch (CartelNoDisponibleException e) {

            return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("codigo", e.motivo(), "mensaje", "El cartel no está disponible: " + e.motivo()));

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorGenerico);
        }
    }
}
