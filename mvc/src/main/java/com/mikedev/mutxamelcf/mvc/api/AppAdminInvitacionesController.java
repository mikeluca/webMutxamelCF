package com.mikedev.mutxamelcf.mvc.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.InvitacionAppRequest;
import com.mikedev.mutxamelcf.mvc.communication.InvitacionUsuario;
import com.mikedev.mutxamelcf.service.EmailYaRegistradoException;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * Invitar usuarios desde la app. Solo ADMIN_APP: la comprobación está en UsuarioAppService
 * (SecurityException -> 403), no aquí. La invitación es la misma que la del panel web
 * ({@link InvitacionUsuario}).
 */
@RestController
@RequestMapping("/api/app/admin")
public class AppAdminInvitacionesController {

    private static final Logger logger = LoggerFactory.getLogger(AppAdminInvitacionesController.class);

    private final UsuarioAppService usuarioAppService;
    private final InvitacionUsuario invitacionUsuario;

    public AppAdminInvitacionesController(
            UsuarioAppService usuarioAppService,
            InvitacionUsuario invitacionUsuario) {

        this.usuarioAppService = usuarioAppService;
        this.invitacionUsuario = invitacionUsuario;
    }

    /** GET /api/app/admin/vinculos-disponibles */
    @GetMapping("/vinculos-disponibles")
    public ResponseEntity<?> vinculosDisponibles(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.ok(usuarioAppService.obtenerVinculosDisponibles(
                    Integer.parseInt(authentication.getName())));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (Exception e) {

            logger.error("Error al obtener los vínculos disponibles", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al obtener los vínculos disponibles");
        }
    }

    /**
     * POST /api/app/admin/invitaciones {email, nombre, apellidos, vinculos, roles}
     *
     * 201 con el email al que se invitó; 400 si algo no es válido (también el rol ADMIN_APP);
     * 409 si el email ya está registrado.
     */
    @PostMapping("/invitaciones")
    public ResponseEntity<?> invitar(
            @RequestBody InvitacionAppRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();

        try {

            InvitacionUsuario.Resultado resultado = invitacionUsuario.desdeApp(
                    Integer.parseInt(authentication.getName()), request);

            respuesta.put("email", resultado.email());
            respuesta.put("emailEnviado", resultado.enviado());
            respuesta.put("mensaje", resultado.enviado()
                    ? "Invitación enviada correctamente a " + resultado.email() + "."
                    : "La cuenta se ha creado, pero no se ha podido enviar el email de invitación. "
                            + "Reenvíalo desde Cuentas pendientes.");

            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (EmailYaRegistradoException e) {

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Ya existe una cuenta con ese email. Revisa Cuentas pendientes.");

        } catch (IllegalArgumentException | IllegalStateException e) {

            logger.warn("No se ha podido invitar al usuario desde la app: {}", e.getMessage());

            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {

            logger.error("Error al invitar al usuario desde la app", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error inesperado al crear la invitación.");
        }
    }
}
