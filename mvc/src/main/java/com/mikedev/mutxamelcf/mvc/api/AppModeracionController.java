package com.mikedev.mutxamelcf.mvc.api;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.ReporteModeracionResponse;
import com.mikedev.mutxamelcf.mvc.communication.CuentaActivadaNotifier;
import com.mikedev.mutxamelcf.service.ModeracionService;

/** Cola de moderación de mensajes reportados: solo ADMIN_APP (lo comprueba el servicio). */
@RestController
@RequestMapping("/api/app/moderacion")
public class AppModeracionController {

    private static final Logger logger = LoggerFactory.getLogger(AppModeracionController.class);

    private final ModeracionService moderacionService;
    private final CuentaActivadaNotifier cuentaActivadaNotifier;

    public AppModeracionController(
            ModeracionService moderacionService,
            CuentaActivadaNotifier cuentaActivadaNotifier) {

        this.moderacionService = moderacionService;
        this.cuentaActivadaNotifier = cuentaActivadaNotifier;
    }

    /** GET /api/app/moderacion/reportes?estado=PENDIENTE|RESUELTOS */
    @GetMapping("/reportes")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String estado,
            Authentication authentication) {

        return responder(authentication, usuarioId -> moderacionService.listar(usuarioId, estado));
    }

    /** GET /api/app/moderacion/reportes/pendientes/contador */
    @GetMapping("/reportes/pendientes/contador")
    public ResponseEntity<?> contarPendientes(Authentication authentication) {

        return responder(authentication,
                usuarioId -> Map.of("pendientes", moderacionService.contarPendientes(usuarioId)));
    }

    @PostMapping("/reportes/{id}/descartar")
    public ResponseEntity<?> descartar(
            @PathVariable Long id,
            @RequestBody(required = false) ReporteModeracionResponse.Accion accion,
            Authentication authentication) {

        return responder(authentication, usuarioId -> {
            moderacionService.descartar(usuarioId, id, nota(accion));
            return null;
        });
    }

    @PostMapping("/reportes/{id}/borrar-mensaje")
    public ResponseEntity<?> borrarMensaje(
            @PathVariable Long id,
            @RequestBody(required = false) ReporteModeracionResponse.Accion accion,
            Authentication authentication) {

        return responder(authentication, usuarioId -> {
            moderacionService.borrarMensaje(usuarioId, id, nota(accion));
            return null;
        });
    }

    @PostMapping("/reportes/{id}/desactivar-autor")
    public ResponseEntity<?> desactivarAutor(
            @PathVariable Long id,
            @RequestBody(required = false) ReporteModeracionResponse.Accion accion,
            Authentication authentication) {

        boolean borrarMensaje = accion != null && Boolean.TRUE.equals(accion.getBorrarMensaje());

        return responder(authentication, usuarioId -> {
            moderacionService.desactivarAutor(usuarioId, id, nota(accion), borrarMensaje);
            return null;
        });
    }

    /**
     * POST /api/app/moderacion/reportes/{id}/reactivar-autor {nota?}
     *
     * Solo ADMIN_APP, con el reporte en AUTOR_DESACTIVADO y el autor aún desactivado (409 si no).
     * Igual que activar desde el panel web, avisa por email a la cuenta reactivada.
     */
    @PostMapping("/reportes/{id}/reactivar-autor")
    public ResponseEntity<?> reactivarAutor(
            @PathVariable Long id,
            @RequestBody(required = false) ReporteModeracionResponse.Accion accion,
            Authentication authentication) {

        return responder(authentication, usuarioId -> {
            Long autorId = moderacionService.reactivarAutor(usuarioId, id, nota(accion));
            cuentaActivadaNotifier.notificar(autorId.intValue());
            return null;
        });
    }

    private static String nota(ReporteModeracionResponse.Accion accion) {
        return accion == null ? null : accion.getNota();
    }

    private interface Consulta {
        Object ejecutar(Long usuarioId);
    }

    private ResponseEntity<?> responder(Authentication authentication, Consulta consulta) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            Object resultado = consulta.ejecutar(Long.valueOf(authentication.getName()));

            return resultado == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(resultado);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (NoSuchElementException e) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());

        } catch (IllegalStateException e) {

            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            logger.error("Error en la moderación de mensajes reportados", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error en la moderación");
        }
    }
}
