package com.mikedev.mutxamelcf.mvc.api;

import java.util.LinkedHashMap;
import java.util.Map;

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

import com.mikedev.mutxamelcf.model.ActualizarNombreUsuarioAppRequest;
import com.mikedev.mutxamelcf.model.AnadirVinculosRequest;
import com.mikedev.mutxamelcf.model.InvitacionAppRequest;
import com.mikedev.mutxamelcf.model.VinculoSolicitado;
import com.mikedev.mutxamelcf.mvc.communication.CuentaActivadaNotifier;
import com.mikedev.mutxamelcf.mvc.communication.InvitacionUsuario;
import com.mikedev.mutxamelcf.mvc.communication.ReenvioActivacion;
import com.mikedev.mutxamelcf.service.EmailYaRegistradoException;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

/**
 * Gestionar los usuarios de la app desde la app (solo ADMIN_APP: 403 al resto). Envuelve lo mismo que el panel web
 * (/admin/usuarios-app): UsuarioAppService, InvitacionUsuario y ReenvioActivacion, con los mismos mensajes. Las reglas
 * de seguridad (no desactivarse, no tocar a otro ADMIN_APP, no asignar ADMIN_APP) las comprueba el servicio. Las
 * respuestas llevan {"mensaje": "..."} o {"error": "..."}.
 */
@RestController
@RequestMapping("/api/app/admin/usuarios")
public class AppAdminUsuariosController {

    private static final Logger logger = LoggerFactory.getLogger(AppAdminUsuariosController.class);

    private final UsuarioAppService usuarioAppService;
    private final InvitacionUsuario invitacionUsuario;
    private final ReenvioActivacion reenvioActivacion;
    private final CuentaActivadaNotifier cuentaActivadaNotifier;

    public AppAdminUsuariosController(
            UsuarioAppService usuarioAppService,
            InvitacionUsuario invitacionUsuario,
            ReenvioActivacion reenvioActivacion,
            CuentaActivadaNotifier cuentaActivadaNotifier) {

        this.usuarioAppService = usuarioAppService;
        this.invitacionUsuario = invitacionUsuario;
        this.reenvioActivacion = reenvioActivacion;
        this.cuentaActivadaNotifier = cuentaActivadaNotifier;
    }

    /** Qué hace una ruta con el id del administrador que la llama. */
    @FunctionalInterface
    private interface Accion {
        ResponseEntity<?> ejecutar(int solicitanteId) throws Exception;
    }

    /** GET /api/app/admin/usuarios?q=&rol=&estado= */
    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "rol", required = false) String rol,
            @RequestParam(name = "estado", required = false) String estado,
            Authentication authentication) {

        return ejecutar(authentication, "No se han podido obtener los usuarios",
                id -> ResponseEntity.ok(usuarioAppService.listarUsuariosDesdeApp(id, q, rol, estado)));
    }

    /** GET /api/app/admin/usuarios/opciones */
    @GetMapping("/opciones")
    public ResponseEntity<?> opciones(Authentication authentication) {

        return ejecutar(authentication, "No se han podido obtener las opciones",
                id -> ResponseEntity.ok(usuarioAppService.obtenerOpcionesUsuariosDesdeApp(id)));
    }

    /** POST /api/app/admin/usuarios/invitar {email, nombre, apellidos, vinculos, roles}: 201, o 409 si el email existe. */
    @PostMapping("/invitar")
    public ResponseEntity<?> invitar(@RequestBody(required = false) InvitacionAppRequest request,
            Authentication authentication) {

        return ejecutar(authentication, "Error inesperado al crear la invitación.", id -> {

            InvitacionUsuario.Resultado resultado;

            try {
                resultado = invitacionUsuario.desdeApp(id, request);
            } catch (EmailYaRegistradoException e) {
                return mapa(HttpStatus.CONFLICT, "error", "Ya existe una cuenta con ese email. Búscala en Usuarios.");
            }

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("email", resultado.email());
            respuesta.put("emailEnviado", resultado.enviado());
            respuesta.put("mensaje", resultado.enviado()
                    ? "Invitación enviada correctamente a " + resultado.email() + "."
                    : "El usuario se ha creado, pero no se ha podido enviar el email de invitación. "
                            + "Usa 'Reenviar invitación' cuando quieras volver a intentarlo.");

            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        });
    }

    /** POST /api/app/admin/usuarios/{id}/nombre {nombre, apellidos} */
    @PostMapping("/{id}/nombre")
    public ResponseEntity<?> nombre(@PathVariable int id, @RequestBody ActualizarNombreUsuarioAppRequest request,
            Authentication authentication) {

        return ejecutar(authentication, "Error inesperado al actualizar el nombre.", solicitante -> {
            usuarioAppService.actualizarNombreDesdeApp(solicitante, id, request.getNombre(), request.getApellidos());
            return mapa(HttpStatus.OK, "mensaje", "Nombre actualizado correctamente.");
        });
    }

    /** POST /api/app/admin/usuarios/{id}/vinculos {vinculos: [{tipo, personaId}]} */
    @PostMapping("/{id}/vinculos")
    public ResponseEntity<?> anadirVinculos(@PathVariable int id, @RequestBody AnadirVinculosRequest request,
            Authentication authentication) {

        return ejecutar(authentication, "Error inesperado al añadir el vínculo.", solicitante -> {
            usuarioAppService.agregarVinculosDesdeApp(solicitante, id, request);
            return mapa(HttpStatus.OK, "mensaje", "Vínculo/rol añadido correctamente.");
        });
    }

    /** POST /api/app/admin/usuarios/{id}/vinculos/quitar {tipo, personaId} */
    @PostMapping("/{id}/vinculos/quitar")
    public ResponseEntity<?> quitarVinculo(@PathVariable int id, @RequestBody VinculoSolicitado vinculo,
            Authentication authentication) {

        return ejecutar(authentication, "Error inesperado al quitar el vínculo.", solicitante -> {
            usuarioAppService.quitarVinculoDesdeApp(solicitante, id, vinculo);
            return mapa(HttpStatus.OK, "mensaje", "Vínculo/rol quitado correctamente.");
        });
    }

    /** POST /api/app/admin/usuarios/{id}/reenviar: un reenvío cada 10 minutos por usuario (429 si es pronto). */
    @PostMapping("/{id}/reenviar")
    public ResponseEntity<?> reenviar(@PathVariable int id, Authentication authentication) {

        return ejecutar(authentication, "Error inesperado al reenviar la invitación.", solicitante -> {

            try {

                ReenvioActivacion.Resultado resultado = reenvioActivacion.desdeAppComoPanel(solicitante, id);

                return mapa(HttpStatus.OK, "mensaje", "Invitación reenviada a " + resultado.email() + ".");

            } catch (ReenvioActivacion.DemasiadoPronto e) {

                return mapa(HttpStatus.TOO_MANY_REQUESTS, "error", "Ya se reenvió la activación a este usuario hace poco. "
                        + "Inténtalo de nuevo en " + e.minutosRestantes() + " min.");

            } catch (ReenvioActivacion.EnvioFallido e) {

                return mapa(HttpStatus.INTERNAL_SERVER_ERROR, "error",
                        "No se ha podido enviar el email. Inténtalo de nuevo en unos minutos.");
            }
        });
    }

    /** POST /api/app/admin/usuarios/{id}/activar */
    @PostMapping("/{id}/activar")
    public ResponseEntity<?> activar(@PathVariable int id, Authentication authentication) {

        return ejecutar(authentication, "Error inesperado al activar la cuenta.", solicitante -> {
            usuarioAppService.activarDesdeApp(solicitante, id);
            cuentaActivadaNotifier.notificar(id);
            return mapa(HttpStatus.OK, "mensaje", "Cuenta activada.");
        });
    }

    /** POST /api/app/admin/usuarios/{id}/desactivar */
    @PostMapping("/{id}/desactivar")
    public ResponseEntity<?> desactivar(@PathVariable int id, Authentication authentication) {

        return ejecutar(authentication, "Error inesperado al desactivar la cuenta.", solicitante -> {
            usuarioAppService.desactivarDesdeApp(solicitante, id);
            return mapa(HttpStatus.OK, "mensaje", "Cuenta desactivada.");
        });
    }

    /** POST /api/app/admin/usuarios/{id}/eliminar: solo invitaciones sin activar (como en el panel). */
    @PostMapping("/{id}/eliminar")
    public ResponseEntity<?> eliminar(@PathVariable int id, Authentication authentication) {

        return ejecutar(authentication, "Error inesperado al eliminar la invitación.", solicitante -> {
            usuarioAppService.eliminarInvitacionDesdeApp(solicitante, id);
            return mapa(HttpStatus.OK, "mensaje", "Invitación eliminada.");
        });
    }

    /**
     * 401 sin sesión, 403 si no es ADMIN_APP (SecurityException del servicio), 400 si algo no es válido o una regla
     * de seguridad lo impide, y 500 con el mensaje indicado para lo inesperado.
     */
    private ResponseEntity<?> ejecutar(Authentication authentication, String errorInesperado, Accion accion) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return mapa(HttpStatus.UNAUTHORIZED, "error", "Usuario no autenticado");
        }

        int solicitante;

        try {
            solicitante = Integer.parseInt(authentication.getName());
        } catch (NumberFormatException e) {
            return mapa(HttpStatus.UNAUTHORIZED, "error", "Usuario no válido");
        }

        try {

            return accion.ejecutar(solicitante);

        } catch (SecurityException e) {

            return mapa(HttpStatus.FORBIDDEN, "error", e.getMessage());

        } catch (IllegalArgumentException | IllegalStateException e) {

            logger.warn("Administración de usuarios desde la app: {}", e.getMessage());
            return mapa(HttpStatus.BAD_REQUEST, "error", e.getMessage());

        } catch (org.springframework.dao.DataIntegrityViolationException e) {

            // Con el constraint real en el log (getMostSpecificCause lo trae: ORA-02292 ... (USER.FK_...)).
            logger.error("Restricción de integridad en la administración de usuarios desde la app: {}",
                    e.getMostSpecificCause().getMessage(), e);
            return mapa(HttpStatus.CONFLICT, "error", "No se puede eliminar la invitación porque la cuenta tiene "
                    + "datos asociados. Desactívala en vez de eliminarla.");

        } catch (Exception e) {

            logger.error("Error en la administración de usuarios desde la app: {}", e.getMessage(), e);
            return mapa(HttpStatus.INTERNAL_SERVER_ERROR, "error", errorInesperado);
        }
    }

    private static ResponseEntity<Map<String, String>> mapa(HttpStatus estado, String clave, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of(clave, mensaje));
    }
}
