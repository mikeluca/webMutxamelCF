package com.mikedev.mutxamelcf.mvc.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.Comunicacion;
import com.mikedev.mutxamelcf.model.ReporteComunicacionRequest;
import com.mikedev.mutxamelcf.model.RolApp;
import com.mikedev.mutxamelcf.model.UsuarioApp;
import com.mikedev.mutxamelcf.mvc.communication.ComunicacionesService;
import com.mikedev.mutxamelcf.service.ComunicacionService;
import com.mikedev.mutxamelcf.service.UsuarioAppService;

import jakarta.validation.Valid;

/**
 * Denuncia de un mensaje o comunicación por parte de un usuario de la
 * app (Apple Guideline 1.2). No se guarda nada: se avisa al club por
 * email con los datos relevantes para que lo revise.
 */
@RestController
@RequestMapping("/api/app/comunicaciones")
public class AppReporteComunicacionController {

    private static final Logger logger = LoggerFactory.getLogger(AppReporteComunicacionController.class);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ComunicacionService comunicacionService;
    private final UsuarioAppService usuarioAppService;
    private final ComunicacionesService comunicacionesService;

    public AppReporteComunicacionController(
            ComunicacionService comunicacionService,
            UsuarioAppService usuarioAppService,
            ComunicacionesService comunicacionesService) {

        this.comunicacionService = comunicacionService;
        this.usuarioAppService = usuarioAppService;
        this.comunicacionesService = comunicacionesService;
    }

    /**
     * POST /api/app/comunicaciones/{id}/reportar
     */
    @PostMapping("/{id}/reportar")
    public ResponseEntity<?> reportar(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReporteComunicacionRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");
        }

        try {

            Long reportanteId = Long.parseLong(authentication.getName());

            // Solo se puede reportar lo que el usuario puede ver.
            Comunicacion comunicacion = comunicacionService.obtenerPorId(id, reportanteId);

            if (reportanteId.equals(comunicacion.getUsuarioAutorId())) {
                return ResponseEntity
                        .badRequest()
                        .body("No puedes reportar tu propio mensaje");
            }

            UsuarioApp reportante = usuarioAppService.obtenerPorId(reportanteId.intValue());

            String motivo = request != null && request.getMotivo() != null && !request.getMotivo().isBlank()
                    ? request.getMotivo().trim()
                    : "(sin motivo indicado)";

            String asunto = "REPORTE de contenido en la app (mensaje " + comunicacion.getId() + ")";

            boolean enviado = comunicacionesService.enviarReporteContenido(
                    asunto,
                    construirCuerpo(comunicacion, reportanteId, reportante, motivo),
                    reportante != null ? reportante.getEmail() : null);

            if (!enviado) {
                return ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("No se ha podido enviar el reporte. Inténtalo de nuevo más tarde.");
            }

            logger.info("Reporte de contenido enviado: comunicacionId={}, reportanteId={}",
                    comunicacion.getId(), reportanteId);

            return ResponseEntity.noContent().build();

        } catch (NumberFormatException e) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Usuario no autenticado");

        } catch (SecurityException e) {

            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(e.getMessage());

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());

        } catch (Exception e) {

            logger.error("Error al procesar un reporte de contenido: comunicacionId={}", id, e);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al enviar el reporte");
        }
    }

    private String construirCuerpo(
            Comunicacion comunicacion,
            Long reportanteId,
            UsuarioApp reportante,
            String motivo) {

        Long autorId = comunicacion.getUsuarioAutorId();

        UsuarioApp autor = autorId != null
                ? usuarioAppService.obtenerPorId(autorId.intValue())
                : null;

        LocalDateTime fecha = comunicacion.getFechaPublicacion() != null
                ? comunicacion.getFechaPublicacion()
                : comunicacion.getFechaCreacion();

        StringBuilder cuerpo = new StringBuilder();

        cuerpo.append("Un usuario ha reportado un contenido en la app del Mutxamel CF.\n\n");

        cuerpo.append("== REPORTE ==\n");
        cuerpo.append("Fecha del reporte: ").append(LocalDateTime.now().format(FORMATO_FECHA)).append('\n');
        cuerpo.append("Motivo: ").append(motivo).append("\n\n");

        cuerpo.append("== QUIEN REPORTA ==\n");
        cuerpo.append(describirUsuario(reportanteId, reportante)).append("\n\n");

        cuerpo.append("== AUTOR DEL CONTENIDO ==\n");
        cuerpo.append(describirUsuario(autorId, autor)).append("\n\n");

        cuerpo.append("== CONTENIDO REPORTADO ==\n");
        cuerpo.append("Id: ").append(comunicacion.getId()).append('\n');
        cuerpo.append("Tipo: ")
                .append(comunicacion.getTipo() != null ? comunicacion.getTipo() : "GRUPAL")
                .append('\n');
        if (comunicacion.getTitulo() != null && !comunicacion.getTitulo().isBlank()) {
            cuerpo.append("Título: ").append(comunicacion.getTitulo()).append('\n');
        }
        cuerpo.append("Fecha: ").append(fecha != null ? fecha.format(FORMATO_FECHA) : "(desconocida)").append('\n');
        cuerpo.append("Texto:\n").append(comunicacion.getContenido()).append('\n');

        return cuerpo.toString();
    }

    private String describirUsuario(Long id, UsuarioApp usuario) {

        if (id == null || usuario == null) {
            return "Id: " + (id != null ? id : "(desconocido)") + " (usuario no disponible)";
        }

        if (usuario.isEliminada()) {
            return "Id: " + id + " (cuenta eliminada)";
        }

        List<RolApp> roles = usuarioAppService.obtenerRoles(id.intValue());

        String codigos = roles.stream()
                .map(RolApp::getCodigo)
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("(sin roles)");

        return "Id: " + id + "\nEmail: " + usuario.getEmail() + "\nRoles: " + codigos;
    }
}
