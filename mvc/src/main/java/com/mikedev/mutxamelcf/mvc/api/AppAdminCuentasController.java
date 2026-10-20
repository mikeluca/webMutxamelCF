package com.mikedev.mutxamelcf.mvc.api;

import java.time.Duration;
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

import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;
import com.mikedev.mutxamelcf.serviceimpl.LimitadorReenvioActivacion;

/** Cuentas invitadas que aún no se han activado: solo ADMIN_APP. */
@RestController
@RequestMapping("/api/app/admin")
public class AppAdminCuentasController {

    private static final Logger logger = LoggerFactory.getLogger(AppAdminCuentasController.class);

    private final UsuarioAppService usuarioAppService;
    private final ComunicacionesService comunicacionesService;
    private final LimitadorReenvioActivacion limitador;

    public AppAdminCuentasController(
            UsuarioAppService usuarioAppService,
            ComunicacionesService comunicacionesService,
            LimitadorReenvioActivacion limitador) {

        this.usuarioAppService = usuarioAppService;
        this.comunicacionesService = comunicacionesService;
        this.limitador = limitador;
    }

    /** GET /api/app/admin/usuarios-pendientes */
    @GetMapping("/usuarios-pendientes")
    public ResponseEntity<?> listarPendientes(Authentication authentication) {

        ResponseEntity<?> denegado = validarAdmin(authentication);

        if (denegado != null) {
            return denegado;
        }

        try {

            return ResponseEntity.ok(usuarioAppService.listarUsuariosPendientes());

        } catch (Exception e) {

            logger.error("Error al listar las cuentas pendientes de activar", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener las cuentas pendientes");
        }
    }

    /**
     * POST /api/app/admin/usuarios/{id}/reenviar-activacion
     *
     * Misma lógica que /admin/usuarios-app/{id}/reenviar del panel web
     * (UsuarioAppService.reenviarInvitacion + enviarInvitacionApp), solo para
     * cuentas pendientes y como mucho una vez cada 10 minutos por usuario.
     */
    @PostMapping("/usuarios/{id}/reenviar-activacion")
    public ResponseEntity<?> reenviarActivacion(@PathVariable int id, Authentication authentication) {

        ResponseEntity<?> denegado = validarAdmin(authentication);

        if (denegado != null) {
            return denegado;
        }

        Map<String, String> respuesta = new HashMap<>();

        try {

            Duration espera = limitador.esperaRestante(id);

            if (!espera.isZero()) {

                long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);

                respuesta.put("error", "Ya se reenvió la activación a este usuario hace poco. "
                        + "Inténtalo de nuevo en " + minutos + (minutos == 1 ? " minuto." : " minutos."));

                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(respuesta);
            }

            InvitacionUsuarioApp invitacion = usuarioAppService.reenviarActivacionPendiente(id);

            boolean enviado = comunicacionesService.enviarInvitacionApp(
                    invitacion.getEmail(),
                    invitacion.getNombrePersona(),
                    invitacion.getTokenActivacion());

            if (!enviado) {

                respuesta.put("error", "No se ha podido enviar el email. Inténtalo de nuevo en unos minutos.");

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta);
            }

            limitador.registrar(id);

            respuesta.put("mensaje", "Invitación reenviada a " + invitacion.getEmail() + ".");
            respuesta.put("email", invitacion.getEmail());

            return ResponseEntity.ok(respuesta);

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

    /** null si el usuario autenticado es ADMIN_APP; si no, la respuesta de error. */
    private ResponseEntity<?> validarAdmin(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            int usuarioId = Integer.parseInt(authentication.getName());

            if (!usuarioAppService.tieneRol(usuarioId, "ADMIN_APP")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Solo un administrador puede consultar las cuentas pendientes");
            }

            return null;

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");
        }
    }
}
