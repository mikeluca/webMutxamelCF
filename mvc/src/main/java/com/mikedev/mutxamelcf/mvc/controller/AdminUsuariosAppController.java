package com.mikedev.mutxamelcf.mvc.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.mikedev.mutxamelcf.model.AnadirVinculosRequest;
import com.mikedev.mutxamelcf.model.InvitacionUsuarioApp;
import com.mikedev.mutxamelcf.model.InvitarUsuarioAppRequest;
import com.mikedev.mutxamelcf.model.PersonasVinculablesResponse;
import com.mikedev.mutxamelcf.model.UsuarioAppAdminResponse;
import com.mikedev.mutxamelcf.model.VinculoSolicitado;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import jakarta.validation.Valid;

/**
 * Gestión de las cuentas de la app móvil (USUARIOS_APP) desde el
 * panel de administración web. Reservado a SUPER (ver SecurityConfig).
 */
@Controller
@RequestMapping("/admin/usuarios-app")
public class AdminUsuariosAppController {

    private static final Logger logger = LoggerFactory.getLogger(AdminUsuariosAppController.class);

    private final UsuarioAppService usuarioAppService;
    private final ComunicacionesService comunicacionesService;

    public AdminUsuariosAppController(
            UsuarioAppService usuarioAppService,
            ComunicacionesService comunicacionesService) {

        this.usuarioAppService = usuarioAppService;
        this.comunicacionesService = comunicacionesService;
    }

    @GetMapping
    public String listar(Model model) {

        logger.debug("Inicio listar usuarios app");

        List<UsuarioAppAdminResponse> usuarios = usuarioAppService.listarUsuariosAdmin();
        PersonasVinculablesResponse personasVinculables = usuarioAppService.obtenerPersonasVinculables();

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("personasVinculables", personasVinculables);

        logger.debug("Fin listar usuarios app: total={}", usuarios.size());

        return "admin/usuarios-app";
    }

    @PostMapping("/invitar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> invitar(
            @Valid @RequestBody InvitarUsuarioAppRequest request) {

        Map<String, String> response = new HashMap<>();

        try {

            InvitacionUsuarioApp invitacion = usuarioAppService.invitarUsuario(request);

            boolean enviado = comunicacionesService.enviarInvitacionApp(
                    invitacion.getEmail(),
                    invitacion.getNombrePersona(),
                    invitacion.getTokenActivacion());

            if (enviado) {
                response.put("mensaje", "Invitación enviada correctamente a " + invitacion.getEmail() + ".");
            } else {
                response.put("mensaje",
                        "El usuario se ha creado, pero no se ha podido enviar el email de invitación. "
                                + "Usa 'Reenviar invitación' cuando quieras volver a intentarlo.");
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException | IllegalStateException e) {

            logger.warn("No se ha podido invitar al usuario: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {

            logger.error("Error al invitar al usuario de la app: {}", e.getMessage(), e);
            response.put("error", "Error inesperado al crear la invitación.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/{id}/vinculos")
    @ResponseBody
    public ResponseEntity<Map<String, String>> anadirVinculos(
            @PathVariable int id,
            @Valid @RequestBody AnadirVinculosRequest request) {

        Map<String, String> response = new HashMap<>();

        try {

            usuarioAppService.agregarVinculosAUsuarioExistente(id, request);
            response.put("mensaje", "Vínculo/rol añadido correctamente.");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {

            logger.warn("No se ha podido añadir el vínculo: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {

            logger.error("Error al añadir el vínculo: {}", e.getMessage(), e);
            response.put("error", "Error inesperado al añadir el vínculo.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/{id}/vinculos/quitar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> quitarVinculo(
            @PathVariable int id,
            @Valid @RequestBody VinculoSolicitado vinculo) {

        Map<String, String> response = new HashMap<>();

        try {

            usuarioAppService.quitarVinculo(id, vinculo);
            response.put("mensaje", "Vínculo/rol quitado correctamente.");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {

            logger.warn("No se ha podido quitar el vínculo: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {

            logger.error("Error al quitar el vínculo: {}", e.getMessage(), e);
            response.put("error", "Error inesperado al quitar el vínculo.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/{id}/reenviar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> reenviar(@PathVariable int id) {

        Map<String, String> response = new HashMap<>();

        try {

            InvitacionUsuarioApp invitacion = usuarioAppService.reenviarInvitacion(id);

            boolean enviado = comunicacionesService.enviarInvitacionApp(
                    invitacion.getEmail(),
                    invitacion.getNombrePersona(),
                    invitacion.getTokenActivacion());

            if (enviado) {
                response.put("mensaje", "Invitación reenviada a " + invitacion.getEmail() + ".");
                return ResponseEntity.ok(response);
            }

            response.put("error", "No se ha podido enviar el email. Inténtalo de nuevo en unos minutos.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (IllegalArgumentException | IllegalStateException e) {

            logger.warn("No se ha podido reenviar la invitación: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {

            logger.error("Error al reenviar la invitación: {}", e.getMessage(), e);
            response.put("error", "Error inesperado al reenviar la invitación.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/{id}/activar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> activar(@PathVariable int id) {

        Map<String, String> response = new HashMap<>();

        try {

            usuarioAppService.activarUsuarioAdmin(id);
            response.put("mensaje", "Cuenta activada.");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {

            logger.error("Error al activar la cuenta: {}", e.getMessage(), e);
            response.put("error", "Error inesperado al activar la cuenta.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/{id}/desactivar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> desactivar(@PathVariable int id) {

        Map<String, String> response = new HashMap<>();

        try {

            usuarioAppService.desactivarUsuarioAdmin(id);
            response.put("mensaje", "Cuenta desactivada.");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {

            logger.error("Error al desactivar la cuenta: {}", e.getMessage(), e);
            response.put("error", "Error inesperado al desactivar la cuenta.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/{id}/eliminar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable int id) {

        Map<String, String> response = new HashMap<>();

        try {

            usuarioAppService.eliminarInvitacion(id);
            response.put("mensaje", "Invitación eliminada.");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException | IllegalStateException e) {

            logger.warn("No se ha podido eliminar la invitación: {}", e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {

            logger.error("Error al eliminar la invitación: {}", e.getMessage(), e);
            response.put("error", "Error inesperado al eliminar la invitación.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
