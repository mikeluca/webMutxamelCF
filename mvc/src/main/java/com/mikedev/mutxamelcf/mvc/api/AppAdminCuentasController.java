package com.mikedev.mutxamelcf.mvc.api;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.mvc.communication.ReenvioActivacion;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * Cuentas invitadas que aún no se han activado. Solo ADMIN_APP: la comprobación
 * está en UsuarioAppService (SecurityException -> 403).
 */
@RestController
@RequestMapping("/api/app/admin")
public class AppAdminCuentasController {

    private static final Logger logger = LoggerFactory.getLogger(AppAdminCuentasController.class);

    private final UsuarioAppService usuarioAppService;
    private final ReenvioActivacion reenvioActivacion;

    public AppAdminCuentasController(UsuarioAppService usuarioAppService, ReenvioActivacion reenvioActivacion) {
        this.usuarioAppService = usuarioAppService;
        this.reenvioActivacion = reenvioActivacion;
    }

    /** GET /api/app/admin/usuarios-pendientes */
    @GetMapping("/usuarios-pendientes")
    public ResponseEntity<?> listarPendientes(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.ok(
                    usuarioAppService.listarUsuariosPendientes(Integer.parseInt(authentication.getName())));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (Exception e) {

            logger.error("Error al listar las cuentas pendientes de activar", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las cuentas pendientes");
        }
    }

    /**
     * POST /api/app/admin/usuarios/{id}/reenviar-activacion
     *
     * Solo cuentas pendientes y como mucho un reenvío cada 10 minutos por usuario (el
     * límite y el envío del email los comparte con el panel web: ReenvioActivacion).
     */
    @PostMapping("/usuarios/{id}/reenviar-activacion")
    public ResponseEntity<?> reenviarActivacion(@PathVariable int id, Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        Map<String, String> respuesta = new HashMap<>();

        try {

            ReenvioActivacion.Resultado resultado = reenvioActivacion.desdeApp(
                    Integer.parseInt(authentication.getName()), id);

            respuesta.put("mensaje", "Invitación reenviada a " + resultado.email() + ".");
            respuesta.put("email", resultado.email());

            return ResponseEntity.ok(respuesta);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (ReenvioActivacion.DemasiadoPronto e) {

            respuesta.put("error", "Ya se reenvió la activación a este usuario hace poco. "
                    + "Inténtalo de nuevo en " + e.minutosRestantes()
                    + (e.minutosRestantes() == 1 ? " minuto." : " minutos."));

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(respuesta);

        } catch (ReenvioActivacion.EnvioFallido e) {

            respuesta.put("error", "No se ha podido enviar el email. Inténtalo de nuevo en unos minutos.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta);

        } catch (IllegalArgumentException | IllegalStateException e) {

            logger.warn("No se ha podido reenviar la activación: usuarioAppId={}, motivo={}", id, e.getMessage());

            respuesta.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(respuesta);

        } catch (Exception e) {

            logger.error("Error al reenviar la activación: usuarioAppId={}", id, e);

            respuesta.put("error", "Error inesperado al reenviar la invitación.");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta);
        }
    }
}
