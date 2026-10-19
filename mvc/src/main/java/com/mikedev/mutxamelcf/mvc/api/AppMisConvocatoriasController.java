package com.mikedev.mutxamelcf.mvc.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.MiConvocatoriaResponse;
import com.mikedev.mutxamelcf.model.RespuestaConvocatoriaRequest;
import com.mikedev.mutxamelcf.service.RespuestaConvocatoriaService;

/**
 * Convocatorias vistas por JUGADOR y FAMILIAR. No exigen poder gestionar el
 * equipo (eso es del entrenador): el servicio comprueba el vínculo del usuario
 * con cada jugador, así que la seguridad no depende de la app.
 */
@RestController
@RequestMapping("/api/app")
public class AppMisConvocatoriasController {

    private final RespuestaConvocatoriaService respuestaConvocatoriaService;

    public AppMisConvocatoriasController(RespuestaConvocatoriaService respuestaConvocatoriaService) {
        this.respuestaConvocatoriaService = respuestaConvocatoriaService;
    }

    /**
     * GET /api/app/mis-convocatorias
     *
     * Convocatorias de los jugadores vinculados al usuario, desde hoy hasta
     * dentro de 14 días.
     */
    @GetMapping("/mis-convocatorias")
    public ResponseEntity<?> obtenerMisConvocatorias(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.valueOf(authentication.getName());

            List<MiConvocatoriaResponse> convocatorias = respuestaConvocatoriaService
                    .obtenerMisConvocatorias(usuarioId);

            return ResponseEntity.ok(convocatorias);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las convocatorias");
        }
    }

    /**
     * PUT /api/app/convocatorias/{id}/jugadores/{jugadorId}/respuesta
     *
     * Body: {"respuesta": "VA" | "NO_PUEDE", "motivo": "..."}.
     */
    @PutMapping("/convocatorias/{id}/jugadores/{jugadorId}/respuesta")
    public ResponseEntity<?> responder(
            @PathVariable Long id,
            @PathVariable Long jugadorId,
            @RequestBody RespuestaConvocatoriaRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Long usuarioId = Long.valueOf(authentication.getName());

            MiConvocatoriaResponse respuesta = respuestaConvocatoriaService.responder(
                    usuarioId, id, jugadorId, request);

            return ResponseEntity.ok(respuesta);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al guardar la respuesta");
        }
    }

}
