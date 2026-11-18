package com.mikedev.mutxamelcf.mvc.api;

import java.util.Map;

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

import com.mikedev.mutxamelcf.model.SolicitudFamiliaRequest;
import com.mikedev.mutxamelcf.mvc.config.PublicFormRateLimiter;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService;
import com.mikedev.mutxamelcf.service.SolicitudFamiliaService.Revisor;

/**
 * Las solicitudes de acceso de las familias con la sesión de la app (docs/api-app-solicitudes-familia.md): pedir el
 * acceso estando ya dentro, ver el estado de las propias y, para coordinación, administración y los entrenadores de
 * los equipos coincidentes, revisarlas.
 *
 * <p>
 * Una cuenta sin roles (la de una familia que aún no está aprobada) solo puede usar las dos primeras; el resto las
 * rechaza el propio servicio (403). Las coincidencias con jugadores solo salen en las rutas de revisión.
 */
@RestController
@RequestMapping("/api/app/solicitudes-familia")
public class AppSolicitudFamiliaController {

    /** Cuerpo de POST /hijos/{id}/aprobar. */
    public record AprobarRequest(Long jugadorId) {
    }

    /** Cuerpo de POST /hijos/{id}/rechazar. */
    public record RechazarRequest(String motivo) {
    }

    private final SolicitudFamiliaService solicitudFamiliaService;
    private final PublicFormRateLimiter formRateLimiter;

    public AppSolicitudFamiliaController(SolicitudFamiliaService solicitudFamiliaService,
            PublicFormRateLimiter formRateLimiter) {

        this.solicitudFamiliaService = solicitudFamiliaService;
        this.formRateLimiter = formRateLimiter;
    }

    /** POST /api/app/solicitudes-familia: pide el acceso con la sesión iniciada (sin crear cuenta ni verificar email). */
    @PostMapping
    public ResponseEntity<?> solicitar(@RequestBody(required = false) SolicitudFamiliaRequest request,
            Authentication authentication) {

        try {

            int usuarioId = usuarioId(authentication);

            if (!formRateLimiter.permitir("solicitud-familia/sesion/" + usuarioId)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("codigo",
                        "DEMASIADOS_INTENTOS", "mensaje", PublicSolicitudFamiliaController.MENSAJE_DEMASIADOS_INTENTOS));
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(solicitudFamiliaService.solicitarConSesion(usuarioId,
                    request));

        } catch (RuntimeException e) {
            return error(e);
        }
    }

    /** GET /api/app/solicitudes-familia/mias: las solicitudes propias que no han caducado, sin jugadores. */
    @GetMapping("/mias")
    public ResponseEntity<?> mias(Authentication authentication) {

        try {
            return ResponseEntity.ok(solicitudFamiliaService.misSolicitudes(usuarioId(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    /** GET /api/app/solicitudes-familia/pendientes: las que puede revisar, con sus coincidencias. */
    @GetMapping("/pendientes")
    public ResponseEntity<?> pendientes(Authentication authentication) {

        try {
            return ResponseEntity.ok(solicitudFamiliaService.pendientes(revisor(authentication)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    /** GET /api/app/solicitudes-familia/pendientes/contador: {@code {"total": n}}. */
    @GetMapping("/pendientes/contador")
    public ResponseEntity<?> contador(Authentication authentication) {

        try {
            return ResponseEntity.ok(Map.of("total", solicitudFamiliaService.contarPendientes(revisor(authentication))));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    /** GET /api/app/solicitudes-familia/jugadores?q=: busca jugadores entre los que puede ver quien revisa. */
    @GetMapping("/jugadores")
    public ResponseEntity<?> jugadores(@RequestParam(name = "q", required = false) String texto,
            Authentication authentication) {

        try {
            return ResponseEntity.ok(solicitudFamiliaService.buscarJugadores(revisor(authentication), texto));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    /** POST /api/app/solicitudes-familia/hijos/{hijoId}/aprobar con {@code {"jugadorId": n}}. */
    @PostMapping("/hijos/{hijoId}/aprobar")
    public ResponseEntity<?> aprobar(@PathVariable long hijoId, @RequestBody(required = false) AprobarRequest request,
            Authentication authentication) {

        try {

            if (request == null || request.jugadorId() == null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", "Elige el jugador"));
            }

            return ResponseEntity
                    .ok(solicitudFamiliaService.aprobarHijo(revisor(authentication), hijoId, request.jugadorId()));

        } catch (RuntimeException e) {
            return error(e);
        }
    }

    /** POST /api/app/solicitudes-familia/hijos/{hijoId}/rechazar con {@code {"motivo": "..."}} (opcional). */
    @PostMapping("/hijos/{hijoId}/rechazar")
    public ResponseEntity<?> rechazar(@PathVariable long hijoId, @RequestBody(required = false) RechazarRequest request,
            Authentication authentication) {

        try {

            String motivo = request == null ? null : request.motivo();

            return ResponseEntity.ok(solicitudFamiliaService.rechazarHijo(revisor(authentication), hijoId, motivo));

        } catch (RuntimeException e) {
            return error(e);
        }
    }

    private static int usuarioId(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new NoAutenticado();
        }

        try {
            return Integer.parseInt(authentication.getName());
        } catch (NumberFormatException e) {
            throw new NoAutenticado();
        }
    }

    private static Revisor revisor(Authentication authentication) {
        return Revisor.app(usuarioId(authentication));
    }

    private static final class NoAutenticado extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    /** 401 sin sesión, 403 sin permiso, 409 si el estado ya no lo permite y 400 si la petición no vale. */
    private static ResponseEntity<?> error(RuntimeException e) {

        if (e instanceof NoAutenticado) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensaje", "Usuario no autenticado"));
        }

        if (e instanceof SecurityException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("mensaje", e.getMessage()));
        }

        if (e instanceof IllegalStateException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", e.getMessage()));
        }

        if (e instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        }

        throw e;
    }
}
