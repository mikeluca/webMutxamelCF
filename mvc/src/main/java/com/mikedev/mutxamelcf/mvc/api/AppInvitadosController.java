package com.mikedev.mutxamelcf.mvc.api;

import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mikedev.mutxamelcf.model.ConflictoInvitacionException;
import com.mikedev.mutxamelcf.model.MotivoInvitacionRequest;
import com.mikedev.mutxamelcf.service.InvitadosService;

/**
 * Jugadores de otros equipos invitados a la convocatoria de un partido (docs/convocatoria-invitados.md y
 * docs/api-app-convocatoria-invitados.md). Los permisos, los estados y los plazos los comprueba el servicio.
 */
@RestController
@RequestMapping("/api/app")
public class AppInvitadosController {

    private final InvitadosService invitadosService;

    public AppInvitadosController(InvitadosService invitadosService) {
        this.invitadosService = invitadosService;
    }

    /** GET /api/app/partidos/{id}/invitables/equipos: los equipos de los que este partido puede invitar jugadores. */
    @GetMapping("/partidos/{id}/invitables/equipos")
    public ResponseEntity<?> equipos(@PathVariable Long id, Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK, usuario -> invitadosService.equiposInvitables(usuario, id));
    }

    /** GET /api/app/partidos/{id}/invitables/equipos/{equipoId}/jugadores: sus jugadores invitables. */
    @GetMapping("/partidos/{id}/invitables/equipos/{equipoId}/jugadores")
    public ResponseEntity<?> jugadores(
            @PathVariable Long id, @PathVariable Long equipoId, Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK,
                usuario -> invitadosService.jugadoresInvitables(usuario, id, equipoId));
    }

    /** GET /api/app/invitaciones/pendientes: pendientes de coordinación y de su entrenador (COORDINADOR, ADMIN_APP). */
    @GetMapping("/invitaciones/pendientes")
    public ResponseEntity<?> pendientes(Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK, invitadosService::pendientes);
    }

    /** POST /api/app/invitaciones/{id}/aprobar */
    @PostMapping("/invitaciones/{id}/aprobar")
    public ResponseEntity<?> aprobar(@PathVariable Long id, Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK, usuario -> invitadosService.aprobar(usuario, id));
    }

    /** POST /api/app/invitaciones/{id}/rechazar  {motivo?} */
    @PostMapping("/invitaciones/{id}/rechazar")
    public ResponseEntity<?> rechazar(
            @PathVariable Long id,
            @RequestBody(required = false) MotivoInvitacionRequest request,
            Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK,
                usuario -> invitadosService.rechazar(usuario, id, request != null ? request.getMotivo() : null));
    }

    /**
     * POST /api/app/invitaciones/{id}/liberar: el cuerpo técnico del equipo de origen, o coordinación, libera al jugador
     * (lo quita de la convocatoria de su equipo ese día) y queda autorizado.
     */
    @PostMapping("/invitaciones/{id}/liberar")
    public ResponseEntity<?> liberar(@PathVariable Long id, Authentication authentication) {

        return ejecutar(authentication, HttpStatus.OK, usuario -> invitadosService.liberar(usuario, id));
    }

    /** POST /api/app/partidos/{id}/invitados/{jugadorId}/desestimar  {motivo}: la familia o el jugador. */
    @PostMapping("/partidos/{id}/invitados/{jugadorId}/desestimar")
    public ResponseEntity<?> desestimar(
            @PathVariable Long id,
            @PathVariable Long jugadorId,
            @RequestBody MotivoInvitacionRequest request,
            Authentication authentication) {

        return ejecutar(authentication, HttpStatus.CREATED,
                usuario -> invitadosService.desestimar(usuario, id, jugadorId, request.getMotivo()));
    }

    private ResponseEntity<?> ejecutar(Authentication authentication, HttpStatus exito,
            Function<Long, Object> accion) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado");
        }

        try {

            return ResponseEntity.status(exito).body(accion.apply(Long.valueOf(authentication.getName())));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no válido");

        } catch (SecurityException e) {

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());

        } catch (ConflictoInvitacionException e) {

            return ConflictoInvitacionRespuesta.de(e);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());

        }
    }
}
