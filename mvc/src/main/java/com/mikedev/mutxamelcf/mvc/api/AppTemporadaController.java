package com.mikedev.mutxamelcf.mvc.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.TemporadaDTO;
import com.mikedev.mutxamelcf.service.TemporadaService;

/**
 * Exposición de temporadas a la app móvil. La gestión (alta/edición)
 * de temporadas sigue siendo exclusiva de la web de administración
 * (PagosController, bajo /admin/pagos/**, protegido por ROLE_SUPER);
 * este controlador solo permite CONSULTAR la temporada activa, para
 * que el calendario de la app pueda acotar "partidos pasados" a la
 * temporada en curso en vez de a una ventana de fechas fija.
 */
@RestController
@RequestMapping("/api/app/temporadas")
public class AppTemporadaController {

    private final TemporadaService temporadaService;

    public AppTemporadaController(TemporadaService temporadaService) {
        this.temporadaService = temporadaService;
    }

    /**
     * GET /api/app/temporadas/activa
     */
    @GetMapping("/activa")
    public ResponseEntity<?> obtenerTemporadaActiva(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            TemporadaDTO temporada = temporadaService.obtenerTemporadaActiva();

            if (temporada == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(temporada);

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener la temporada activa");
        }
    }

}
